package me.mattco.reeva.parser

import me.mattco.reeva.ast.FunctionDeclarationNode
import me.mattco.reeva.ast.VariableRefNode
import me.mattco.reeva.ast.VariableSourceNode

open class Scope(val outer: Scope? = null) {
    val childScopes = mutableListOf<Scope>()
    val parentHoistingScope: HoistingScope by lazy { firstParentOfType() }
    val globalScope: GlobalScope by lazy { firstParentOfType() }

    val declaredVariables = mutableListOf<Variable>()

    // Variables that have yet to be connected to their source
    val pendingReferences = mutableListOf<VariableRefNode>()

    var possiblyReferencesArguments = false
    var functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

    private var nextInlineableRegister = 0
    var additionalInlineableRegisterCount = 0
    var nextSlot = 0
        private set

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

    fun addFunction(node: FunctionDeclarationNode) {
        functionsToInitialize.add(node)
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

    fun onFinish() {
        processUnlinkedNodes()
        searchForUseBeforeDecl()
        allocateInlineableRegisters(1)
        onFinishImpl()

        pendingReferences.clear()
        childScopes.forEach { it.pendingReferences.clear() }
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
    
    private fun allocateInlineableRegisters(startingRegister: Int) {
        nextInlineableRegister = startingRegister

        declaredVariables.filter {
            it.mode == Variable.Mode.Parameter
        }.forEachIndexed { index, variable ->
            variable.slot = if (variable.isInlineable) {
                nextInlineableRegister = index + 2
                index + 1
            } else {
                nextSlot++
            }
        }

        declaredVariables.filter {
            it.mode != Variable.Mode.Parameter
        }.forEach {
            it.slot = if (it.isInlineable) {
                additionalInlineableRegisterCount++
                nextInlineableRegister++
            } else nextSlot++
        }

        childScopes.forEach {
            if (it is HoistingScope) {
                it.allocateInlineableRegisters(1)
            } else {
                it.allocateInlineableRegisters(nextInlineableRegister)
                nextInlineableRegister += it.additionalInlineableRegisterCount
                additionalInlineableRegisterCount += it.additionalInlineableRegisterCount
            }
        }
    }

    protected open fun onFinishImpl() {
        childScopes.forEach(Scope::onFinishImpl)
    }
}

open class HoistingScope(outer: Scope? = null) : Scope(outer) {
    var hasUseStrictDirective: Boolean = false

    override fun onFinishImpl() {
        possiblyReferencesArguments = searchForArgumentsReference(this)
        super.onFinishImpl()
    }

    private fun searchForArgumentsReference(scope: Scope): Boolean {
        if (this is GlobalScope || scope.declaredVariables.any { it.name == "arguments" })
            return false

        var found = false

        for (node in scope.pendingReferences) {
            if (node.boundName() == "arguments") {
                node.targetVar.type = Variable.Type.Const
                node.targetVar.mode = Variable.Mode.Declared
                node.targetVar.scope = this
                found = true
            }
        }

        return found || scope.childScopes.filter { it !is HoistingScope }.any { searchForArgumentsReference(it) }
    }
}

open class GlobalScope : HoistingScope() {
    override fun requiresEnv() = false

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
