package me.mattco.reeva.parsing

import me.mattco.reeva.ast.BindingIdentifierNode
import me.mattco.reeva.ast.FunctionDeclarationNode
import me.mattco.reeva.ast.VariableRefNode
import me.mattco.reeva.ast.VariableSourceNode
import me.mattco.reeva.ast.statements.VariableDeclarationNode

open class Scope(val outer: Scope? = null) {
    val childScopes = mutableListOf<Scope>()
    val parentHoistingScope: HoistingScope by lazy { firstParentOfType() }
    val globalScope: GlobalScope by lazy { firstParentOfType() }

    val declaredVariables = mutableListOf<Variable>()

    // Variables that have yet to be connected to their source
    val pendingReferences = mutableListOf<VariableRefNode>()

    var functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
    var hoistedVarDecls = mutableListOf<VariableDeclarationNode>()

    protected var nextInlineableRegister = 0
    var additionalInlineableRegisterCount = 0
    var nextSlot = 0
        protected set

    val isStrict: Boolean
        get() = parentHoistingScope.hasUseStrictDirective

    init {
        @Suppress("LeakingThis")
        outer?.childScopes?.add(this)
    }

    open fun declaredVarMode(type: Variable.Type): Variable.Mode = Variable.Mode.Declared

    open fun requiresEnv(): Boolean = declaredVariables.any { !it.isInlineable }

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
        var i = 0
        while (scope != ancestorScope) {
            if (scope.requiresEnv())
                i++
            scope = scope.outer!!
        }
        return i
    }

    open fun onFinish() {
        processUnlinkedNodes()
        searchForUseBeforeDecl()
        allocateSlots(1)
        clearPendingReferences()
    }

    private fun processUnlinkedNodes() {
        // Attempt to connect any remaining global var references
        val iter = pendingReferences.iterator()
        while (iter.hasNext()) {
            val node = iter.next()
            val variable = findDeclaredVariable(node.targetVar.name)
            if (variable != null) {
                node.targetVar = variable

                if (node.scope.parentHoistingScope != node.targetVar.scope.parentHoistingScope)
                    node.targetVar.isInlineable = false

                iter.remove()
            }
        }

        childScopes.forEach(Scope::processUnlinkedNodes)
    }

    private fun searchForUseBeforeDecl() {
        outer@ for (node in pendingReferences) {
            val refStart = node.sourceStart.index
            val varStart = node.targetVar.source.sourceStart.index

            // If the declaration of the variable comes after the use of the variable in
            // the source code, it's likely that it is used before the declaration
            if (refStart < varStart)
                node.targetVar.possiblyUsedBeforeDecl = true

            // If the scope of the reference doesn't have the scope of the target somewhere
            // in its scope hierarchy, it is likely relying on var hoisting (or just an error)
            var scope: Scope? = node.scope
            while (scope != null) {
                if (scope == node.targetVar.scope)
                    continue@outer
                scope = scope.outer
            }

            node.targetVar.possiblyUsedBeforeDecl = true
        }

        childScopes.forEach(Scope::searchForUseBeforeDecl)
    }
    
    open fun allocateSlots(startingRegister: Int) {
        nextInlineableRegister = startingRegister

        declaredVariables.forEach {
            it.slot = if (it.isInlineable) {
                additionalInlineableRegisterCount++
                nextInlineableRegister++
            } else nextSlot++
        }

        childScopes.forEach {
            it.allocateSlots(nextInlineableRegister)
            if (it !is HoistingScope) {
                nextInlineableRegister += it.additionalInlineableRegisterCount
                additionalInlineableRegisterCount += it.additionalInlineableRegisterCount
            }
        }
    }

    private fun clearPendingReferences() {
        pendingReferences.clear()
        childScopes.forEach(Scope::clearPendingReferences)
    }
}

open class HoistingScope(outer: Scope? = null) : Scope(outer) {
    var hasUseStrictDirective: Boolean = false
    var argumentsObjectMode = ArgumentsObjectMode.None
    var argumentsObjectVariable: Variable? = null

    override fun onFinish() {
        initializeArgumentsObjectVariable(this)
        super.onFinish()
    }

    override fun allocateSlots(startingRegister: Int) {
        val parameters = declaredVariables.filter { it.mode == Variable.Mode.Parameter }
        val locals = declaredVariables.filter { it.mode != Variable.Mode.Parameter }

        nextInlineableRegister = parameters.size + 1

        parameters.forEachIndexed { index, variable ->
            variable.slot = if (variable.isInlineable) {
                index + 1
            } else nextSlot++
        }

        locals.forEach {
            it.slot = if (it.isInlineable) {
                additionalInlineableRegisterCount++
                nextInlineableRegister++
            } else nextSlot++
        }

        childScopes.forEach {
            it.allocateSlots(nextInlineableRegister)
            if (it !is HoistingScope) {
                nextInlineableRegister += it.additionalInlineableRegisterCount
                additionalInlineableRegisterCount += it.additionalInlineableRegisterCount
            }
        }
    }

    enum class ArgumentsObjectMode {
        None,
        Unmapped,
        Mapped,
    }

    // TODO: Get better at OOP and refactor Scopes to not be so weirdly recursive
    companion object {
        private fun initializeArgumentsObjectVariable(scope: Scope) {
            if (scope is HoistingScope && scope.argumentsObjectMode != ArgumentsObjectMode.None) {
                val refs = searchForArgumentsReference(scope)
                if (refs.isNotEmpty()) {
                    val dummySourceNode = BindingIdentifierNode("arguments")
                    dummySourceNode.scope = scope
                    val argumentsVariable = Variable(
                        "arguments",
                        Variable.Type.Const,
                        Variable.Mode.Declared,
                        dummySourceNode,
                    )
                    dummySourceNode.variable = argumentsVariable
                    scope.addDeclaredVariable(argumentsVariable)
                    scope.argumentsObjectVariable = argumentsVariable
                } else {
                    scope.argumentsObjectMode = ArgumentsObjectMode.None
                }
            }

            scope.childScopes.forEach { initializeArgumentsObjectVariable(it) }
        }

        private fun searchForArgumentsReference(scope: Scope): List<VariableRefNode> {
            if (scope is GlobalScope || scope.declaredVariables.any { it.name == "arguments" })
                return emptyList()

            val refs = mutableListOf<VariableRefNode>()

            for (node in scope.pendingReferences) {
                if (node.boundName() == "arguments")
                    refs.add(node)
            }

            return refs + scope.childScopes.filter { it !is HoistingScope }.map { searchForArgumentsReference(it) }.flatten()
        }
    }
}

open class GlobalScope : HoistingScope() {
    override fun declaredVarMode(type: Variable.Type): Variable.Mode {
        return if (type == Variable.Type.Var) {
            Variable.Mode.Global
        } else Variable.Mode.Declared
    }
}

data class Variable(
    val name: String,
    var type: Type,
    var mode: Mode,
    var source: VariableSourceNode,
) {
    var possiblyUsedBeforeDecl = false

    var scope: Scope
        get() = source.scope
        set(value) { source.scope = value }

    var isInlineable = true
    var slot: Int = -1

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
