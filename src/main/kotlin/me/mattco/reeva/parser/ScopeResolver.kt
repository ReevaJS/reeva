package me.mattco.reeva.parser

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.UnaryExpressionNode
import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.utils.expect
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
            val declarationScope = scope
            val hoistedScope = scope.parentHoistingScope

            decl.scope = hoistedScope
            decl.declaredScope = declarationScope

            decl.variable = Variable(
                decl.identifier.identifierName,
                Variable.Type.Var,
                hoistedScope.declaredVarMode(Variable.Type.Var),
                decl,
            )
            hoistedScope.addDeclaredVariable(decl.variable)

            if (hoistedScope != declarationScope)
                declarationScope.hoistedVarDecls.add(node)

            if (decl.initializer != null)
                visit(decl.initializer)
        }
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        for (decl in node.declarations) {
            decl.scope = scope
            decl.variable = Variable(
                decl.identifier.identifierName,
                if (node.isConst) Variable.Type.Const else Variable.Type.Let,
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
        val declarationScope = scope
        val hoistedScope = scope.parentHoistingScope
        node.scope = hoistedScope
        node.declaredScope = declarationScope

        if (hoistedScope != declarationScope)
            declarationScope.functionsToInitialize.add(node)

        val identifier = node.identifier
        identifier.scope = hoistedScope
        identifier.variable = Variable(
            identifier.identifierName,
            Variable.Type.Var,
            hoistedScope.declaredVarMode(Variable.Type.Var),
            node,
        )
        hoistedScope.addDeclaredVariable(identifier.variable)

        node.functionScope = visitFunctionHelper(node.parameters, node.body, isLexical = false)
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body, isLexical = false)
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body, isLexical = true)
    }

    override fun visitMethodDefinition(node: MethodDefinitionNode) {
        node.scope = scope
        visit(node.propName)
        node.functionScope = visitFunctionHelper(node.parameters, node.body, isLexical = true)
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        expect(node.identifier != null)
        val identifier = node.identifier
        node.scope = scope
        identifier.scope = scope
        identifier.variable = Variable(
            identifier.identifierName,
            Variable.Type.Var,
            Variable.Mode.Declared,
            node,
        )
        scope.addDeclaredVariable(identifier.variable)

        visitClassHelper(node.classNode)
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        visitClassHelper(node.classNode)
    }

    private fun visitClassHelper(node: ClassNode) {
        val classScope = HoistingScope(scope)
        scope = classScope
        classScope.hasUseStrictDirective = true
        node.scope = classScope

        for (element in node.body) {
            if (element is ClassMethodNode)
                visitMethodDefinition(element.method)
        }

        scope = scope.outer!!
    }

    private fun visitFunctionHelper(parameters: ParameterList, body: ASTNode, isLexical: Boolean): Scope {
        val functionScope = HoistingScope(scope)
        scope = functionScope

        if (body is BlockNode && body.hasUseStrict)
            functionScope.hasUseStrictDirective = true

        var hasParameterExpressions = false
        var hasSimpleParams = true
        var hasArgumentsIdentifier = false

        for (param in parameters) {
            val paramIdent = param.identifier

            if (paramIdent.identifierName == "arguments")
                hasArgumentsIdentifier = true
            if (param.initializer != null)
                hasParameterExpressions = true
            if (!param.isSimple())
                hasSimpleParams = false

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

        for (variable in bodyScope.declaredVariables) {
            val name = when {
                variable.source is FunctionDeclarationNode -> variable.name
                variable.type != Variable.Type.Var -> variable.name
                else -> continue
            }

            if (name == "arguments")
                hasArgumentsIdentifier = true
        }

        val argumentsObjectNeeded = !isLexical && !hasParameterExpressions && !hasArgumentsIdentifier

        functionScope.argumentsObjectMode = when {
            !argumentsObjectNeeded -> HoistingScope.ArgumentsObjectMode.None
            bodyScope.isStrict || !hasSimpleParams -> HoistingScope.ArgumentsObjectMode.Unmapped
            else -> HoistingScope.ArgumentsObjectMode.Mapped
        }

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
