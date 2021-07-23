package me.mattco.reeva.parsing

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.NewTargetNode
import me.mattco.reeva.ast.expressions.UnaryExpressionNode
import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.literals.ThisLiteralNode
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.utils.expect

class ScopeResolver : ASTVisitor {
    private lateinit var scope: Scope

    fun resolve(script: ScriptNode) {
        val globalScope = GlobalScope()
        scope = globalScope
        globalScope.isStrict = script.hasUseStrict

        script.scope = scope

        visit(script.statements)

        scope.finish()
    }

    fun resolve(function: FunctionDeclarationNode) {
        val globalScope = GlobalScope()
        scope = globalScope

        function.scope = scope

        visit(function)

        scope.finish()
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
            val declaredScope = scope
            val hoistedScope = scope.outerHoistingScope

            decl.type = VariableType.Var
            decl.mode = if (hoistedScope is GlobalScope) {
                VariableMode.Global
            } else VariableMode.Declared

            declaredScope.addVariableSource(decl)
            hoistedScope.addHoistedVariable(decl)

            if (decl.initializer != null)
                visit(decl.initializer)
        }
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        val type = if (node.isConst) VariableType.Const else VariableType.Let

        for (decl in node.declarations) {
            scope.addVariableSource(decl)

            decl.type = type
            decl.mode = VariableMode.Declared

            if (decl.initializer != null)
                visit(decl.initializer)
        }
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        scope.addVariableReference(node)
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        val declaredScope = scope
        val hoistedScope = scope.outerHoistingScope

        node.type = VariableType.Var
        node.mode = if (hoistedScope is GlobalScope) {
            VariableMode.Global
        } else VariableMode.Declared

        declaredScope.addVariableSource(node)
        hoistedScope.addHoistedVariable(node)

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
        node.functionScope = visitFunctionHelper(node.parameters, node.body, isLexical = false)
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        expect(node.identifier != null)
        node.mode = VariableMode.Declared
        node.type = VariableType.Const
        scope.addVariableSource(node)

        visitClassHelper(node.classNode)
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        visitClassHelper(node.classNode)
    }

    private fun visitClassHelper(node: ClassNode) {
        if (node.heritage != null)
            visit(node.heritage)

        val classScope = HoistingScope(scope)
        scope = classScope
        classScope.isStrict = true
        node.scope = classScope

        for (element in node.body) {
            if (element is ClassMethodNode) {
                visitMethodDefinition(element.method)
                if (element.isConstructor())
                    (element.method.functionScope as HoistingScope).isDerivedClassConstructor = true
            } else {
                expect(element is ClassFieldNode)
                visitPropertyName(element.identifier)
                if (element.initializer != null)
                    visit(element.initializer)
            }
        }

        scope = scope.outer!!
    }

    private fun visitFunctionHelper(parameters: ParameterList, body: ASTNode, isLexical: Boolean): Scope {
        val functionScope = HoistingScope(scope, isLexical)
        scope = functionScope

        if (body is BlockNode && body.hasUseStrict)
            functionScope.isStrict = true

        var hasParameterExpressions = false
        var hasSimpleParams = true
        var hasArgumentsIdentifier = false

        for (param in parameters) {
            param.type = VariableType.Var
            param.mode = VariableMode.Parameter

            if (param.name() == "arguments")
                hasArgumentsIdentifier = true
            if (param.initializer != null)
                hasParameterExpressions = true
            if (!param.isSimple())
                hasSimpleParams = false

            scope.addVariableSource(param)

            if (param.initializer != null)
                visit(param.initializer)
        }

        val bodyScope = if (body is BlockNode && !parameters.isSimple()) {
            // The body scope shouldn't be a target for the receiver or new.target
            // sources
            HoistingScope(scope, isLexical = true).also {
                scope = it
            }
        } else functionScope

        if (body is BlockNode) {
            body.scope = bodyScope
            visitBlock(body, pushScope = false)
        } else {
            visit(body)
        }

        if (bodyScope != functionScope)
            scope = scope.outer!!

        scope = scope.outer!!

        for (variable in bodyScope.variableSources) {
            if (variable.name() == "arguments")
                hasArgumentsIdentifier = true
        }

        val argumentsObjectNeeded = !isLexical && !hasParameterExpressions && !hasArgumentsIdentifier

        functionScope.argumentsMode = when {
            !argumentsObjectNeeded -> HoistingScope.ArgumentsMode.None
            bodyScope.isStrict || !hasSimpleParams -> HoistingScope.ArgumentsMode.Unmapped
            else -> HoistingScope.ArgumentsMode.Mapped
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
                parameter.type = VariableType.Let
                parameter.mode = VariableMode.Declared

                scope = Scope(scope)
                scope.addVariableSource(parameter)

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
        visitForEach(node)
    }

    override fun visitForOf(node: ForOfNode) {
        visitForEach(node)
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        visitForEach(node)
    }

    private fun visitForEach(node: ForEachNode) {
        visit(node.expression)

        val decl = node.decl
        val body = node.body

        val needsDeclScope = decl is VariableDeclarationNode || decl is LexicalDeclarationNode || body is BlockNode
        if (needsDeclScope) {
            scope = Scope(scope)
            node.initializerScope = scope
        }

        visit(decl)
        visit(body)

        if (needsDeclScope)
            scope = scope.outer!!
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        node.scope = scope
        super.visitUnaryExpression(node)
    }

    override fun visitThisLiteral(node: ThisLiteralNode) {
        node.scope = scope
        scope.outerHoistingScope.addReceiverReference(node)
    }

    override fun visitNewTargetExpression(node: NewTargetNode) {
        node.scope = scope
    }
}
