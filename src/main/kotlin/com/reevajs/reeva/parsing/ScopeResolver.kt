package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.CallExpressionNode
import com.reevajs.reeva.ast.expressions.NewTargetNode
import com.reevajs.reeva.ast.expressions.UnaryExpressionNode
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.literals.ThisLiteralNode
import com.reevajs.reeva.ast.statements.*
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

class ScopeResolver : DefaultAstVisitor() {
    private lateinit var scope: Scope

    fun resolve(node: NodeWithScope) {
        val globalScope = GlobalScope()
        scope = globalScope

        when (node) {
            is ScriptNode -> {
                globalScope.isIntrinsicallyStrict = node.hasUseStrict
                node.statements.forEach { it.accept(this) }
            }
            is FunctionDeclarationNode -> {
                node.scope = scope
                node.accept(this)
            }
            is ModuleNode -> {
                globalScope.isIntrinsicallyStrict = true
                scope = ModuleScope(globalScope)
                node.body.forEach { it.accept(this) }
            }
        }

        node.scope = scope

        globalScope.finish()
    }

    override fun visit(node: BlockNode) {
        visitBlock(node, pushScope = true)
    }

    private fun visitBlock(node: BlockNode, pushScope: Boolean) {
        if (pushScope)
            scope = BlockScope(scope)

        node.scope = scope
        node.statements.forEach { it.accept(this) }

        if (pushScope)
            scope = scope.outer!!
    }

    override fun visit(node: Import) {
        scope.addVariableSource(node)
        node.type = VariableType.Let
        node.mode = VariableMode.Import

        super.visit(node)
    }

    override fun visit(node: VariableDeclarationNode) {
        for (decl in node.declarations) {
            val mode = if (scope.outerHoistingScope is GlobalScope) {
                VariableMode.Global
            } else VariableMode.Declared

            visitDeclaration(decl, mode, VariableType.Var)
        }
    }

    override fun visit(node: LexicalDeclarationNode) {
        val type = if (node.isConst) VariableType.Const else VariableType.Let

        for (decl in node.declarations)
            visitDeclaration(decl, VariableMode.Declared, type)
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

        declaration.initializer?.accept(this)
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
                node.initializer?.accept(this)
            }
            is ComputedBindingProperty -> {
                node.name.accept(this)
                visitBindingDeclarationOrPattern(node.alias, mode, type)
                node.initializer?.accept(this)
            }
        }
    }

    private fun visitBindingElement(node: BindingElement, mode: VariableMode, type: VariableType) {
        when (node) {
            is BindingElisionElement -> { /* no-op */ }
            is BindingRestElement -> visitBindingDeclarationOrPattern(node.declaration, mode, type)
            is SimpleBindingElement -> {
                visitBindingDeclarationOrPattern(node.alias, mode, type)
                node.initializer?.accept(this)
            }
        }
    }

    private fun visitBindingDeclarationOrPattern(
        node: BindingDeclarationOrPattern,
        mode: VariableMode,
        type: VariableType
    ) {
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

    override fun visit(node: BindingDeclaration) {
        unreachable()
    }

    override fun visit(node: BindingDeclarationOrPattern) {
        unreachable()
    }

    override fun visit(node: BindingRestProperty) {
        unreachable()
    }

    override fun visit(node: SimpleBindingProperty) {
        unreachable()
    }

    override fun visit(node: ComputedBindingProperty) {
        unreachable()
    }

    override fun visit(node: BindingRestElement) {
        unreachable()
    }

    override fun visit(node: SimpleBindingElement) {
        unreachable()
    }

    override fun visit(node: BindingElisionElement) {
        unreachable()
    }

    override fun visit(node: IdentifierReferenceNode) {
        scope.addVariableReference(node)
    }

    override fun visit(node: FunctionDeclarationNode) {
        val declaredScope = scope
        val hoistedScope = scope.outerHoistingScope

        node.type = VariableType.Var
        node.mode = if (hoistedScope is GlobalScope) {
            VariableMode.Global
        } else VariableMode.Declared

        declaredScope.addVariableSource(node)
        hoistedScope.addHoistedVariable(node)

        node.functionScope = visitFunctionHelper(node.parameters, node.body, node.kind, isLexical = false)
    }

    override fun visit(node: FunctionExpressionNode) {
        if (node.identifier != null) {
            node.type = VariableType.Var
            node.mode = VariableMode.Declared

            scope.addVariableSource(node)
        }

        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body, node.kind, isLexical = false)
    }

    override fun visit(node: ArrowFunctionNode) {
        node.scope = scope
        node.functionScope = visitFunctionHelper(node.parameters, node.body, node.kind, isLexical = true)
    }

    override fun visit(node: MethodDefinitionNode) {
        node.scope = scope
        node.propName.accept(this)
        node.functionScope = visitFunctionHelper(
            node.parameters,
            node.body,
            node.methodKind.toFunctionKind(),
            isLexical = false,
        )
    }

    override fun visit(node: ClassDeclarationNode) {
        expect(node.identifier != null)
        node.mode = VariableMode.Declared
        node.type = VariableType.Const
        scope.addVariableSource(node)

        visitClassHelper(node.classNode)
    }

    override fun visit(node: ClassExpressionNode) {
        visitClassHelper(node.classNode)
    }

    private fun visitClassHelper(node: ClassNode) {
        if (node.heritage != null)
            node.heritage.accept(this)

        val classScope = FunctionScope(scope)
        scope = classScope
        classScope.isIntrinsicallyStrict = true
        node.scope = classScope

        for (element in node.body) {
            if (element is ClassMethodNode) {
                element.method.accept(this)
                if (element.isConstructor())
                    (element.method.functionScope as HoistingScope).isDerivedClassConstructor = true
            } else {
                expect(element is ClassFieldNode)
                element.identifier.accept(this)
                if (element.initializer != null)
                    element.initializer.accept(this)
            }
        }

        scope = scope.outer!!
    }

    private fun visitFunctionHelper(
        parameters: ParameterList,
        body: AstNode,
        kind: AOs.FunctionKind,
        isLexical: Boolean,
    ): Scope {
        val functionScope = FunctionScope(scope, isLexical)
        scope = functionScope

        if (body is BlockNode && body.hasUseStrict)
            functionScope.isIntrinsicallyStrict = true

        var hasParameterExpressions = false
        var hasSimpleParams = true
        var hasArgumentsIdentifier = false

        for (param in parameters.parameters) {
            when (param) {
                is SimpleParameter -> {
                    if (param.identifier.processedName == "arguments")
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

            param.accept(this)
        }

        val bodyScope = if (body is BlockNode && !parameters.isSimple()) {
            // The body scope shouldn't be a target for the receiver or new.target
            // sources
            FunctionScope(scope, isLexical = true).also {
                scope = it
            }
        } else functionScope

        if (body is BlockNode) {
            body.scope = bodyScope
            visitBlock(body, pushScope = false)
        } else {
            body.accept(this)
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

    override fun visit(node: SimpleParameter) {
        node.type = VariableType.Var
        node.mode = VariableMode.Parameter
        node.scope = scope
        scope.addVariableSource(node)
        super.visit(node)
    }

    override fun visit(node: RestParameter) {
        visitBindingDeclarationOrPattern(node.declaration, VariableMode.Parameter, VariableType.Var)
    }

    override fun visit(node: BindingParameter) {
        visitBindingPattern(node.pattern, VariableMode.Parameter, VariableType.Var)
    }

    override fun visit(node: CallExpressionNode) {
        if (node.target is IdentifierReferenceNode && node.target.rawName == "eval")
            scope.markEvalScope()

        super.visit(node)
    }

    override fun visit(node: TryStatementNode) {
        node.tryBlock.accept(this)

        val catchNode = node.catchNode
        if (catchNode != null) {
            val parameter = catchNode.parameter
            if (parameter != null) {
                scope = BlockScope(scope)
                visitBindingDeclarationOrPattern(parameter.declaration, VariableMode.Declared, VariableType.Let)
                visitBlock(catchNode.block, pushScope = false)
                scope = scope.outer!!
            } else {
                catchNode.block.accept(this)
            }
        }

        node.finallyBlock?.accept(this)
    }

    override fun visit(node: ForStatementNode) {
        val initializer = node.initializer
        val needInitScope = initializer is VariableDeclarationNode || initializer is LexicalDeclarationNode

        if (needInitScope) {
            scope = BlockScope(scope)
            node.initializerScope = scope
        }

        if (initializer != null)
            initializer.accept(this)

        if (node.condition != null)
            node.condition.accept(this)

        if (node.incrementer != null)
            node.incrementer.accept(this)

        node.body.accept(this)

        if (needInitScope)
            scope = scope.outer!!
    }

    override fun visit(node: ForInNode) {
        visitForEach(node)
    }

    override fun visit(node: ForOfNode) {
        visitForEach(node)
    }

    override fun visit(node: ForAwaitOfNode) {
        visitForEach(node)
    }

    private fun visitForEach(node: ForEachNode) {
        node.expression.accept(this)

        val decl = node.decl
        val body = node.body

        val needsDeclScope = decl is VariableDeclarationNode || decl is LexicalDeclarationNode || body is BlockNode
        if (needsDeclScope) {
            scope = BlockScope(scope)
            node.initializerScope = scope
        }

        decl.accept(this)
        body.accept(this)

        if (needsDeclScope)
            scope = scope.outer!!
    }

    override fun visit(node: UnaryExpressionNode) {
        node.scope = scope
        super.visit(node)
    }

    override fun visit(node: ThisLiteralNode) {
        node.scope = scope
        scope.outerHoistingScope.addReceiverReference(node)
    }

    override fun visit(node: NewTargetNode) {
        node.scope = scope
    }
}
