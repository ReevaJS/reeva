package me.mattco.reeva.parser

import me.mattco.reeva.ast.VariableRefNode
import me.mattco.reeva.ast.VariableSourceNode
import me.mattco.reeva.ir.opcodes.CreateBlockScope
import me.mattco.reeva.ir.opcodes.Opcode

open class Scope(val outer: Scope? = null) {
    val depth: Int = outer?.depth?.plus(1) ?: 0

    val childScopes = mutableListOf<Scope>()
    var globalScope: Scope? = null

    private val _declaredVariables = mutableListOf<Variable>()
    val declaredVariables: List<Variable>
        get() = _declaredVariables

    // Variables that have yet to be connected to their source
    val refNodes = mutableListOf<VariableRefNode>()

    // How many slots the EnvRecord associated with this Scope requires
    var numSlots: Int = 0
        protected set

    val requiresEnv: Boolean get() = numSlots > 0

    open val declaredVarMode = Variable.Mode.Declared

    val isStrict: Boolean by lazy {
        firstParentOfType<HoistingScope>().hasUseStrictDirective
    }

    var possiblyReferencesArguments = false

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)

        globalScope = outer?.globalScope
    }

    open fun createEnterScopeOpcode(numSlots: Int): Opcode {
        return CreateBlockScope(numSlots)
    }

    fun hoistingScope(): HoistingScope {
        return if (this is HoistingScope) this else outer!!.hoistingScope()
    }

    fun addDeclaredVariable(variable: Variable) {
        if (variable.type != Variable.Type.Var || this is HoistingScope) {
           _declaredVariables.add(variable)
        } else {
            outer!!.addDeclaredVariable(variable)
        }
    }

    fun addReference(node: VariableRefNode) {
        val name = node.boundName()

        refNodes.add(node)

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

    fun onFinish() {
        processUnlinkedNodes()
        searchForUseBeforeDecl()
        refNodes.clear()
        onFinishImpl()
    }

    private fun processUnlinkedNodes() {
        // Attempt to connect any remaining global var references
        for (node in refNodes) {
            val variable = findDeclaredVariable(node.targetVar.name)
            if (variable != null)
                node.targetVar = variable
        }

        childScopes.forEach(Scope::processUnlinkedNodes)
    }

    private fun searchForUseBeforeDecl() {
        // TODO: Improving this is not trivial, but it would be nice

        for (node in refNodes) {
            val refStart = node.sourceStart.index
            val varStart = node.targetVar.source.sourceStart.index

            if (refStart < varStart)
                node.targetVar.possiblyUsedBeforeDecl = true
        }

        childScopes.forEach(Scope::searchForUseBeforeDecl)
    }

    protected open fun onFinishImpl() {
        // Assign each variable their own slot index
        declaredVariables.forEachIndexed { index, value ->
            value.slot = index
            numSlots++
        }

        childScopes.forEach(Scope::onFinishImpl)
    }
}

open class HoistingScope(outer: Scope? = null) : Scope(outer) {
    var hasUseStrictDirective: Boolean = false

    override fun createEnterScopeOpcode(numSlots: Int): Opcode {
        TODO()
    }

    override fun onFinishImpl() {
        possiblyReferencesArguments = searchForArgumentsReference(this)
        super.onFinishImpl()
    }

    private fun searchForArgumentsReference(scope: Scope): Boolean {
        for (node in scope.refNodes) {
            if (node.boundName() == "arguments" && node.targetVar.mode == Variable.Mode.Global)
                return true
        }

        return scope.childScopes.filter { it !is HoistingScope }.any { searchForArgumentsReference(it) }
    }
}

class ClassScope(outer: Scope? = null) : Scope(outer) {
}

open class GlobalScope : HoistingScope() {
    override val declaredVarMode = Variable.Mode.Global

    override fun createEnterScopeOpcode(numSlots: Int): Opcode {
        TODO()
    }
}

class ModuleScope : GlobalScope() {
    override fun createEnterScopeOpcode(numSlots: Int): Opcode {
        TODO()
    }
}

data class Variable(
    val name: String,
    val type: Type,
    val mode: Mode,
    var source: VariableSourceNode,
) {
    /**
     * The slot index of this variable in its context
     */
    var slot = -1

    var possiblyUsedBeforeDecl = false

    val scope: Scope get() = source.scope

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
