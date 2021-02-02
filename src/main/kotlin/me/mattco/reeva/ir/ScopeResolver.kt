package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.statements.BlockNode
import me.mattco.reeva.ast.statements.LexicalDeclarationNode
import me.mattco.reeva.ast.statements.VariableDeclarationNode

/**
 * The scope resolver is responsible for "filling in" many of
 * the AST fields.
 *
 * First and foremost, it connects identifiers with their
 * source: either a function parameter, a variable declaration,
 * or a global variable.
 */
class ScopeResolver : ASTVisitor {
    private lateinit var topLevelScope: Scope
    private lateinit var scope: Scope
    private lateinit var topLevelNode: VariableSourceNode

    fun resolve(script: ScriptNode) {
        topLevelScope = Scope(Scope.Type.ScriptScope, null)
        scope = topLevelScope
        script.scope = scope
        topLevelNode = script

        script.variableDeclarations().forEach(::handleVarDeclaration)
        script.lexicalDeclarations().forEach(::handleLexicalDeclaration)

        visit(script.statements)
    }

    override fun visit(node: ASTNode) {
        if (node is NodeWithScope)
            node.scope = scope
        super.visit(node)
    }

    override fun visitBlock(node: BlockNode) {
        pushScope(Scope.Type.BlockScope)
        node.scope = scope
        node.scopedLexicalDeclarations().forEach(::handleLexicalDeclaration)
        super.visitBlock(node)
        popScope()
    }

    override fun visitBindingIdentifier(node: BindingIdentifierNode) {
        val variable = getVariable(node.identifierName)
        val source = variable.source
        node.variable = variable

        if (scope.crossesVarBoundary(source.scope))
            variable.isInlineable = false

        super.visitBindingIdentifier(node)
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        val variable = getVariable(node.identifierName)
        val source = variable.source
        node.variable = variable

        if (scope.crossesVarBoundary(source.scope))
            variable.isInlineable = false

        super.visitIdentifierReference(node)
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        node.declarations.forEach {
            it.scope = scope
            it.variable = getVariable(it.identifier.identifierName)
        }
        super.visitLexicalDeclaration(node)
    }

    override fun visitVariableDeclaration(node: VariableDeclarationNode) {
        node.declarations.forEach {
            it.scope = scope
            it.variable = getVariable(it.identifier.identifierName)
        }
        super.visitVariableDeclaration(node)
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        node.variable = Variable(
            node.identifier.identifierName,
            Variable.Mode.Var,
            Variable.Kind.Normal,
            node
        )
        scope.addVariable(node.identifier.identifierName, node.variable)

        node.scope = scope
        pushScope(Scope.Type.FunctionScope)

        node.parameters.forEach {
            val name = it.boundName()
            it.scope = scope
            it.variable = Variable(
                name,
                Variable.Mode.Var,
                Variable.Kind.FunctionParameter,
                it,
            )
            scope.addVariable(name, it.variable)
        }

        visit(node.body)

        popScope()
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        node.scope = scope
        pushScope(Scope.Type.FunctionScope)

        node.parameters.forEach {
            val name = it.boundName()
            it.scope = scope
            it.variable = Variable(
                name,
                Variable.Mode.Var,
                Variable.Kind.FunctionParameter,
                it,
            )
            scope.addVariable(name, it.variable)
        }

        visit(node.body)

        popScope()
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        pushScope(Scope.Type.FunctionScope)

        node.parameters.forEach {
            val name = it.boundName()
            scope.addVariable(name, Variable(
                name,
                Variable.Mode.Var,
                Variable.Kind.FunctionParameter,
                it, // TODO(BindingPattern): Narrow the source here (i.e. not just the entire binding node)
            ))
        }

        visit(node.body)

        popScope()
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        TODO()
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        TODO()
    }

    private fun pushScope(type: Scope.Type) {
        scope = Scope(type, scope)
    }

    private fun popScope() {
        scope = scope.outer!!
    }

    private fun handleVarDeclaration(node: VariableSourceNode) {
        val name = node.boundName()
        scope.addVariable(name, Variable(
            name,
            Variable.Mode.Var,
            Variable.Kind.Normal,
            node,
        ))
    }

    private fun handleLexicalDeclaration(node: VariableSourceNode) {
        val name = node.boundName()
        val mode = if (node.isConst) Variable.Mode.Const else Variable.Mode.Let

        scope.addVariable(name, Variable(
            name,
            mode,
            Variable.Kind.Normal,
            node,
        ))
    }

    private fun getVariable(name: String) = scope.getVariable(name) ?: Variable(
        name,
        Variable.Mode.Var,
        Variable.Kind.Global,
        topLevelNode
    ).also {
        topLevelScope.addVariable(name, it)
    }
}

data class Variable(
    val name: String,
    var mode: Mode,
    val kind: Kind,
    val source: VariableSourceNode,
) {
    lateinit var scope: Scope
    var isUsed = false
    var isInlineable = true
    var index: Int = -1

    init {
        if (kind == Kind.Global)
            isInlineable = false
    }

    enum class Mode {
        Var,
        Let,
        Const,
    }

    enum class Kind {
        Normal,
        Global,
        FunctionParameter,
    }
}

enum class LanguageMode {
    Strict,
    Sloppy,
}

class Scope(val type: Type, val outer: Scope? = null) {
    val variables = mutableMapOf<String, Variable>()
    private var crossBoundaryScopeCache = mutableMapOf<Scope, Boolean>()

    fun hasVariable(name: String): Boolean = name in variables || outer?.hasVariable(name) == true

    fun getVariable(name: String): Variable? {
        return if (name in variables) variables[name] else outer?.getVariable(name)
    }

    fun addVariable(name: String, value: Variable) {
        if (name in variables)
            TODO("Isn't this not possible? Should it be handled?")

        value.scope = this
        variables[name] = value
    }

    var languageMode = LanguageMode.Sloppy

    // Determines if a scope in this scope's parent chain is linked
    // across a var-binding boundary (i.e. function or class boundary)
    fun crossesVarBoundary(parentScope: Scope): Boolean {
        return crossBoundaryScopeCache.getOrPut(parentScope) {
            var currScope: Scope? = this

            while (currScope != null) {
                if (currScope == parentScope)
                    return@getOrPut false

                if (currScope.type == Type.FunctionScope || currScope.type == Type.ClassScope)
                    return@getOrPut true

                currScope = currScope.outer
            }

            throw IllegalStateException("Scope $parentScope is not in the parent chain of scope $this")
        }
    }

    enum class Type {
        ClassScope,
        EvalScope,
        FunctionScope,
        ModuleScope,
        ScriptScope,
        CatchScope,
        BlockScope,
        WithScope,
    }
}
