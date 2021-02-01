package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.statements.BlockNode

/**
 * The scope analyzer is responsible for "filling in" many of
 * the AST fields.
 *
 * First and foremost, it connects identifiers with their
 * source: either a function parameter, a variable declaration,
 * or a global variable.
 */
class ScopeAnalyzer : ASTVisitor {
    private lateinit var topLevelScope: Scope
    private lateinit var scope: Scope
    private lateinit var topLevelNode: NodeWithScope

    fun analyze(script: ScriptNode) {
        topLevelScope = Scope(Scope.Type.ScriptScope, null)
        scope = topLevelScope
        script.scope = scope
        topLevelNode = script

        script.variableDeclarations().forEach(::handleVarDeclaration)
        script.lexicalDeclarations().forEach(::handleLexicalDeclaration)

        visit(script.statementList)
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
        val variable = scope.getVariable(node.identifierName)
        val source = variable?.source ?: topLevelNode
        node.source = source

        if (source is VariableSourceNode && scope.crossesVarBoundary(source.scope))
            variable!!.isInlineable = false

        super.visitBindingIdentifier(node)
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        val variable = scope.getVariable(node.identifierName)
        val source = variable?.source ?: topLevelNode
        node.source = source

        // TODO: Somehow make function parameters VariableSourceNodes
        if (source is VariableSourceNode && scope.crossesVarBoundary(source.scope))
            variable!!.isInlineable = false

        super.visitIdentifierReference(node)
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        pushScope(Scope.Type.FunctionScope)
        node.scope = scope

        node.parameters.forEach {
            it.boundNames().forEach { name ->
                scope.addVariable(name, Variable(
                    name,
                    Variable.Mode.Var,
                    Variable.Kind.FunctionParameter,
                    node, // TODO: should this be the actual param itself?
                ))
            }
        }

        visit(node.body)

        popScope()
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        pushScope(Scope.Type.FunctionScope)
        node.scope = scope

        node.parameters.forEach {
            it.boundNames().forEach { name ->
                scope.addVariable(name, Variable(
                    name,
                    Variable.Mode.Var,
                    Variable.Kind.FunctionParameter,
                    node, // TODO: should this be the actual param itself?
                ))
            }
        }

        visit(node.body)

        popScope()
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        scope.addVariable(node.name, Variable(
            node.name,
            Variable.Mode.Var,
            Variable.Kind.Normal,
            node
        ))

        pushScope(Scope.Type.FunctionScope)

        node.parameters.forEach {
            it.boundNames().forEach { name ->
                scope.addVariable(name, Variable(
                    name,
                    Variable.Mode.Var,
                    Variable.Kind.FunctionParameter,
                    it, // TODO(BindingPattern): Narrow the source here (i.e. not just the entire binding node)
                ))
            }
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
        node.boundNames().forEach { name ->
            val variable = Variable(
                name,
                Variable.Mode.Var,
                Variable.Kind.Normal,
                node as NodeWithScope, // TODO: Let's avoid this cast somehow
            )
            scope.addVariable(name, variable)
        }
    }

    private fun handleLexicalDeclaration(node: VariableSourceNode) {
        val mode = if (node.isConst) Variable.Mode.Const else Variable.Mode.Let

        node.boundNames().forEach { name ->
            val variable = Variable(
                name,
                mode,
                Variable.Kind.Normal,
                node as NodeWithScope, // TODO: Let's avoid this cast somehow
            )
            scope.addVariable(name, variable)
        }
    }
}

data class Variable(
    val name: String,
    var mode: Mode,
    val kind: Kind,
    val source: NodeWithScope,
) {
    lateinit var scope: Scope
    var isUsed = false
    var isInlineable = true
    var index: Int = -1

    enum class Mode {
        Var,
        Let,
        Const,
    }

    enum class Kind {
        Normal,
        FunctionParameter,
    }
}

enum class LanguageMode {
    Strict,
    Sloppy,
}

class Scope(val type: Type, val outer: Scope? = null) {
    private val variableMap = mutableMapOf<String, Variable>()
    private var crossBoundaryScopeCache = mutableMapOf<Scope, Boolean>()

    fun hasVariable(name: String): Boolean = name in variableMap || outer?.hasVariable(name) == true

    fun getVariable(name: String): Variable? {
        return if (name in variableMap) variableMap[name] else outer?.getVariable(name)
    }

    fun addVariable(name: String, value: Variable) {
        if (name in variableMap)
            TODO("Isn't this not possible? Should it be handled?")

        value.scope = this
        variableMap[name] = value
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
