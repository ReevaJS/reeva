package me.mattco.reeva.ir

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*

/*

let x = 10;
arr.map(e => e.x);
if (x) {
    console.log('hi');
}

0: LdaImm #10
1: JumpIfFalse 3
2: <console.log('hi')>
3: return

 */

class FunctionInfo(
    val name: String?,
    val code: Array<Opcode>,
    val registerCount: Int, // includes argCount
    val argCount: Int,
    val isTopLevelScript: Boolean = false,
)

class IRTransformer : ASTVisitor {
    private lateinit var opcodeBuilder: OpcodeBuilder
    private val functionInfo = mutableListOf<FunctionInfo>()

    fun transform(node: ScriptNode): List<FunctionInfo> {
        if (::opcodeBuilder.isInitialized)
            throw IllegalStateException("Cannot re-use an IRTransformer")

        opcodeBuilder = OpcodeBuilder()

        val opcodes = opcodeBuilder.build()
        functionInfo.add(0, FunctionInfo(null, opcodes, 0, 0, true))
        return functionInfo
    }

    override fun visitBlock(node: BlockNode) {
        super.visitBlock(node)
    }

    override fun visitExpressionStatement(node: ExpressionStatementNode) {
        super.visitExpressionStatement(node)
    }

    override fun visitIfStatement(node: IfStatementNode) {
        super.visitIfStatement(node)
    }

    override fun visitDoWhileStatement(node: DoWhileStatementNode) {
        super.visitDoWhileStatement(node)
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        super.visitWhileStatement(node)
    }

    override fun visitForStatement(node: ForStatementNode) {
        super.visitForStatement(node)
    }

    override fun visitForIn(node: ForInNode) {
        super.visitForIn(node)
    }

    override fun visitForOf(node: ForOfNode) {
        super.visitForOf(node)
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        super.visitForAwaitOf(node)
    }

    override fun visitLabelledStatement(node: LabelledStatementNode) {
        super.visitLabelledStatement(node)
    }

    override fun visitThrowStatement(node: ThrowStatementNode) {
        super.visitThrowStatement(node)
    }

    override fun visitTryStatement(node: TryStatementNode) {
        super.visitTryStatement(node)
    }

    override fun visitBreakStatement(node: BreakStatementNode) {
        super.visitBreakStatement(node)
    }

    override fun visitReturnStatement(node: ReturnStatementNode) {
        super.visitReturnStatement(node)
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        super.visitLexicalDeclaration(node)
    }

    override fun visitVariableStatement(node: VariableStatementNode) {
        super.visitVariableStatement(node)
    }

    override fun visitDebuggerStatement() {
        super.visitDebuggerStatement()
    }

    override fun visitImportDeclaration(node: ImportDeclarationNode) {
        super.visitImportDeclaration(node)
    }

    override fun visitExportDeclaration(node: ExportDeclarationNode) {
        super.visitExportDeclaration(node)
    }

    override fun visitBindingIdentifier(node: BindingIdentifierNode) {
        super.visitBindingIdentifier(node)
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        super.visitIdentifierReference(node)
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        super.visitFunctionDeclaration(node)
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        super.visitFunctionExpression(node)
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        super.visitArrowFunction(node)
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        super.visitClassDeclaration(node)
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        super.visitClassExpression(node)
    }

    override fun visitAdditiveExpression(node: AdditiveExpressionNode) {
        super.visitAdditiveExpression(node)
    }

    override fun visitBitwiseANDExpression(node: BitwiseANDExpressionNode) {
        super.visitBitwiseANDExpression(node)
    }

    override fun visitBitwiseORExpression(node: BitwiseORExpressionNode) {
        super.visitBitwiseORExpression(node)
    }

    override fun visitBitwiseXORExpression(node: BitwiseXORExpressionNode) {
        super.visitBitwiseXORExpression(node)
    }

    override fun visitCoalesceExpression(node: CoalesceExpressionNode) {
        super.visitCoalesceExpression(node)
    }

    override fun visitEqualityExpression(node: EqualityExpressionNode) {
        super.visitEqualityExpression(node)
    }

    override fun visitExponentiationExpression(node: ExponentiationExpressionNode) {
        super.visitExponentiationExpression(node)
    }

    override fun visitLogicalANDExpression(node: LogicalANDExpressionNode) {
        super.visitLogicalANDExpression(node)
    }

    override fun visitLogicalORExpression(node: LogicalORExpressionNode) {
        super.visitLogicalORExpression(node)
    }

    override fun visitMultiplicativeExpression(node: MultiplicativeExpressionNode) {
        super.visitMultiplicativeExpression(node)
    }

    override fun visitRelationalExpression(node: RelationalExpressionNode) {
        super.visitRelationalExpression(node)
    }

    override fun visitShiftExpression(node: ShiftExpressionNode) {
        super.visitShiftExpression(node)
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        super.visitUnaryExpression(node)
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        super.visitUpdateExpression(node)
    }

    override fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        super.visitAssignmentExpression(node)
    }

    override fun visitAwaitExpression(node: AwaitExpressionNode) {
        super.visitAwaitExpression(node)
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        super.visitCallExpression(node)
    }

    override fun visitCommaExpression(node: CommaExpressionNode) {
        super.visitCommaExpression(node)
    }

    override fun visitConditionalExpression(node: ConditionalExpressionNode) {
        super.visitConditionalExpression(node)
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        super.visitMemberExpression(node)
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        super.visitNewExpression(node)
    }

    override fun visitOptionalExpression(node: OptionalExpressionNode) {
        super.visitOptionalExpression(node)
    }

    override fun visitSuperPropertyExpression(node: SuperPropertyExpressionNode) {
        super.visitSuperPropertyExpression(node)
    }

    override fun visitSuperCallExpression(node: SuperCallExpressionNode) {
        super.visitSuperCallExpression(node)
    }

    override fun visitImportCallExpression(node: ImportCallExpressionNode) {
        super.visitImportCallExpression(node)
    }

    override fun visitYieldExpression(node: YieldExpressionNode) {
        super.visitYieldExpression(node)
    }

    override fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        super.visitParenthesizedExpression(node)
    }

    override fun visitTemplateLiteral(node: TemplateLiteralNode) {
        super.visitTemplateLiteral(node)
    }

    override fun visitRegExpLiteral(node: RegExpLiteralNode) {
        super.visitRegExpLiteral(node)
    }

    override fun visitImportMetaExpression() {
        super.visitImportMetaExpression()
    }

    override fun visitNewTargetExpression() {
        super.visitNewTargetExpression()
    }

    override fun visitArrayLiteral(node: ArrayLiteralNode) {
        super.visitArrayLiteral(node)
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        super.visitObjectLiteral(node)
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        super.visitBooleanLiteral(node)
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        super.visitStringLiteral(node)
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        super.visitNumericLiteral(node)
    }

    override fun visitBigIntLiteral(node: BigIntLiteralNode) {
        super.visitBigIntLiteral(node)
    }

    override fun visitNullLiteral() {
        super.visitNullLiteral()
    }

    override fun visitThisLiteral() {
        super.visitThisLiteral()
    }
}
