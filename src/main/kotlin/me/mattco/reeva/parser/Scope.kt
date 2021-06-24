package me.mattco.reeva.parser

import me.mattco.reeva.ast.VariableRefNode
import me.mattco.reeva.ast.VariableSourceNode

open class Scope(val outer: Scope? = null) {
    val childScopes = mutableListOf<Scope>()
    val parentHoistingScope: HoistingScope by lazy { firstParentOfType() }
    val globalScope: GlobalScope by lazy { firstParentOfType() }

    val declaredVariables = mutableListOf<Variable>()

    // Variables that have yet to be connected to their source
    val pendingReferences = mutableListOf<VariableRefNode>()

    open val declaredVarMode = Variable.Mode.Declared

    var possiblyReferencesArguments = false

    val isStrict: Boolean
        get() = parentHoistingScope.hasUseStrictDirective

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)
    }

    fun addDeclaredVariable(variable: Variable) {
        if (variable.type != Variable.Type.Var || this is HoistingScope) {
            declaredVariables.add(variable)
        } else {
            outer!!.addDeclaredVariable(variable)
        }
    }

    fun addReference(node: VariableRefNode) {
        val name = node.boundName()

        pendingReferences.add(node)

        node.targetVar = Variable(
            name,
            Variable.Type.Var,
            Variable.Mode.Global,
            GlobalSourceNode().also { it.scope = globalScope },
        )
    }

    private fun findDeclaredVariable(name: String): Variable? {
        return declaredVariables.firstOrNull {
            it.name == name
        } ?: outer?.findDeclaredVariable(name)
    }

    private inline fun <reified T : Scope> firstParentOfType(): T {
        var scope = this
        while (scope !is T)
            scope = scope.outer!!
        return scope
    }

    fun envDistanceFrom(ancestorScope: Scope): Int {
        if (ancestorScope == this)
            return 0

        var scope = this
        var i = 1
        while (scope != ancestorScope) {
            i++
            scope = scope.outer!!
        }
        return i
    }

    fun onFinish() {
        processUnlinkedNodes()
        searchForUseBeforeDecl()
        onFinishImpl()
        pendingReferences.clear()
    }

    private fun processUnlinkedNodes() {
        // Attempt to connect any remaining global var references
        for (node in pendingReferences) {
            val variable = findDeclaredVariable(node.targetVar.name)
            if (variable != null)
                node.targetVar = variable
        }

        childScopes.forEach(Scope::processUnlinkedNodes)
    }

    private fun searchForUseBeforeDecl() {
        // TODO: Improving this is not trivial, but it would be nice

        for (node in pendingReferences) {
            val refStart = node.sourceStart.index
            val varStart = node.targetVar.source.sourceStart.index

            if (refStart < varStart)
                node.targetVar.possiblyUsedBeforeDecl = true
        }

        childScopes.forEach(Scope::searchForUseBeforeDecl)
    }

    protected open fun onFinishImpl() {
    }
}

open class HoistingScope(outer: Scope? = null) : Scope(outer) {
    var hasUseStrictDirective: Boolean = false

    override fun onFinishImpl() {
        possiblyReferencesArguments = searchForArgumentsReference(this)
        super.onFinishImpl()
    }

    private fun searchForArgumentsReference(scope: Scope): Boolean {
        for (node in scope.pendingReferences) {
            if (node.boundName() == "arguments" && node.targetVar.mode == Variable.Mode.Global)
                return true
        }

        return scope.childScopes.filter { it !is HoistingScope }.any { searchForArgumentsReference(it) }
    }
}

open class GlobalScope : HoistingScope() {
    override val declaredVarMode = Variable.Mode.Global
}

data class Variable(
    val name: String,
    val type: Type,
    val mode: Mode,
    var source: VariableSourceNode,
) {
    var possiblyUsedBeforeDecl = false

    val scope: Scope
        get() = source.scope

    enum class Mode {
        Declared,
        Parameter,
        Global,
    }

    enum class Type {
        Var,
        Let,
        Const,
    }
}

class GlobalSourceNode : VariableSourceNode()
