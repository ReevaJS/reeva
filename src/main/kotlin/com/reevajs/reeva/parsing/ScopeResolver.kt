package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.NewTargetNode
import com.reevajs.reeva.ast.expressions.UnaryExpressionNode
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.literals.ThisLiteralNode
import com.reevajs.reeva.ast.statements.*
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

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
            val mode = if (scope.outerHoistingScope is GlobalScope) {
                VariableMode.Global
            } else VariableMode.Declared

            visitDeclaration(decl, mode, VariableType.Var)
        }
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        val type = if (node.isConst) VariableType.Const else VariableType.Let

        for (decl in node.declarations) {
            visitDeclaration(decl, VariableMode.Declared, type)
        }
    }

    private fun visitDeclaration(declaration: Declaration, mode: VariableMode, type: VariableType) {
        when (declaration) {
            is NamedDeclaration -> {
                scope.addVariableSource(declaration)
                if (type == VariableType.Var)
                    scope.outerHoistingScope.addHoistedVariable(declaration)

                declaration.type = type
                declaration.mode = mode
            }
            is DestructuringDeclaration -> {
                visitBindingPattern(declaration.pattern, mode, type)
            }
        }

        declaration.initializer?.let(::visit)
    }

    private fun visitBindingPattern(pattern: BindingPatternNode, mode: VariableMode, type: VariableType) {
        when (pattern.kind) {
            BindingKind.Object -> pattern.bindingProperties.forEach { visitBindingProperty(it, mode, type) }
            BindingKind.Array -> pattern.bindingElements.forEach { visitBindingElement(it, mode, type) }
        }
    }

    private fun visitBindingProperty(node: BindingProperty, mode: VariableMode, type: VariableType) {
        when (node) {
            is BindingRestProperty -> visitBindingDeclaration(node.declaration, mode, type)
            is SimpleBindingProperty -> {
                if (node.alias != null) {
                    visitBindingDeclarationOrPattern(node.alias, mode, type)
                } else {
                    visitBindingDeclaration(node.declaration, mode, type)
                }
                node.initializer?.let(::visit)
            }
            is ComputedBindingProperty -> {
                visit(node.name)
                visitBindingDeclarationOrPattern(node.alias, mode, type)
                node.initializer?.let(::visit)
            }
        }
    }

    private fun visitBindingElement(node: BindingElement, mode: VariableMode, type: VariableType) {
        when (node) {
            is BindingElisionElement -> { /* no-op */ }
            is BindingRestElement -> visitBindingDeclarationOrPattern(node.declaration, mode, type)
            is SimpleBindingElement -> {
                visitBindingDeclarationOrPattern(node.alias, mode, type)
                node.initializer?.let(::visit)
            }
        }
    }

    private fun visitBindingDeclarationOrPattern(node: BindingDeclarationOrPattern, mode: VariableMode, type: VariableType) {
        if (node.isBindingPattern) {
            visitBindingPattern(node.asBindingPattern, mode, type)
        } else {
            visitBindingDeclaration(node.asBindingDeclaration, mode, type)
        }
    }

    private fun visitBindingDeclaration(node: BindingDeclaration, mode: VariableMode, type: VariableType) {
        scope.addVariableSource(node)
        if (type == VariableType.Var)
            scope.outerHoistingScope.addHoistedVariable(node)

        node.type = type
        node.mode = mode
    }

    override fun visitBindingDeclaration(node: BindingDeclaration) {
        unreachable()
    }

    override fun visitBindingDeclarationOrPattern(node: BindingDeclarationOrPattern) {
        unreachable()
    }

    override fun visitBindingRestProperty(node: BindingRestProperty) {
        unreachable()
    }

    override fun visitSimpleBindingProperty(node: SimpleBindingProperty) {
        unreachable()
    }

    override fun visitComputedBindingProperty(node: ComputedBindingProperty) {
        unreachable()
    }

    override fun visitBindingRestElement(node: BindingRestElement) {
        unreachable()
    }

    override fun visitSimpleBindingElement(node: SimpleBindingElement) {
        unreachable()
    }

    override fun visitBindingElisionElement(node: BindingElisionElement) {
        unreachable()
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
            when (param) {
                is SimpleParameter -> {
                    if (param.identifier.name == "arguments")
                        hasArgumentsIdentifier = true
                    if (param.initializer != null)
                        hasParameterExpressions = true
                }
                is BindingParameter -> {
                    // TODO: Check for "arguments" name recursively
                    if (param.initializer != null)
                        hasParameterExpressions = true
                }
                is RestParameter -> {
                    hasSimpleParams = false
                }
            }

            visit(param)
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

    override fun visitSimpleParameter(node: SimpleParameter) {
        node.type = VariableType.Var
        node.mode = VariableMode.Parameter
        node.scope = scope
        scope.addVariableSource(node)
        super.visitSimpleParameter(node)
    }

    override fun visitRestParameter(node: RestParameter) {
        visitBindingDeclarationOrPattern(node.declaration, VariableMode.Parameter, VariableType.Var)
    }

    override fun visitBindingParameter(node: BindingParameter) {
        visitBindingPattern(node.pattern, VariableMode.Parameter, VariableType.Var)
    }

    override fun visitTryStatement(node: TryStatementNode) {
        val catchNode = node.catchNode
        val finallyBlock = node.finallyBlock

        visitBlock(node.tryBlock)

        if (catchNode != null) {
            val parameter = catchNode.parameter
            if (parameter != null) {
                scope = Scope(scope)
                visitBindingDeclarationOrPattern(parameter.declaration, VariableMode.Declared, VariableType.Let)
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