package me.mattco.reeva.parsing

import me.mattco.reeva.ast.*

open class Scope(val outer: Scope? = null) {
    val outerHoistingScope = outerScopeOfType<HoistingScope>()
    val outerGlobalScope = outerScopeOfType<GlobalScope>()

    val childScopes = mutableListOf<Scope>()

    val variableSources = mutableListOf<VariableSourceNode>()
    val pendingVariableReferences = mutableListOf<VariableRefNode>()

    open val isStrict: Boolean
        get() = outerHoistingScope.isStrict

    protected open var nextInlineableRegister: Int
        get() = outer!!.nextInlineableRegister
        set(value) { outer!!.nextInlineableRegister = value }
    protected open var nextSlot: Int
        get() = outer!!.nextSlot
        set(value) { outer!!.nextSlot = value }

    val inlineableRegisterCount: Int get() = nextInlineableRegister
    val slotCount: Int get() = nextSlot

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)
    }

    fun requiresEnv() = variableSources.any { !it.isInlineable }

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
            if (sourceVariable.scope.outerHoistingScope != outerHoistingScope)
                sourceVariable.isInlineable = false
        } else {
            reference.source = GlobalSourceNode(reference.name())
            pendingVariableReferences.add(reference)
        }
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
        allocateSlots()
        pendingVariableReferences.clear()
        childScopes.forEach { it.finish() }
    }

    protected open fun allocateSlots() {
        variableSources.forEach {
            it.slot = if (it.isInlineable) {
                nextInlineableRegister++
            } else nextSlot++
        }
    }

    private inline fun <reified T> outerScopeOfType(): T {
        var scope: Scope? = this
        while (scope !is T)
            scope = scope!!.outer
        return scope
    }

    private fun connectPendingReferences(source: VariableSourceNode) {
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

open class HoistingScope(outer: Scope? = null) : Scope(outer) {
    override var isStrict = false

    override var nextInlineableRegister = 1
    override var nextSlot = 0

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

    override fun allocateSlots() {
        val parameters = variableSources.filter { it.mode == VariableMode.Parameter }
        val locals = variableSources.filter { it.mode != VariableMode.Parameter }

        nextInlineableRegister = parameters.size + 1

        parameters.forEachIndexed { index, source ->
            source.slot = if (source.isInlineable) {
                index + 1
            } else nextSlot++
        }

        locals.forEach {
            it.slot = if (it.isInlineable) {
                nextInlineableRegister++
            } else nextSlot++
        }
    }

    enum class ArgumentsMode {
        None,
        Unmapped,
        Mapped,
    }
}

class GlobalScope : HoistingScope(null) {

}
