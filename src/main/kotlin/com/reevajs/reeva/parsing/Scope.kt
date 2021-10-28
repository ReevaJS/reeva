package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.transformer.Transformer

open class Scope(val outer: Scope? = null, val allowVarInlining: Boolean = true) {
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

    protected open var nextSlot: Int
        get() = outer!!.nextSlot
        set(value) { outer!!.nextSlot = value }

    val inlineableLocalCount: Int get() = nextInlineableLocal
    val slotCount: Int get() = nextSlot

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
            reference.source = GlobalSourceNode(reference.name())
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

    open fun finish() {
        allocateLocals()
        pendingVariableReferences.clear()
        childScopes.forEach { it.finish() }
    }

    protected open fun allocateLocals() {
        variableSources.forEach {
            it.index = if (it.isInlineable) {
                nextInlineableLocal++
            } else nextSlot++
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
    override var nextSlot = 0

    // Variables that are only "effectively" declared in this scope, such
    // as var declarations in a nested block
    val hoistedVariables = mutableListOf<VariableSourceNode>()

    var receiverVariable: VariableSourceNode? = null

    var argumentsMode = ArgumentsMode.None
    lateinit var argumentsSource: VariableSourceNode

    override fun requiresEnv() = super.requiresEnv() || receiverVariable?.isInlineable == false

    fun addHoistedVariable(source: VariableSourceNode) {
        if (source.scope == this)
            return

        hoistedVariables.add(source)
        source.hoistedScope = this
    }

    fun addReceiverReference(node: VariableRefNode) {
        val declaredScope = node.scope
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

        if (receiverScope != declaredScope)
            receiverScope.receiverVariable!!.isInlineable = false
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

        when (receiverVariable?.isInlineable) {
            true -> receiverVariable!!.index = 0
            false -> receiverVariable!!.index = nextSlot++
            else -> {}
        }

        parameters.forEachIndexed { index, source ->
            source.index = if (source.isInlineable) {
                reservedLocals + index
            } else nextSlot++
        }

        locals.forEach {
            it.index = if (it.isInlineable) {
                nextInlineableLocal++
            } else nextSlot++
        }
    }

    enum class ArgumentsMode {
        None,
        Unmapped,
        Mapped,
    }
}

class GlobalScope : HoistingScope(null, isLexical = false)
