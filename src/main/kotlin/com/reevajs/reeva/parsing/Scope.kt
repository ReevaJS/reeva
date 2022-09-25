package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.transformer.Transformer

open class Scope(
    val outer: Scope? = null,
    var allowVarInlining: Boolean = true,
) {
    val outerHoistingScope = outerScopeOfType<HoistingScope>()
    val outerGlobalScope = outerScopeOfType<GlobalScope>()

    val childScopes = mutableListOf<Scope>()

    val variableSources = mutableListOf<VariableSourceNode>()
    val pendingVariableReferences = mutableListOf<VariableRefNode>()

    open val isStrict: Boolean
        get() = outerHoistingScope.isStrict

    protected open var nextInlineableLocal: Int
        get() = outer!!.nextInlineableLocal
        set(value) { outer!!.nextInlineableLocal = value }

    val inlineableLocalCount: Int get() = nextInlineableLocal

    var isTaintedByEval = false
        private set

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)
    }

    open fun requiresEnv() = variableSources.any { !it.isInlineable }

    fun addVariableSource(source: VariableSourceNode) {
        source.scope = this
        variableSources.add(source)

        connectPendingReferences(source)

        if (!allowVarInlining)
            source.isInlineable = false
    }

    fun addVariableReference(reference: VariableRefNode) {
        reference.scope = this

        val sourceVariable = findSourceVariable(reference.name())
        if (sourceVariable != null) {
            reference.source = sourceVariable
            if (sourceVariable.scope.outerHoistingScope != outerHoistingScope)
                sourceVariable.isInlineable = false
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
            if (scope.requiresEnv())
                i++
            scope = scope.outer!!
        }
        return i
    }

    fun markEvalScope() {
        allowVarInlining = false
        isTaintedByEval = true

        for (source in variableSources)
            source.isInlineable = false

        outer?.markEvalScope()
    }

    open fun finish() {
        allocateLocals()
        pendingVariableReferences.clear()
        childScopes.forEach { it.finish() }
    }

    protected open fun allocateLocals() {
        variableSources.forEach {
            it.key = when {
                isTaintedByEval -> VariableKey.Named
                it.isInlineable -> VariableKey.InlineIndex(nextInlineableLocal++)
                else -> VariableKey.Named
            }
        }
    }

    private inline fun <reified T> outerScopeOfType(): T {
        var scope: Scope? = this
        while (scope !is T)
            scope = scope!!.outer
        return scope
    }

    protected fun connectPendingReferences(source: VariableSourceNode) {
        for (reference in pendingVariableReferences) {
            if (reference.name() == source.name()) {
                reference.source = source
                if (source.scope.outerHoistingScope != outerHoistingScope)
                    source.isInlineable = false
            }
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

open class HoistingScope(
    outer: Scope? = null,
    val isLexical: Boolean = false,
    allowVarInlining: Boolean = true,
    isGenerator: Boolean = false,
) : Scope(outer, allowVarInlining) {
    override var isStrict = false
    var isDerivedClassConstructor = false
    private val reservedLocals = Transformer.getReservedLocalsCount(isGenerator)

    override var nextInlineableLocal = reservedLocals

    // Variables that are only "effectively" declared in this scope, such
    // as var declarations in a nested block
    val hoistedVariables = mutableListOf<VariableSourceNode>()

    var argumentsMode = ArgumentsMode.None
    lateinit var argumentsSource: VariableSourceNode

    fun addHoistedVariable(source: VariableSourceNode) {
        if (source.scope == this)
            return

        hoistedVariables.add(source)
        source.hoistedScope = this
    }

    override fun finish() {
        if (argumentsMode != ArgumentsMode.None) {
            if (needsArgumentsObject()) {
                argumentsSource = object : VariableSourceNode() {
                    override fun name() = "arguments"
                }
                argumentsSource.mode = VariableMode.Declared
                argumentsSource.type = VariableType.Var

                addVariableSource(argumentsSource)
            } else {
                argumentsMode = ArgumentsMode.None
            }
        }

        super.finish()
    }

    private fun needsArgumentsObject(scope: Scope = this): Boolean {
        if (scope.variableSources.any { it.name() == "arguments" })
            return false

        if (scope.pendingVariableReferences.any { it.name() == "arguments" })
            return true

        return scope.childScopes.filter { it !is HoistingScope }.any(::needsArgumentsObject)
    }

    override fun allocateLocals() {
        val parameters = variableSources.filter { it.mode == VariableMode.Parameter }
        val locals = variableSources.filter { it.mode != VariableMode.Parameter }

        nextInlineableLocal = reservedLocals + parameters.size

        parameters.forEachIndexed { index, source ->
            source.key = when {
                isTaintedByEval -> VariableKey.Named
                source.isInlineable -> VariableKey.InlineIndex(reservedLocals + index)
                else -> VariableKey.Named
            }
        }

        locals.forEach {
            it.key = when {
                isTaintedByEval -> VariableKey.Named
                it.isInlineable -> VariableKey.InlineIndex(nextInlineableLocal++)
                else -> VariableKey.Named
            }
        }
    }

    enum class ArgumentsMode {
        None,
        Unmapped,
        Mapped,
    }
}

class GlobalScope : HoistingScope(null, isLexical = false) {
    override fun requiresEnv() = false
}

class ModuleScope(outer: Scope) : HoistingScope(outer, isLexical = false) {
    override var isStrict = true
}
