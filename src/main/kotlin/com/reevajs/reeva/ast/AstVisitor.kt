package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.expressions.*
import com.reevajs.reeva.ast.literals.*
import com.reevajs.reeva.ast.statements.*

interface AstVisitor {
    fun visit(node: AstNode) {
        when (node) {
            is ArgumentNode -> visitArgument(node)
            is ArrayLiteralNode -> visitArrayLiteral(node)
            is ArrowFunctionNode -> visitArrowFunction(node)
            is AssignmentExpressionNode -> visitAssignmentExpression(node)
            is AwaitExpressionNode -> visitAwaitExpression(node)
            is BigIntLiteralNode -> visitBigIntLiteral(node)
            is BinaryExpressionNode -> visitBinaryExpression(node)
            is BindingDeclaration -> visitBindingDeclaration(node)
            is BindingDeclarationOrPattern -> visitBindingDeclarationOrPattern(node)
            is BindingElisionElement -> visitBindingElisionElement(node)
            is BindingParameter -> visitBindingParameter(node)
            is BindingPatternNode -> visitBindingPattern(node)
            is BindingRestElement -> visitBindingRestElement(node)
            is BindingRestProperty -> visitBindingRestProperty(node)
            is BlockNode -> visitBlock(node)
            is BlockStatementNode -> visit(node.block)
            is BooleanLiteralNode -> visitBooleanLiteral(node)
            is BreakStatementNode -> visitBreakStatement(node)
            is CallExpressionNode -> visitCallExpression(node)
            is ClassDeclarationNode -> visitClassDeclaration(node)
            is ClassExpressionNode -> visitClassExpression(node)
            is ClassFieldNode -> visitClassField(node)
            is ClassMethodNode -> visitClassMethod(node)
            is ClassNode -> visitClass(node)
            is CommaExpressionNode -> visitCommaExpression(node)
            is ComputedBindingProperty -> visitComputedBindingProperty(node)
            is ConditionalExpressionNode -> visitConditionalExpression(node)
            is ContinueStatementNode -> visitContinueStatement(node)
            is DebuggerStatementNode -> visitDebuggerStatement()
            is DoWhileStatementNode -> visitDoWhileStatement(node)
            is EmptyStatementNode -> {}
            is Export -> visitExport(node)
            is ExportNode -> visitExportNode(node)
            is ExpressionStatementNode -> visitExpressionStatement(node)
            is ForAwaitOfNode -> visitForAwaitOf(node)
            is ForInNode -> visitForIn(node)
            is ForOfNode -> visitForOf(node)
            is ForStatementNode -> visitForStatement(node)
            is FunctionDeclarationNode -> visitFunctionDeclaration(node)
            is FunctionExpressionNode -> visitFunctionExpression(node)
            is IdentifierNode -> visitIdentifier(node)
            is IdentifierReferenceNode -> visitIdentifierReference(node)
            is IfStatementNode -> visitIfStatement(node)
            is Import -> visitImport(node)
            is ImportCallExpressionNode -> visitImportCallExpression(node)
            is ImportMetaExpressionNode -> visitImportMetaExpression()
            is ImportNode -> visitImportNode(node)
            is LexicalDeclarationNode -> visitLexicalDeclaration(node)
            is MemberExpressionNode -> visitMemberExpression(node)
            is MethodDefinitionNode -> visitMethodDefinition(node)
            is ModuleNode -> visitModule(node)
            is NewExpressionNode -> visitNewExpression(node)
            is NewTargetNode -> visitNewTargetExpression(node)
            is NullLiteralNode -> visitNullLiteral()
            is NumericLiteralNode -> visitNumericLiteral(node)
            is ObjectLiteralNode -> visitObjectLiteral(node)
            is OptionalChainNode -> visitOptionalChain(node)
            is ParameterList -> visitParameterList(node)
            is ParenthesizedExpressionNode -> visitParenthesizedExpression(node)
            is PropertyName -> visitPropertyName(node)
            is RegExpLiteralNode -> visitRegExpLiteral(node)
            is RestParameter -> visitRestParameter(node)
            is ReturnStatementNode -> visitReturnStatement(node)
            is ScriptNode -> visitScript(node)
            is SimpleBindingElement -> visitSimpleBindingElement(node)
            is SimpleBindingProperty -> visitSimpleBindingProperty(node)
            is SimpleParameter -> visitSimpleParameter(node)
            is StringLiteralNode -> visitStringLiteral(node)
            is SuperCallExpressionNode -> visitSuperCallExpression(node)
            is SuperPropertyExpressionNode -> visitSuperPropertyExpression(node)
            is SwitchStatementNode -> visitSwitchStatement(node)
            is TemplateLiteralNode -> visitTemplateLiteral(node)
            is ThisLiteralNode -> visitThisLiteral(node)
            is ThrowStatementNode -> visitThrowStatement(node)
            is TryStatementNode -> visitTryStatement(node)
            is UnaryExpressionNode -> visitUnaryExpression(node)
            is UpdateExpressionNode -> visitUpdateExpression(node)
            is VariableDeclarationNode -> visitVariableDeclaration(node)
            is WhileStatementNode -> visitWhileStatement(node)
            is YieldExpressionNode -> visitYieldExpression(node)
            else -> throw IllegalArgumentException("Unrecognized AstNode ${node.astNodeName}")
        }
    }

    fun visitScript(node: ScriptNode) {
        node.statements.forEach(::visit)
    }

    fun visitModule(node: ModuleNode) {
        node.body.forEach(::visit)
    }

    fun visitBlock(node: BlockNode) {
        node.statements.forEach(::visit)
    }

    fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)
        visit(node.trueBlock)
        if (node.falseBlock != null)
            visit(node.falseBlock)
    }

    fun visitDoWhileStatement(node: DoWhileStatementNode) {
        visit(node.condition)
        visit(node.body)
    }

    fun visitWhileStatement(node: WhileStatementNode) {
        visit(node.condition)
        visit(node.body)
    }

    fun visitForStatement(node: ForStatementNode) {
        node.initializer?.also(::visit)
        node.condition?.also(::visit)
        node.incrementer?.also(::visit)
        visit(node.body)
    }

    fun visitSwitchStatement(node: SwitchStatementNode) {
        visit(node.target)
        node.clauses.forEach {
            it.target?.let(::visit)
            it.body?.forEach(::visit)
        }
    }

    fun visitForIn(node: ForInNode) {
        visit(node.decl)
        visit(node.expression)
        visit(node.body)
    }

    fun visitForOf(node: ForOfNode) {
        visit(node.decl)
        visit(node.expression)
        visit(node.body)
    }

    fun visitForAwaitOf(node: ForAwaitOfNode) {
        visit(node.decl)
        visit(node.expression)
        visit(node.body)
    }

    fun visitThrowStatement(node: ThrowStatementNode) {
        visit(node.expr)
    }

    fun visitTryStatement(node: TryStatementNode) {
        visit(node.tryBlock)
        node.catchNode?.also {
            it.parameter?.declaration?.let(::visit)
            visit(it.block)
        }
        node.finallyBlock?.also(::visit)
    }

    fun visitBreakStatement(node: BreakStatementNode) {}

    fun visitContinueStatement(node: ContinueStatementNode) {}

    fun visitReturnStatement(node: ReturnStatementNode) {
        node.expression?.also(::visit)
    }

    fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    fun visitVariableDeclaration(node: VariableDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    private fun visitDeclaration(declaration: Declaration) {
        when (declaration) {
            is NamedDeclaration -> visitNamedDeclaration(declaration)
            is DestructuringDeclaration -> visitDestructuringDeclaration(declaration)
        }
    }

    fun visitNamedDeclaration(declaration: NamedDeclaration) {
        visit(declaration.identifier)
        declaration.initializer?.let(::visit)
    }

    fun visitDestructuringDeclaration(declaration: DestructuringDeclaration) {
        visit(declaration.pattern)
        declaration.initializer?.let(::visit)
    }

    fun visitBindingPattern(node: BindingPatternNode) {
        when (node.kind) {
            BindingKind.Object -> node.bindingProperties.forEach(::visit)
            BindingKind.Array -> node.bindingElements.forEach(::visit)
        }
    }

    fun visitBindingDeclaration(node: BindingDeclaration) {
        visit(node.identifier)
    }

    fun visitBindingDeclarationOrPattern(node: BindingDeclarationOrPattern) {
        visit(node.node)
    }

    fun visitBindingRestProperty(node: BindingRestProperty) {
        visit(node.declaration)
    }

    fun visitSimpleBindingProperty(node: SimpleBindingProperty) {
        visit(node.declaration)
        if (node.alias != null)
            visit(node.alias)
        if (node.initializer != null)
            visit(node.initializer)
    }

    fun visitComputedBindingProperty(node: ComputedBindingProperty) {
        visit(node.name)
        visit(node.alias)
        if (node.initializer != null)
            visit(node.initializer)
    }

    fun visitBindingRestElement(node: BindingRestElement) {
        visit(node.declaration)
    }

    fun visitSimpleBindingElement(node: SimpleBindingElement) {
        visit(node.alias)
        if (node.initializer != null)
            visit(node.initializer)
    }

    fun visitBindingElisionElement(node: BindingElisionElement) {}

    fun visitDebuggerStatement() {}

    fun visitParameterList(node: ParameterList) {
        node.parameters.forEach(::visit)
    }

    fun visitImportNode(node: ImportNode) {
        node.imports.forEach(::visit)
    }

    fun visitImport(import: Import) {
        when (import) {
            is Import.Default -> visit(import.identifier)
            is Import.Named -> {
                visit(import.importIdent)
                if (import.importIdent != import.localIdent)
                    visit(import.localIdent)
            }
            is Import.Namespace -> visit(import.identifier)
        }
    }

    fun visitExportNode(node: ExportNode) {
        node.exports.forEach(::visit)
    }

    fun visitExport(export: Export) {
        when (export) {
            is Export.Expr -> visit(export.expr)
            is Export.Named -> {
                visit(export.exportIdent)
                if (export.exportIdent != export.localIdent)
                    visit(export.localIdent)
            }
            is Export.Namespace -> export.alias?.let(::visit)
            is Export.Node -> visit(export.node)
        }
    }

    fun visitArgument(node: ArgumentNode) {
        visit(node.expression)
    }

    fun visitIdentifierReference(node: IdentifierReferenceNode) {}

    // Should only ever happen in CPEAAPL nodes
    fun visitIdentifier(node: IdentifierNode) {}

    fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        visit(node.parameters)
        visit(node.body)
    }

    fun visitSimpleParameter(node: SimpleParameter) {
        visit(node.identifier)
        node.initializer?.let(::visit)
    }

    fun visitRestParameter(node: RestParameter) {
        visit(node.declaration)
    }

    fun visitBindingParameter(node: BindingParameter) {
        visit(node.pattern)
        node.initializer?.let(::visit)
    }

    fun visitPropertyName(node: PropertyName) {
        when (node.type) {
            PropertyName.Type.Identifier -> (node.expression as IdentifierNode).processedName
            PropertyName.Type.Computed -> visit(node.expression)
            else -> {
            }
        }
    }

    fun visitMethodDefinition(node: MethodDefinitionNode) {
        visit(node.parameters)
        visit(node.body)
    }

    fun visitFunctionExpression(node: FunctionExpressionNode) {
        visit(node.parameters)
        visit(node.body)
    }

    fun visitArrowFunction(node: ArrowFunctionNode) {
        visit(node.parameters)
        visit(node.body)
    }

    fun visitClassDeclaration(node: ClassDeclarationNode) {
        node.identifier?.let(::visit)
        visit(node.classNode)
    }

    fun visitClassExpression(node: ClassExpressionNode) {
        node.identifier?.let(::visit)
        visit(node.classNode)
    }

    fun visitClass(node: ClassNode) {
        node.heritage?.let(::visit)
        for (element in node.body)
            visit(element)
    }

    fun visitClassField(node: ClassFieldNode) {
        visit(node.identifier)
        node.initializer?.let(::visit)
    }

    fun visitClassMethod(node: ClassMethodNode) {
        visit(node.method)
    }

    fun visitBinaryExpression(node: BinaryExpressionNode) {
        visit(node.lhs)
        visit(node.rhs)
    }

    fun visitUnaryExpression(node: UnaryExpressionNode) {
        visit(node.expression)
    }

    fun visitUpdateExpression(node: UpdateExpressionNode) {
        visit(node.target)
    }

    fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        visit(node.lhs)
        visit(node.rhs)
    }

    fun visitAwaitExpression(node: AwaitExpressionNode) {
        visit(node.expression)
    }

    fun visitCallExpression(node: CallExpressionNode) {
        visit(node.target)
        node.arguments.forEach { visit(it.expression) }
    }

    fun visitCommaExpression(node: CommaExpressionNode) {
        node.expressions.forEach(::visit)
    }

    fun visitConditionalExpression(node: ConditionalExpressionNode) {
        visit(node.predicate)
        visit(node.ifTrue)
        visit(node.ifFalse)
    }

    fun visitMemberExpression(node: MemberExpressionNode) {
        visit(node.lhs)
        visit(node.rhs)
    }

    fun visitNewExpression(node: NewExpressionNode) {
        visit(node.target)
        node.arguments.forEach { visit(it.expression) }
    }

    fun visitOptionalChain(node: OptionalChainNode) {
        visit(node.base)
        for (part in node.parts) {
            when (part) {
                is OptionalAccessChain -> visit(part.identifier)
                is OptionalCallChain -> part.arguments.forEach(::visit)
                is OptionalComputedAccessChain -> visit(part.expr)
            }
        }
    }

    fun visitSuperPropertyExpression(node: SuperPropertyExpressionNode) {
        visit(node.target)
    }

    fun visitSuperCallExpression(node: SuperCallExpressionNode) {
        node.arguments.forEach { visit(it.expression) }
    }

    fun visitImportCallExpression(node: ImportCallExpressionNode) {
        visit(node.expression)
    }

    fun visitYieldExpression(node: YieldExpressionNode) {
        node.expression?.also(::visit)
    }

    fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        visit(node.expression)
    }

    fun visitTemplateLiteral(node: TemplateLiteralNode) {
        node.parts.forEach(::visit)
    }

    fun visitRegExpLiteral(node: RegExpLiteralNode) {}

    fun visitImportMetaExpression() {}

    fun visitNewTargetExpression(node: NewTargetNode) {}

    fun visitArrayLiteral(node: ArrayLiteralNode) {
        node.elements.forEach {
            it.expression?.also(::visit)
        }
    }

    fun visitObjectLiteral(node: ObjectLiteralNode) {
        node.list.forEach {
            when (it) {
                is KeyValueProperty -> {
                    visit(it.key.expression)
                    visit(it.value)
                }
                is MethodProperty -> visit(it.method)
                is ShorthandProperty -> visit(it.key)
                is SpreadProperty -> visit(it.target)
            }
        }
    }

    fun visitBooleanLiteral(node: BooleanLiteralNode) {}

    fun visitStringLiteral(node: StringLiteralNode) {}

    fun visitNumericLiteral(node: NumericLiteralNode) {}

    fun visitBigIntLiteral(node: BigIntLiteralNode) {}

    fun visitNullLiteral() {}

    fun visitThisLiteral(node: ThisLiteralNode) {}
}
