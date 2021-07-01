package me.mattco.reeva.parser

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.UnaryExpressionNode
import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.utils.unreachable

class ScopeResolver : ASTVisitor {
    private lateinit var scope: Scope

    fun resolve(script: ScriptNode) {
        val globalScope = GlobalScope()
        scope = globalScope
        globalScope.hasUseStrictDirective = script.hasUseStrict

        script.scope = scope

        visit(script.statements)

        scope.onFinish()
    }

    fun resolve(function: FunctionDeclarationNode) {
        val globalScope = GlobalScope()
        scope = globalScope

        function.scope = scope

        visit(function)

        scope.onFinish()
    }

    override fun visitBlock(node: BlockNode) {
        visitBlock(node, pushScope = true)
    }

    private fun visitBlock(node: BlockNode, pushScope: Boolean) {
        if (pushScope)
            scope = Scope(scope)

        node.scope = scope
        visit(node.statements)

        if (pushScope)
            scope = scope.outer!!
    }

    override fun visitVariableDeclaration(node: VariableDeclarationNode) {
        for (decl in node.declarations) {
            decl.scope = scope.parentHoistingScope
            decl.variable = Variable(
                decl.identifier.identifierName,
                Variable.Type.Var,
                decl.scope.declaredVarMode(Variable.Type.Var),
                decl,
            )
            decl.scope.addDeclaredVariable(decl.variable)

            if (decl.initializer != null)
                visit(decl.initializer)
        }
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        for (decl in node.declarations) {
            decl.scope = scope
            decl.variable = Variable(
                decl.identifier.identifierName,
                Variable.Type.Var,
                Variable.Mode.Declared,
                decl,
            )
            decl.scope.addDeclaredVariable(decl.variable)

            if (decl.initializer != null)
                visit(decl.initializer)
        }
    }

    override fun visitBindingIdentifier(node: BindingIdentifierNode) {
        unreachable()
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        node.scope = scope
        scope.addReference(node)
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        val declarationScope = scope.parentHoistingScope
        node.scope = declarationScope

        val identifier = node.identifier
        identifier.scope = declarationScope
        identifier.variable = Variable(
            identifier.identifierName,
            Variable.Type.Var,
            declarationScope.declaredVarMode(Variable.Type.Var),
            node,
        )
        declarationScope.addDeclaredVariable(identifier.variable)

        node.functionScope = visitFunctionHelper(node.parameters, node.body)
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body)
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body)
    }

    override fun visitMethodDefinition(node: MethodDefinitionNode) {
        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body)
    }

    private fun visitFunctionHelper(parameters: ParameterList, body: ASTNode): Scope {
        val functionScope = HoistingScope(scope)
        scope = functionScope

        if (body is BlockNode && body.hasUseStrict)
            functionScope.hasUseStrictDirective = true

        for (param in parameters) {
            val paramIdent = param.identifier
            paramIdent.variable = Variable(
                param.identifier.identifierName,
                Variable.Type.Let,
                Variable.Mode.Parameter,
                param.identifier,
            )
            paramIdent.scope = scope
            functionScope.addDeclaredVariable(paramIdent.variable)

            if (param.initializer != null)
                visit(param.initializer)
        }

        val bodyScope = if (body is BlockNode && !parameters.isSimple()) {
            HoistingScope(scope).also {
                scope = it
            }
        } else functionScope

        if (body is BlockNode)
            body.scope = bodyScope

        if (body is BlockNode) {
            visitBlock(body, pushScope = false)
        } else visit(body)

        if (bodyScope != functionScope)
            scope = scope.outer!!

        scope = scope.outer!!

        return functionScope
    }

    override fun visitTryStatement(node: TryStatementNode) {
        val catchNode = node.catchNode
        val finallyBlock = node.finallyBlock

        visitBlock(node.tryBlock)

        if (catchNode != null) {
            val parameter = catchNode.parameter
            if (parameter != null) {
                scope = Scope(scope)
                parameter.scope = scope
                parameter.variable = Variable(
                    parameter.identifierName,
                    Variable.Type.Let,
                    Variable.Mode.Declared,
                    parameter,
                )
                scope.addDeclaredVariable(parameter.variable)

                visitBlock(catchNode.block, pushScope = false)

                scope = scope.outer!!
            } else {
                visitBlock(catchNode.block)
            }
        }

        if (finallyBlock != null)
            visitBlock(finallyBlock)
    }

    override fun visitForStatement(node: ForStatementNode) {
        val initializer = node.initializer
        val needInitScope = initializer is VariableDeclarationNode || initializer is LexicalDeclarationNode

        if (needInitScope) {
            scope = Scope(scope)
            node.initializerScope = scope
        }

        if (initializer != null)
            visit(initializer)

        if (node.condition != null)
            visit(node.condition)

        if (node.incrementer != null)
            visit(node.incrementer)

        visit(node.body)

        if (needInitScope)
            scope = scope.outer!!
    }

    override fun visitForIn(node: ForInNode) {
        visitForEach(node.decl, node.expression, node.body)
    }

    override fun visitForOf(node: ForOfNode) {
        visitForEach(node.decl, node.expression, node.body)
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        visitForEach(node.decl, node.expression, node.body)
    }

    private fun visitForEach(decl: ASTNode, expression: ExpressionNode, body: StatementNode) {
        val needScope = decl is VariableDeclarationNode || decl is LexicalDeclarationNode || body is BlockNode

        val blockScope = Scope(scope)
        if (needScope)
            scope = blockScope

        visit(decl)
        if (needScope)
            scope = scope.outer!!
        visit(expression)
        if (needScope)
            scope = blockScope

        if (body is BlockNode) {
            visitBlock(body, pushScope = false)
        } else visit(body)

        if (needScope)
            scope = scope.outer!!
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        node.scope = scope
        super.visitUnaryExpression(node)
    }
}