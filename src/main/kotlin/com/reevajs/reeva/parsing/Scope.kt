package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.transformer.Transformer
import com.reevajs.reeva.utils.unreachable

open class Scope(val outer: Scope? = null) {
    val outerHoistingScope = outerScopeOfType<HoistingScope>()
    val outerGlobalScope = outerScopeOfType<GlobalScope>()

    val childScopes = mutableListOf<Scope>()

    val variableSources = mutableListOf<VariableSourceNode>()
    val pendingVariableReferences = mutableListOf<VariableRefNode>()

    var isIntrinsicallyStrict = false

    val isStrict: Boolean
        get() = isIntrinsicallyStrict || (outer?.isStrict ?: false)

    open val inlineableLocalCount = 0

    var isTaintedByEval = false
        private set

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)
    }

    fun addVariableSource(source: VariableSourceNode) {
        source.scope = this
        variableSources.add(source)
        connectPendingReferences(source)
    }

    fun addVariableReference(reference: VariableRefNode) {
        reference.scope = this

        val sourceVariable = findSourceVariable(reference.name())
        if (sourceVariable != null) {
            reference.source = sourceVariable
        } else {
            reference.source = GlobalSourceNode(reference.name()).apply {
                scope = this@Scope
            }
            pendingVariableReferences.add(reference)
        }
    }

    fun getReceiverOrNewTargetScope(): HoistingScope {
        var receiverScope = outerHoistingScope
        while (receiverScope.isLexical)
            receiverScope = receiverScope.outer!!.outerHoistingScope
        return receiverScope
    }

    fun envDistanceFrom(ancestorScope: Scope): Int {
        if (ancestorScope == this)
            return 0

        var scope = this
        var i = 0
        while (scope != ancestorScope) {
            i++
            scope = scope.outer!!
        }
        return i
    }

    fun markEvalScope() {
        isTaintedByEval = true
        outer?.markEvalScope()
    }

    open fun finish() {
        pendingVariableReferences.clear()
        childScopes.forEach { it.finish() }
    }

    private inline fun <reified T> outerScopeOfType(): T {
        var scope: Scope? = this
        while (scope !is T)
            scope = scope!!.outer
        return scope
    }

    protected fun connectPendingReferences(source: VariableSourceNode) {
        for (reference in pendingVariableReferences) {
            if (reference.name() == source.name())
                reference.source = source
        }

        childScopes.forEach { it.connectPendingReferences(source) }
    }

    private fun findSourceVariable(name: String): VariableSourceNode? {
        for (source in variableSources) {
            if (source.name() == name)
                return source
        }

        return outer?.findSourceVariable(name)
    }
}

open class HoistingScope(outer: Scope? = null, val isLexical: Boolean = false) : Scope(outer) {
    var isDerivedClassConstructor = false
    override var inlineableLocalCount = Transformer.RESERVED_LOCALS_COUNT

    // Variables that are only "effectively" declared in this scope, such
    // as var declarations in a nested block
    val hoistedVariables = mutableListOf<VariableSourceNode>()

    var receiverVariable: VariableSourceNode? = null

    var argumentsMode = ArgumentsMode.None
    lateinit var argumentsSource: VariableSourceNode

    fun addHoistedVariable(source: VariableSourceNode) {
        if (source.scope == this)
            return

        hoistedVariables.add(source)
        source.hoistedScope = this
    }

    fun addReceiverReference(node: VariableRefNode) {
        val receiverScope = getReceiverOrNewTargetScope()

        if (receiverScope.receiverVariable == null) {
            val receiverVariable = FakeSourceNode("*this")
            receiverVariable.mode = VariableMode.Parameter
            receiverVariable.type = VariableType.Const
            receiverVariable.scope = receiverScope
            receiverScope.receiverVariable = receiverVariable
            receiverScope.connectPendingReferences(receiverVariable)
        }

        val sourceVariable = receiverScope.receiverVariable!!
        node.source = sourceVariable
    }

    override fun finish() {
        if (argumentsMode != ArgumentsMode.None) {
            if (needsArgumentsObject()) {
                argumentsSource = object : VariableSourceNode(SourceLocation.EMPTY) {
                    override val children get() = emptyList<AstNode>()
                    override fun name() = "arguments"
                    override fun accept(visitor: AstVisitor) = unreachable()
                }
                argumentsSource.mode = VariableMode.Declared
                argumentsSource.type = VariableType.Var

                addVariableSource(argumentsSource)
            } else {
                argumentsMode = ArgumentsMode.None
            }
        }

        inlineableLocalCount += variableSources.filter { it.mode == VariableMode.Parameter }.size

        super.finish()
    }

    private fun needsArgumentsObject(scope: Scope = this): Boolean {
        if (scope.variableSources.any { it.name() == "arguments" })
            return false

        if (scope.pendingVariableReferences.any { it.name() == "arguments" })
            return true

        return scope.childScopes.filter { it !is HoistingScope }.any(::needsArgumentsObject)
    }

    enum class ArgumentsMode {
        None,
        Unmapped,
        Mapped,
    }
}

class GlobalScope : HoistingScope(null, isLexical = false)

class ModuleScope(outer: Scope) : HoistingScope(outer, isLexical = false) {
    init {
        isIntrinsicallyStrict = true
    }
}
