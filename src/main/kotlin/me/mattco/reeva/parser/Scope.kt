package me.mattco.reeva.parser

import me.mattco.reeva.ast.VariableRefNode
import me.mattco.reeva.ast.VariableSourceNode
import me.mattco.reeva.utils.expect

open class Scope(val outer: Scope? = null) {
    private val childScopes = mutableListOf<Scope>()
    var globalScope: Scope? = null

    private val _declaredVariables = mutableListOf<Variable>()
    val declaredVariables: List<Variable>
        get() = _declaredVariables

    val inlineableVariables: List<Variable>
        get() = declaredVariables.filter { it.isInlineable }

    val envVariables: List<Variable>
        get() = declaredVariables.filterNot { it.isInlineable }

    // Variables that have yet to be connected to their source
    private val unlinkedRefNodes = mutableListOf<VariableRefNode>()

    // How many slots the EnvRecord associated with this Scope requires
    var numSlots: Int = 0
        private set

    val requiresEnv: Boolean get() = numSlots > 0

    val isStrict: Boolean by lazy {
        firstParentOfType<HoistingScope>().hasUseStrictDirective
    }

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)

        globalScope = outer?.globalScope
    }

    fun hoistingScope(): HoistingScope {
        return if (this is HoistingScope) this else outer!!.hoistingScope()
    }

    fun addDeclaredVariable(variable: Variable) {
        expect(variable.mode != Variable.Mode.Global)

        if (variable.type != Variable.Type.Var || this is HoistingScope) {
           _declaredVariables.add(variable)
        } else {
            outer!!.addDeclaredVariable(variable)
        }
    }

    fun addReference(node: VariableRefNode) {
        val name = node.boundName()

        unlinkedRefNodes.add(node)

        node.targetVar = Variable(
            name,
            Variable.Type.Var,
            Variable.Mode.Global,
            GlobalSourceNode().also { it.scope = globalScope!! },
        )
    }

    private fun findDeclaredVariable(name: String): Variable? {
        return _declaredVariables.firstOrNull {
            it.name == name
        } ?: outer?.findDeclaredVariable(name)
    }

    inline fun <reified T : Scope> firstParentOfType(): T {
        var scope = this
        while (scope !is T)
            scope = scope.outer!!
        return scope
    }

    fun distanceFrom(ancestorScope: Scope): Int {
        var scope = this
        var i = 0
        while (scope != ancestorScope) {
            i++
            scope = scope.outer!!
        }
        return i
    }

    fun envDistanceFrom(ancestorScope: Scope): Int {
        if (ancestorScope == this)
            return 0

        var scope = this
        var i = 0
        while (scope != ancestorScope) {
            if (scope.requiresEnv)
                i++
            scope = scope.outer!!
        }
        return i + if (ancestorScope.requiresEnv) 1 else 0
    }

    fun crossesFunctionBoundary(other: Scope): Boolean {
        var scope = this
        while (scope != other) {
            if (scope is HoistingScope)
                return true
            scope = scope.outer!!
        }
        return false
    }

    open fun onFinish() {
        // Process unlinked nodes before we do any slot assignment

        processUnlinkedNodes()
        childScopes.forEach(Scope::processUnlinkedNodes)

        // Assign each non-inlineable variable their own slot index.
        // Inlineable variables are handled during the IR phase
        declaredVariables.filter {
            !it.isInlineable
        }.forEachIndexed { index, value ->
            value.slot = index
            numSlots++
        }

        childScopes.forEach(Scope::onFinish)
    }

    private fun processUnlinkedNodes() {
        // Attempt to connect any remaining global var references
        for (node in unlinkedRefNodes) {
            val variable = findDeclaredVariable(node.targetVar.name)
            if (variable != null) {
                node.targetVar = variable
                if (node.scope.crossesFunctionBoundary(variable.source.scope)) {
                    variable.isInlineable = false
                }
            }
        }
        unlinkedRefNodes.clear()
    }
}

open class HoistingScope(outer: Scope? = null) : Scope(outer) {
    var hasUseStrictDirective: Boolean = false
}

class ClassScope(outer: Scope? = null) : Scope(outer)

class ModuleScope(outer: Scope? = null) : HoistingScope(outer)

data class Variable(
    val name: String,
    val type: Type,
    val mode: Mode,
    var source: VariableSourceNode,
) {
    var isInlineable = source !is GlobalSourceNode

    /**
     * If isInlineable is true, then this is the register that
     * the variable value is currently stored in. If not, then
     * this is the slot index in its context.
     */
    var slot = -1

    enum class Mode {
        Declared,
        Parameter,
        CatchParameter,
        Global,
    }

    enum class Type {
        Var,
        Const,
        Let,
    }
}

class GlobalSourceNode : VariableSourceNode()
