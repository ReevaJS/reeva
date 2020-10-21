package me.mattco.renva.compiler

import codes.som.anthony.koffee.MethodAssembly
import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.*
import codes.som.anthony.koffee.modifiers.public
import codes.som.anthony.koffee.types.TypeLike
import me.mattco.renva.ast.*
import me.mattco.renva.ast.expressions.*
import me.mattco.renva.ast.LiteralNode
import me.mattco.renva.ast.literals.*
import me.mattco.renva.ast.statements.*
import me.mattco.renva.parser.Parser
import me.mattco.renva.runtime.Agent
import me.mattco.renva.runtime.Operations
import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.annotations.ECMAImpl
import me.mattco.renva.runtime.contexts.ExecutionContext
import me.mattco.renva.runtime.environment.DeclarativeEnvRecord
import me.mattco.renva.runtime.environment.EnvRecord
import me.mattco.renva.runtime.values.JSReference
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.functions.JSFunction
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.primitives.*
import me.mattco.renva.utils.expect
import me.mattco.renva.utils.unreachable
import org.objectweb.asm.tree.ClassNode

class Compiler(private val scriptNode: ScriptNode, fileName: String) {
    private val sanitizedFileName = fileName.replace(Regex("[^\\w]"), "_")

    val className = "TopLevel_$sanitizedFileName$classCounter"

    fun compile(): ClassNode {
        return assembleClass(public, className, superName = "me/mattco/renva/compiler/TopLevelScript") {
            method(public, "<init>", void) {
                aload_0
                invokespecial(TopLevelScript::class, "<init>", void)
                _return
            }

            method(public, "run", void, ExecutionContext::class) {
                compileScript(scriptNode)
                _return
            }
        }
    }

    private fun MethodAssembly.compileScript(script: ScriptNode) {
        compileStatementList(script.statementList)
    }

    private fun MethodAssembly.compileStatementList(statementList: StatementListNode) {
        statementList.statements.forEach {
            compileStatement(it as StatementNode)
        }
    }

    private fun MethodAssembly.compileStatement(statement: StatementNode) {
        when (statement) {
            is BlockStatementNode -> compileBlockStatement(statement)
            is VariableStatementNode -> compileVariableStatement(statement)
            is EmptyStatementNode -> {}
            is ExpressionStatementNode -> compileExpressionStatement(statement)
            is IfStatementNode -> compileIfStatement(statement)
            is BreakableStatement -> compileBreakableStatement(statement)
            is LabelledStatement -> compileLabelledStatement(statement)
            is LexicalDeclarationNode -> compileLexicalDeclaration(statement)
            else -> TODO()
        }
    }

    private fun MethodAssembly.compileBlock(block: BlockNode) {
        // TODO: Scopes
        if (block.statements != null) {
            pushContext
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            block.statements.lexicallyScopedDeclarations().forEach { decl ->
                decl.boundNames().forEach { name ->
                    dup
                    ldc(name)
                    if (decl.isConstantDeclaration()) {
                        ldc(true)
                        invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                    } else {
                        ldc(false)
                        invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                    }
                }
                // TODO: FunctionDeclaration check
            }
            compileStatementList(block.statements)
        }
    }

    private fun MethodAssembly.compileBlockStatement(blockStatement: BlockStatementNode) {
        compileBlock(blockStatement.block)
    }

    private fun MethodAssembly.compileVariableStatement(variableStatement: VariableStatementNode) {
        compileVariableDeclarationList(variableStatement.declarations)
    }

    private fun MethodAssembly.compileVariableDeclarationList(list: VariableDeclarationList) {
        list.declarations.forEach {
            compileVariableDeclarationNode(it)
        }
    }

    private fun MethodAssembly.compileVariableDeclarationNode(node: VariableDeclarationNode) {
        if (node.initializer != null) {
            val id = node.identifier.identifierName
            ldc(id)
            aconst_null
            operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
            if (isAnonymousFunctionDefinition(node)) {
                TODO()
            } else {
                compileInitializer(node.initializer)
                operation("getValue", JSValue::class, JSValue::class)
            }
            operation("putValue", void, JSValue::class, JSValue::class)
        } else {
            // Do nothing, this is handled by SS
        }
    }

    @ECMAImpl("IsAnonymousFunctionDefinition", "14.1.12")
    private fun isAnonymousFunctionDefinition(node: ASTNode): Boolean {
        if (!node.isFunctionDefinition())
            return false
        return !node.hasName()
    }

    private fun MethodAssembly.compileInitializer(initializerNode: InitializerNode) {
        compileExpression(initializerNode.node)
    }

    private fun MethodAssembly.compileEmptyStatement(emptyStatement: EmptyStatementNode) { }

    private fun MethodAssembly.compileIfStatement(ifStatement: IfStatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileBreakableStatement(breakableStatement: BreakableStatement) {
        TODO()
    }

    private fun MethodAssembly.compileDoWhileStatement(doWhileStatement: DoWhileStatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileWhileStatement(whileStatement: WhileStatementNode) {
        TODO()
    }

    private fun MethodAssembly.compileLabelledStatement(labelledStatement: LabelledStatement) {
        TODO()
    }

    private fun MethodAssembly.compileHoistableDeclaration(hoistableDeclarationNode: HoistableDeclarationNode) {
        TODO()
    }

    private fun MethodAssembly.compileLexicalDeclaration(lexicalDeclarationNode: LexicalDeclarationNode) {
        lexicalDeclarationNode.bindingList.lexicalBindings.forEach {
            expect(it.initializer != null)

            ldc(it.identifier.identifierName)
            aconst_null
            operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
            if (isAnonymousFunctionDefinition(lexicalDeclarationNode)) {
                TODO()
            } else {
                compileExpression(it.initializer.node)
                operation("getValue", JSValue::class, JSValue::class)
            }
            operation("initializeReferencedBinding", void, JSReference::class, JSValue::class)
        }
    }

    private fun MethodAssembly.compileBindingList(bindingList: BindingListNode) {
        TODO()
    }

    private fun MethodAssembly.compileLexicalBinding(lexicalBindingNode: LexicalBindingNode) {
        TODO()
    }

    private fun MethodAssembly.compileExpressionStatement(expressionStatementNode: ExpressionStatementNode) {
        compileExpression(expressionStatementNode.node)
        operation("getValue", JSValue::class, JSValue::class)
        pop
    }

    private fun MethodAssembly.compileExpression(expressionNode: ExpressionNode) {
        when (expressionNode) {
            ThisNode -> compileThis()
            is CommaExpressionNode -> compileCommaExpressionNode(expressionNode)
            is IdentifierReferenceNode -> compileIdentifierReference(expressionNode)
            is LiteralNode -> compileLiteral(expressionNode)
            is CPEAAPLNode -> compileCPEAAPL(expressionNode)
            is NewExpressionNode -> compileNewExpression(expressionNode)
            is CallExpressionNode -> compileCallExpression(expressionNode)
            is MemberExpressionNode -> compileMemberExpression(expressionNode)
            is OptionalExpressionNode -> compileOptionalExpression(expressionNode)
            is AssignmentExpressionNode -> compileAssignmentExpression(expressionNode)
            is ConditionalExpressionNode -> compileConditionalExpression(expressionNode)
            is CoalesceExpressionNode -> compileCoalesceExpression(expressionNode)
            is LogicalORExpressionNode -> compileLogicalORExpression(expressionNode)
            is LogicalANDExpressionNode -> compileLogicalANDExpression(expressionNode)
            is BitwiseORExpressionNode -> compileBitwiseORExpression(expressionNode)
            is BitwiseXORExpressionNode -> compileBitwiseXORExpression(expressionNode)
            is BitwiseANDExpressionNode -> compileBitwiseANDExpression(expressionNode)
            is EqualityExpressionNode -> compileEqualityExpression(expressionNode)
            is RelationalExpressionNode -> compileRelationalExpression(expressionNode)
            is ShiftExpressionNode -> compileShiftExpression(expressionNode)
            is AdditiveExpressionNode -> compileAdditiveExpression(expressionNode)
            is MultiplicativeExpressionNode -> compileMultiplicationExpression(expressionNode)
            is ExponentiationExpressionNode -> compileExponentiationExpression(expressionNode)
            is UnaryExpressionNode -> compileUnaryExpression(expressionNode)
            is UpdateExpressionNode -> compileUpdateExpression(expressionNode)
            else -> unreachable()
        }
    }

    private fun MethodAssembly.compileCommaExpressionNode(commaExpressionNode: CommaExpressionNode) {
        commaExpressionNode.expressions.forEachIndexed { index, node ->
            compileExpression(node)
            if (index != commaExpressionNode.expressions.lastIndex)
                dup
        }
    }

    private fun MethodAssembly.compileBindingIdentifier(bindingIdentifierNode: BindingIdentifierNode) {
        TODO()
    }

    private fun MethodAssembly.compileLabelIdentifier(labelIdentifierNode: LabelIdentifierNode) {
        TODO()
    }

    private fun MethodAssembly.compileIdentifier(identifierNode: IdentifierNode) {
        TODO()
    }

    private fun MethodAssembly.compileCPEAAPL(cpeaaplNode: CPEAAPLNode) {
        when (cpeaaplNode.context) {
            Parser.CPEAAPLContext.PrimaryExpression -> {
                // Parenthesized expression
                compileExpression(cpeaaplNode.node)
            }
        }
    }

    private fun MethodAssembly.compileParenthesizedExpression(parenthesizedExpressionNode: ParenthesizedExpressionNode) {
        compileExpression(parenthesizedExpressionNode.target)
    }

    private fun MethodAssembly.compileLiteral(literalNode: LiteralNode) {
        when (literalNode) {
            is NullNode -> pushNull
            is TrueNode -> pushTrue
            is FalseNode -> pushFalse
            is StringLiteralNode -> {
                construct(JSString::class, String::class) {
                    ldc(literalNode.value)
                }
            }
            is NumericLiteralNode -> {
                construct(JSNumber::class, Double::class) {
                    ldc(literalNode.value)
                }
            }
            else -> unreachable()
        }
    }

    private fun MethodAssembly.compileMemberExpression(memberExpressionNode: MemberExpressionNode) {
        compileExpression(memberExpressionNode.lhs)
        operation("getValue", JSValue::class, JSValue::class)
        // TODO: Strict mode
        ldc(false)
        when (memberExpressionNode.type) {
            MemberExpressionNode.Type.NonComputed -> {
                expect(memberExpressionNode.rhs is IdentifierNode)
                ldc(memberExpressionNode.rhs.identifierName)
                swap
                operation(
                    "evaluatePropertyAccessWithIdentifierKey",
                    JSValue::class,
                    JSValue::class,
                    String::class,
                    Boolean::class
                )
            }
            MemberExpressionNode.Type.Computed -> {
                compileExpression(memberExpressionNode.rhs)
                swap
                operation(
                    "evaluatePropertyAccessWithExpressionKey",
                    JSValue::class,
                    JSValue::class,
                    JSValue::class,
                    Boolean::class
                )
            }
            else -> TODO()
        }

    }

    private fun MethodAssembly.compileSuperProperty(superPropertyNode: SuperPropertyNode) {
        TODO()
    }

    private fun MethodAssembly.compileMetaProperty(metaPropertyNode: MetaPropertyNode) {
        TODO()
    }

    private fun MethodAssembly.compileNewTarget() {
        operation("getNewTarget", JSValue::class)
    }

    private fun MethodAssembly.compileImportMeta() {
        TODO()
    }

    private fun MethodAssembly.compileNewExpression(newExpressionNode: NewExpressionNode) {
        compileExpression(newExpressionNode.target)
        val arguments = newExpressionNode.arguments
        if (arguments == null) {
            iconst_0
            anewarray<JSValue>()
        } else {
            compileArguments(arguments)
        }
        operation("evaluateNew", JSValue::class, JSValue::class, Array<JSValue>::class)
    }

    private fun MethodAssembly.compileCallExpression(callExpressionNode: CallExpressionNode) {
        compileExpression(callExpressionNode.target)
        dup
        operation("getValue", JSValue::class, JSValue::class)
        swap
        compileArguments(callExpressionNode.arguments)
        // TODO: Tail calls
        ldc(false)
        operation("evaluateCall", JSValue::class, JSValue::class, JSValue::class, Array<JSValue>::class, Boolean::class)
    }

    private fun MethodAssembly.compileArguments(argumentsNode: ArgumentsNode) {
        val arguments = argumentsNode.arguments
        ldc(arguments.size)
        anewarray<JSValue>()
        arguments.forEachIndexed { index, argument ->
            if (argument.isSpread)
                TODO()
            dup
            ldc(index)
            compileExpression(argument.expression)
            operation("getValue", JSValue::class, JSValue::class)
            aastore
        }
    }

    private fun MethodAssembly.compileOptionalExpression(optionalExpressionNode: OptionalExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileSuperCall(superCallNode: SuperCallNode) {
        TODO()
    }

    private fun MethodAssembly.compileImportCall(importCallNode: ImportCallNode) {
        TODO()
    }

    private fun MethodAssembly.compileUpdateExpression(updateExpressionNode: UpdateExpressionNode) {
        compileExpression(updateExpressionNode.target)
        dup
        val lhs = astore()
        // lhs

        var returnValue: Local? = null

        operation("getValue", JSValue::class, JSValue::class)
        operation("toNumeric", JSValue::class, JSValue::class)
        if (updateExpressionNode.isPostfix) {
            dup
            returnValue = astore()
        }
        // oldValue

        dup
        //oldValue, oldValue
        operation("checkNotBigInt", void, JSValue::class)
        // oldValue
        construct(JSNumber::class, Double::class) {
            ldc(1.0)
        }
        // oldValue, 1.0
        if (updateExpressionNode.isIncrement) {
            operation("numericAdd", JSValue::class, JSValue::class, JSValue::class)
        } else {
            operation("numericSubtract", JSValue::class, JSValue::class, JSValue::class)
        }
        // newValue
        if (!updateExpressionNode.isPostfix) {
            dup
            returnValue = astore()
        }

        aload(lhs)
        swap
        // lhs, newValue
        operation("putValue", void, JSValue::class, JSValue::class)
        // empty

        load(returnValue!!)
    }

    private fun MethodAssembly.compileUnaryExpression(unaryExpressionNode: UnaryExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileExponentiationExpression(exponentiationExpressionNode: ExponentiationExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            exponentiationExpressionNode.lhs,
            exponentiationExpressionNode.rhs,
            "**"
        )
    }

    private fun MethodAssembly.compileMultiplicationExpression(multiplicativeExpressionNode: MultiplicativeExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            multiplicativeExpressionNode.lhs,
            multiplicativeExpressionNode.rhs,
            multiplicativeExpressionNode.op.symbol
        )
    }

    private fun MethodAssembly.compileAdditiveExpression(additiveExpressionNode: AdditiveExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            additiveExpressionNode.lhs,
            additiveExpressionNode.rhs,
            if (additiveExpressionNode.isSubtraction) "-" else "+"
        )
    }

    private fun MethodAssembly.compileShiftExpression(shiftExpressionNode: ShiftExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            shiftExpressionNode.lhs,
            shiftExpressionNode.rhs,
            shiftExpressionNode.op.symbol
        )
    }

    private fun MethodAssembly.compileRelationalExpression(relationalExpressionNode: RelationalExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            relationalExpressionNode.lhs,
            relationalExpressionNode.rhs,
            relationalExpressionNode.op.symbol
        )
    }

    private fun MethodAssembly.compileEqualityExpression(equalityExpressionNode: EqualityExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            equalityExpressionNode.lhs,
            equalityExpressionNode.rhs,
            equalityExpressionNode.op.symbol
        )
    }

    private fun MethodAssembly.compileBitwiseANDExpression(bitwiseANDExpressionNode: BitwiseANDExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            bitwiseANDExpressionNode.lhs,
            bitwiseANDExpressionNode.rhs,
            "&"
        )
    }

    private fun MethodAssembly.compileBitwiseXORExpression(bitwiseXORExpressionNode: BitwiseXORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            bitwiseXORExpressionNode.lhs,
            bitwiseXORExpressionNode.rhs,
            "^"
        )
    }

    private fun MethodAssembly.compileBitwiseORExpression(bitwiseORExpressionNode: BitwiseORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            bitwiseORExpressionNode.lhs,
            bitwiseORExpressionNode.rhs,
            "|"
        )
    }

    private fun MethodAssembly.compileLogicalANDExpression(logicalANDExpressionNode: LogicalANDExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            logicalANDExpressionNode.lhs,
            logicalANDExpressionNode.rhs,
            "&&"
        )
    }

    private fun MethodAssembly.compileLogicalORExpression(logicalORExpressionNode: LogicalORExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            logicalORExpressionNode.lhs,
            logicalORExpressionNode.rhs,
            "||"
        )
    }

    private fun MethodAssembly.compileCoalesceExpression(coalesceExpressionNode: CoalesceExpressionNode) {
        evaluateStringOrNumericBinaryExpression(
            coalesceExpressionNode.lhs,
            coalesceExpressionNode.rhs,
            "??"
        )
    }

    private fun MethodAssembly.compileConditionalExpression(conditionalExpressionNode: ConditionalExpressionNode) {
        TODO()
    }

    private fun MethodAssembly.compileAssignmentExpression(assignmentExpressionNode: AssignmentExpressionNode) {
        expect(assignmentExpressionNode.lhs is LeftHandSideExpressionNode)

        if (assignmentExpressionNode.lhs is ObjectLiteralNode || assignmentExpressionNode.lhs is ArrayLiteralNode)
            TODO()

        compileExpression(assignmentExpressionNode.lhs)

        if (assignmentExpressionNode.op == AssignmentExpressionNode.Operator.Equals) {
            compileExpression(assignmentExpressionNode.rhs)
            operation("getValue", JSValue::class, JSValue::class)
            dup_x1
            operation("putValue", void, JSValue::class, JSValue::class)
        } else {
            TODO()
        }
    }

    private fun MethodAssembly.evaluateStringOrNumericBinaryExpression(lhs: ExpressionNode, rhs: ExpressionNode, op: String) {
        compileExpression(lhs)
        compileExpression(rhs)
        ldc(op)
        operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
    }

    /**
     * Stack arguments:
     *   None
     *
     * Stack result (+1):
     *   1. JSValue
     */
    private fun MethodAssembly.compileIdentifierReference(identifierReferenceNode: IdentifierReferenceNode) {
        ldc(identifierReferenceNode.identifierName)
        aconst_null
        operation("resolveBinding", JSReference::class, String::class, EnvRecord::class)
    }

    /**
     * Stack arguments:
     *   None
     *
     * Stack result (+1):
     *   JSValue
     */
    private fun MethodAssembly.compileThis() {
        operation("getGlobalObject", JSObject::class)
    }

    private val MethodAssembly.pushContext: Unit get() = aload_1

    private val MethodAssembly.pushAgent: Unit
        get() {
            pushContext
            getfield(ExecutionContext::class, "agent", Agent::class)
        }

    private val MethodAssembly.pushRealm: Unit
        get() {
            pushContext
            getfield(ExecutionContext::class, "realm", Realm::class)
        }

    private val MethodAssembly.pushFunction: Unit
        get() {
            pushContext
            getfield(ExecutionContext::class, "function", JSFunction::class)
        }

    private val MethodAssembly.pushLexicalEnv: Unit
        get() {
            pushContext
            getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }

    private val MethodAssembly.pushVariableEnv: Unit
        get() {
            pushContext
            getfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
        }

    private val MethodAssembly.pushUndefined: Unit
        get() {
            getstatic(JSUndefined::class, "INSTANCE", JSUndefined::class)
        }

    private val MethodAssembly.pushNull: Unit
        get() {
            getstatic(JSNull::class, "INSTANCE", JSNull::class)
        }

    private val MethodAssembly.pushTrue: Unit
        get() {
            getstatic(JSTrue::class, "INSTANCE", JSTrue::class)
        }

    private val MethodAssembly.pushFalse: Unit
        get() {
            getstatic(JSFalse::class, "INSTANCE", JSFalse::class)
        }

    private fun MethodAssembly.isUndefined() {
        pushUndefined
        acmp
    }

    private fun MethodAssembly.isNull() {
        pushNull
        acmp
    }

    private fun MethodAssembly.isTrue() {
        pushTrue
        acmp
    }

    private fun MethodAssembly.isFalse() {
        pushFalse
        acmp
    }

    private val MethodAssembly.acmp: Unit
        get() {
            ifElseStatement(JumpCondition.RefEqual) {
                ifBlock { iconst_1 }
                elseBlock { iconst_0 }
            }
        }

    private val MethodAssembly.sameValue: Unit
        get() = invokevirtual(JSValue::class, "sameValue", Boolean::class, JSValue::class)

    private val MethodAssembly.sameValueZero: Unit
        get() = invokevirtual(JSValue::class, "sameValueZero", Boolean::class, JSValue::class)

    private val MethodAssembly.sameValueNonNumeric: Unit
        get() = invokevirtual(JSValue::class, "sameValueNonNumeric", Boolean::class, JSValue::class)

    private val MethodAssembly.toBoolean: Unit
        get() = invokevirtual(JSValue::class, "toBoolean", Boolean::class)

    private val MethodAssembly.toNumber: Unit
        get() = invokevirtual(JSValue::class, "toNumber", Double::class)

    private val MethodAssembly.toObject: Unit
        get() = invokevirtual(JSValue::class, "toObject", JSObject::class)

    private val MethodAssembly.wrapInValue: Unit
        get() {
            operation("wrapInValue", JSValue::class, Any::class)
        }

    private fun MethodAssembly.operation(name: String, returnType: TypeLike, vararg parameterTypes: TypeLike) {
        invokestatic(Operations::class, name, returnType, *parameterTypes)
    }

    companion object {
        private var classCounter = 0
            get() = field++
    }
}

/*
function foo() {
}

{
    function foo() {}
}

export foo;
 */

/*
abstract class CompiledProgram {

    abstract fun run()

}




class TopLevel_libjs { }

class Function_foo$0_libjs { }

class Function_foo$1_libjs { }
 */
