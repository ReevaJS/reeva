package me.mattco.reeva.parser

import me.mattco.reeva.ast.VariableRefNode

open class Scope(val outer: Scope? = null) {
    private val childScopes = mutableListOf<Scope>()

    private val _declaredVariables = mutableListOf<Variable>()
    val declaredVariables: List<Variable>
        get() = _declaredVariables

    // Variables that have yet to be connected to their source
    private val unlinkedRefNodes = mutableListOf<VariableRefNode>()

    val isStrict: Boolean by lazy {
        firstParentOfType<HoistingScope>().hasUseStrictDirective
    }

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)
    }

    fun addDeclaredVariable(variable: Variable) {
        _declaredVariables.add(variable)
        addDeclaredVariableHelper(variable)
    }

    fun addReference(node: VariableRefNode) {
        val name = node.boundName()

        node.variable = findDeclaredVariable(name) ?: let {
            unlinkedRefNodes.add(node)
            Variable(name, Variable.Type.Var, Variable.Mode.Global)
        }
    }

    private fun findDeclaredVariable(name: String): Variable? {
        return _declaredVariables.firstOrNull {
            it.name == name
        } ?: outer?.findDeclaredVariable(name)
    }

    private fun addDeclaredVariableHelper(variable: Variable) {
        unlinkedRefNodes.removeIf {
            if (it.variable.name == variable.name) {
                it.variable = variable
                true
            } else false
        }

        // Non-var declarations aren't hoisted out of any scope
        if (variable.type != Variable.Type.Var)
            return

        // If this scope is a HoistingScope, any var-declarations
        // will reside in this scope, not in an upper scope
        if (this is HoistingScope)
            return

        outer?.addDeclaredVariableHelper(variable)
    }

    inline fun <reified T : Scope> firstParentOfType(): T {
        var scope = this
        while (scope !is T)
            scope = scope.outer!!
        return scope
    }

    open fun onFinish() {
        // Attempt to connect any remaining global var references
        for (node in unlinkedRefNodes) {
            val source = findDeclaredVariable(node.variable.name)
            if (source != null)
                node.variable = source
        }
        unlinkedRefNodes.clear()

        childScopes.forEach(Scope::onFinish)
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
) {
    enum class Mode {
        Declared,
        Parameter,
        Global,
    }

    enum class Type {
        Var,
        Const,
        Let,
    }
}
