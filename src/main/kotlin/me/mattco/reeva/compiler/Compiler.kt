package me.mattco.reeva.compiler

import codes.som.anthony.koffee.MethodAssembly
import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.*
import codes.som.anthony.koffee.modifiers.public
import codes.som.anthony.koffee.types.TypeLike
import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.LiteralNode
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.literals.PropertyNameNode
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.DeclarativeEnvRecord
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.JSReference
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import org.objectweb.asm.tree.ClassNode
import java.lang.IllegalArgumentException

class Compiler(private val scriptNode: ScriptNode, fileName: String) {
    private val sanitizedFileName = fileName.replace(Regex("[^\\w]"), "_")
    private val dependencies = mutableListOf<ClassNode>()

    val className = "TopLevel_${sanitizedFileName}_$classCounter"

    fun compile(): CompilationResult {
        val mainClass = assembleClass(public, className, superName = "me/mattco/reeva/compiler/TopLevelScript") {
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

        return CompilationResult(mainClass, dependencies)
    }

    data class CompilationResult(val mainClass: ClassNode, val dependencies: List<ClassNode>)

    @ECMAImpl("GlobalDeclarationInstantiation", "15.1.11")
    private fun MethodAssembly.compileScript(script: ScriptNode) {
        aload_1
        getfield(ExecutionContext::class, "realm", Realm::class)
        getfield(Realm::class, "globalEnv", GlobalEnvRecord::class)

        val lexNames = script.lexicallyDeclaredNames()
        val varNames = script.varDeclaredNames()
        lexNames.forEach {
            listOf("hasVarDeclaration", "hasLexicalDeclaration", "hasRestrictedGlobalProperty").forEach { method ->
                dup
                ldc(it)
                invokevirtual(GlobalEnvRecord::class, method, Boolean::class, String::class)
                ifStatement(JumpCondition.True) {
                    shouldThrow("SyntaxError")
                }
            }
        }
        varNames.forEach {
            dup
            ldc(it)
            invokevirtual(GlobalEnvRecord::class, "hasLexicalDeclaration", Boolean::class, String::class)
            ifStatement(JumpCondition.True) {
                shouldThrow("SyntaxError")
            }
        }

        val varDeclarations = script.varScopedDeclarations()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableListOf<String>()
        varDeclarations.asReversed().forEach {
            if (it is VariableDeclarationNode || it is ForBindingNode || it is BindingIdentifierNode)
                return@forEach
            ecmaAssert(it is FunctionDeclarationNode)
            val boundNames = it.boundNames()
            expect(boundNames.size == 1)
            val functionName = boundNames[0]
            if (functionName !in declaredFunctionNames) {
                dup
                ldc(functionName)
                invokevirtual(GlobalEnvRecord::class, "canDeclareGlobalFunction", Boolean::class, String::class)
                ifStatement(JumpCondition.False) {
                    shouldThrow("TypeError")
                }
                declaredFunctionNames.add(functionName)
                functionsToInitialize.add(0, it)
            }
        }
        var declaredVarNames = mutableListOf<String>()
        varDeclarations.forEach { decl ->
            if (decl is VariableDeclarationNode || decl is ForBindingNode || decl is BindingIdentifierNode) {
                decl.boundNames().forEach { name ->
                    if (name !in declaredFunctionNames) {
                        dup
                        ldc(name)
                        invokevirtual(GlobalEnvRecord::class, "canDeclareGlobalVar", Boolean::class, String::class)
                        ifStatement(JumpCondition.True) {
                            shouldThrow("TypeError")
                        }
                        if (name !in declaredVarNames)
                            declaredVarNames.add(name)
                    }
                }
            }
        }
        val lexDeclarations = script.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                dup
                ldc(name)
                if (decl.isConstantDeclaration()) {
                    ldc(true)
                    invokevirtual(GlobalEnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                } else {
                    ldc(false)
                    invokevirtual(GlobalEnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
        }
        functionsToInitialize.forEach { func ->
            val compiledFunc = instantiateFunctionObject(func)
            dependencies.add(compiledFunc)

            dup
            ldc(func.identifier?.identifierName ?: "default")
            construct(compiledFunc.name, Realm::class) {
                pushRealm
            }
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalFunctionBinding", void, String::class, JSFunction::class, Boolean::class)
        }
        declaredVarNames.forEach {
            dup
            ldc(it)
            ldc(false)
            invokevirtual(GlobalEnvRecord::class, "createGlobalVarBinding", void, String::class, Boolean::class)
        }

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
            is EmptyStatementNode -> {
            }
            is ExpressionStatementNode -> compileExpressionStatement(statement)
            is IfStatementNode -> compileIfStatement(statement)
            is BreakableStatement -> compileBreakableStatement(statement)
            is LabelledStatement -> compileLabelledStatement(statement)
            is LexicalDeclarationNode -> compileLexicalDeclaration(statement)
            is FunctionDeclarationNode -> compileFunctionDeclaration(statement)
            is ReturnStatementNode -> compileReturnStatement(statement)
            else -> TODO()
        }
    }

    private fun MethodAssembly.compileReturnStatement(returnStatementNode: ReturnStatementNode) {
        if (returnStatementNode.node == null) {
            pushUndefined
        } else {
            compileExpression(returnStatementNode.node)
        }
        // Pop the current context off of the execution stack
        invokestatic(Agent::class, "popContext", void)
        areturn
    }

    private fun MethodAssembly.compileFunctionDeclaration(functionDeclarationNode: FunctionDeclarationNode) {
        // TODO
    }

    private fun MethodAssembly.compileBlock(block: BlockNode) {
        // TODO: Scopes
        if (block.statements != null) {
            pushRunningContext
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

    private fun MethodAssembly.compileEmptyStatement(emptyStatement: EmptyStatementNode) {}

    private fun MethodAssembly.compileIfStatement(ifStatement: IfStatementNode) {
        compileExpression(ifStatement.condition)
        operation("getValue", JSValue::class, JSValue::class)
        operation("toBoolean", JSValue::class, JSValue::class)
        pushTrue
        if (ifStatement.falseBlock == null) {
            ifStatement(JumpCondition.RefEqual) {
                compileStatement(ifStatement.trueBlock)
            }
        } else {
            ifElseStatement(JumpCondition.RefEqual) {
                ifBlock {
                    compileStatement(ifStatement.trueBlock)
                }

                elseBlock {
                    compileStatement(ifStatement.falseBlock)
                }
            }
        }
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

    @ECMAImpl(section = "13.3.1.4")
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
            is ObjectLiteralNode -> compileObjectLiteral(expressionNode)
            is ArrayLiteralNode -> compileArrayLiteral(expressionNode)
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

    @ECMAImpl
    private fun MethodAssembly.compileObjectLiteral(objectLiteralNode: ObjectLiteralNode) {
        pushRealm
        invokestatic(JSObject::class, "create", JSObject::class, Realm::class)

        if (objectLiteralNode.list != null) {
            objectLiteralNode.list.properties.forEach { property ->
                dup
                compilePropertyDefinition(property, true)
            }
        }
    }

    @ECMAImpl(section = "12.2.6.8")
    @ECMAImpl(section = "14.3.8")
    private fun MethodAssembly.compilePropertyDefinition(property: PropertyDefinitionNode, enumerable: Boolean) {
        when (property.type) {
            PropertyDefinitionNode.Type.KeyValue -> {
                expect(property.first is PropertyNameNode)
                expect(property.second != null)
                compilePropertyName(property.first)
                if (isAnonymousFunctionDefinition(property.second)) {
                    TODO()
                } else {
                    compileExpression(property.second)
                    operation("getValue", JSValue::class, JSValue::class)
                }
                ecmaAssert(enumerable)
                operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                pop
            }
            PropertyDefinitionNode.Type.Shorthand -> {
                expect(property.first is IdentifierReferenceNode)
                ldc(property.first.identifierName)
                wrapInValue
                compileExpression(property.first)
                operation("getValue", JSValue::class, JSValue::class)
                expect(enumerable)
                operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
                pop
            }
            PropertyDefinitionNode.Type.Method -> TODO()
            PropertyDefinitionNode.Type.Spread -> TODO()
        }
    }

    private fun MethodAssembly.compilePropertyName(propertyNameNode: PropertyNameNode) {
        val expr = propertyNameNode.expr

        when {
            propertyNameNode.isComputed -> compileExpression(expr)
            expr is IdentifierNode -> ldc(expr.identifierName)
            expr is StringLiteralNode -> ldc(expr.value)
            expr is NumericLiteralNode -> ldc(expr.value)
            else -> unreachable()
        }

        if (!propertyNameNode.isComputed)
            wrapInValue
    }

    private fun MethodAssembly.compileArrayLiteral(arrayLiteralNode: ArrayLiteralNode) {
        ldc(arrayLiteralNode.elements.size)
        operation("arrayCreate", JSValue::class, Int::class)
        arrayLiteralNode.elements.forEachIndexed { index, element ->
            dup
            construct(JSString::class, String::class) {
                ldc(index.toString())
            }
            if (element.expression == null) {
                // TODO: Spec doesn't specify this step, is it alright to do this?
                pushEmpty
            } else {
                compileExpression(element.expression)
                operation("getValue", JSValue::class, JSValue::class)
                operation("toString", JSString::class, JSValue::class)
            }
            operation("createDataPropertyOrThrow", Boolean::class, JSValue::class, JSValue::class, JSValue::class)
            pop
        }
    }

    private fun MethodAssembly.compileCommaExpressionNode(commaExpressionNode: CommaExpressionNode) {
        commaExpressionNode.expressions.forEachIndexed { index, node ->
            compileExpression(node)
            if (index != commaExpressionNode.expressions.lastIndex)
                pop
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

    @ECMAImpl(section = "12.3.2.1")
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

    @ECMAImpl(section = "12.15.4")
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
        operation("getValue", JSValue::class, JSValue::class)
        compileExpression(rhs)
        operation("getValue", JSValue::class, JSValue::class)
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

    /*
     * SCRIPT FUNCTION COMPILATION
     */
    @ECMAImpl("InstantiateFunctionObject", "14.1.22")
    private fun instantiateFunctionObject(function: FunctionDeclarationNode): ClassNode {
        val functionName = function.identifier?.identifierName ?: "default"
        val parameters = function.parameters

        return assembleClass(
            public,
            "Function_${functionName}_${sanitizedFileName}_${Agent.objectCount++}",
            superName = "me/mattco/reeva/compiler/JSScriptFunction"
        ) {
            method(public, "<init>", void, Realm::class) {
                aload_0
                aload_1
                getstatic(JSFunction.ThisMode::class, "NonLexical", JSFunction.ThisMode::class)
                // TODO: Strict mode
                ldc(false)
                invokespecial(JSScriptFunction::class, "<init>", void, Realm::class, JSFunction.ThisMode::class, Boolean::class)
                _return
            }

            method(public, "name", String::class) {
                ldc(functionName)
                areturn
            }

            method(public, "getSourceText", String::class) {
                // TODO
                ldc("")
                areturn
            }

            method(public, "isClassConstructor", Boolean::class) {
                ldc(false)
                ireturn
            }

            method(public, "getHomeObject", JSValue::class) {
                // TODO
                pushUndefined
                areturn
            }

            method(public, "getParameterNames", Array<String>::class) {
                if (parameters.restParameter == null) {
                    val actualParams = parameters.functionParameters.parameters
                    ldc(actualParams.size)
                    anewarray<String>()
                    actualParams.forEachIndexed { index, param ->
                        dup
                        ldc(index)
                        ldc(param.bindingElement.binding.identifier.identifierName)
                        aastore
                    }
                } else {
                    TODO()
                }
                areturn
            }

            method(public, "getParamHasDefaultValue", Boolean::class, Int::class) {
                if (parameters.restParameter == null) {
                    parameters.functionParameters.parameters.forEachIndexed { index, param ->
                        iload_1
                        ldc(index)
                        ifStatement(JumpCondition.Equal) {
                            ldc(param.bindingElement.binding.initializer != null)
                            ireturn
                        }
                    }
                    construct(IllegalArgumentException::class, String::class) {
                        ldc("Invalid index given to getParamHasDefaultValue")
                    }
                    athrow
                } else {
                    TODO()
                }
            }

            method(public, "getDefaultParameterValue", JSValue::class, Int::class) {
                if (parameters.restParameter == null) {
                    parameters.functionParameters.parameters.forEachIndexed { index, param ->
                        val binding = param.bindingElement.binding

                        iload_1
                        ldc(index)
                        ifStatement(JumpCondition.Equal) {
                            if (binding.initializer == null) {
                                pushUndefined
                            } else {
                                compileExpression(binding.initializer.node)
                            }
                            areturn
                        }
                    }
                    construct(IllegalArgumentException::class, String::class) {
                        ldc("Invalid index given to getDefaultParameterValue")
                    }
                    athrow
                } else {
                    TODO()
                }
            }

            method(public, "call", JSValue::class, JSValue::class, List::class) {
                // setup
                aload_0
                pushUndefined
                operation("prepareForOrdinaryCall", ExecutionContext::class, JSScriptFunction::class, JSValue::class)
                aload_0
                swap
                aload_1
                operation("ordinaryCallBindThis", JSValue::class, JSScriptFunction::class, ExecutionContext::class, JSValue::class)
                pop

                functionDeclarationInstantiation(function, JSFunction.ThisMode.NonLexical)

                // actual body
                if (function.body.statementList != null)
                    compileStatementList(function.body.statementList)

                // The method may not have a return statement, so we insert one here just in case.
                // If it does, it will do the exact same thing we do here
                invokestatic(Agent::class, "popContext", void)
                pushUndefined
                areturn
            }

            method(public, "construct", JSValue::class, List::class, JSObject::class) {
                // TODO
                pushUndefined
                areturn
            }
        }
    }

    @ECMAImpl("FunctionDeclarationInstantiation", "9.2.10")
    private fun MethodAssembly.functionDeclarationInstantiation(function: FunctionDeclarationNode, thisMode: JSFunction.ThisMode) {
        val parameters = function.parameters
        val body = function.body
        val parameterNames = parameters.boundNames()
        val hasDuplicates = parameterNames.groupBy { it }.size != parameterNames.size
        val simpleParameterList = parameters.isSimpleParameterList()
        val hasParameterExpressions = parameters.containsExpression()
        val varNames = body.varDeclaredNames()
        val varDeclarations = body.varScopedDeclarations()
        val lexicalNames = body.lexicallyDeclaredNames()

        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        body.varScopedDeclarations().asReversed().forEach { decl ->
            if (decl is FunctionDeclarationNode) {
                val boundNames = decl.boundNames()
                expect(boundNames.size == 1)
                val declName = boundNames[0]
                if (declName !in functionNames) {
                    functionNames.add(0, declName)
                    functionsToInitialize.add(decl)
                }
            }
        }

        var argumentsObjectNeeded = when {
            thisMode == JSFunction.ThisMode.Lexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexicalNames) -> false
            else -> true
        }

        pushRunningContext
        getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)

        // TODO: Strict check
        if (!hasParameterExpressions) {
            astore(3)
        } else {
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            dup
            astore(3)
            putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }

        parameterNames.forEach { name ->
            aload(3)
            ldc(name)
            invokevirtual(EnvRecord::class, "hasBinding", Boolean::class, String::class)
            ifStatement(JumpCondition.False) {
                aload(3)
                ldc(name)
                ldc(false)
                invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                if (hasDuplicates) {
                    aload(3)
                    ldc(name)
                    pushUndefined
                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        // This will be dependent on the argumentsObjectNeeded flag
        val parameterBindings = parameterNames

        if (argumentsObjectNeeded) {
            // TODO
        }

        aload_0
        aload_2
        if (hasDuplicates) {
            pushUndefined
        } else {
            aload(3)
        }
        operation("applyFunctionArguments", void, JSScriptFunction::class, List::class, EnvRecord::class)

        val varEnv: Local

        if (!hasParameterExpressions) {
            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    aload(3)
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
            aload(3)
            varEnv = astore()
        } else {
            aload(3)
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            varEnv = astore()
            pushRunningContext
            load(varEnv)
            putfield(ExecutionContext::class, "variableEnv", EnvRecord::class)

            val instantiatedVarNames = parameterBindings.toMutableList()
            varNames.forEach { name ->
                if (name !in instantiatedVarNames) {
                    instantiatedVarNames.add(name)
                    load(varEnv)
                    ldc(name)
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)

                    load(varEnv)
                    ldc(name)

                    if (name !in parameterBindings || name in functionNames) {
                        pushUndefined
                    } else {
                        aload(3)
                        ldc(name)
                        ldc(false)
                        invokevirtual(EnvRecord::class, "getBindingValue", JSValue::class, String::class, Boolean::class)
                    }

                    invokevirtual(EnvRecord::class, "initializeBinding", void, String::class, JSValue::class)
                }
            }
        }

        // TODO: if (isStrict)
        val lexEnv: Local
        if (true) {
            load(varEnv)
            invokestatic(DeclarativeEnvRecord::class, "create", DeclarativeEnvRecord::class, EnvRecord::class)
            lexEnv = astore()
        } else {
            lexEnv = varEnv
        }

        pushRunningContext
        load(lexEnv)
        putfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)

        val lexDeclarations = body.lexicallyScopedDeclarations()
        lexDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                load(lexEnv)
                ldc(name)
                if (decl.isConstantDeclaration()) {
                    ldc(true)
                    invokevirtual(EnvRecord::class, "createImmutableBinding", void, String::class, Boolean::class)
                } else {
                    ldc(false)
                    invokevirtual(EnvRecord::class, "createMutableBinding", void, String::class, Boolean::class)
                }
            }
        }

        functionsToInitialize.forEach { func ->
            val compiledFunc = instantiateFunctionObject(func)
            dependencies.add(compiledFunc)

            val boundNames = func.boundNames()
            expect(boundNames.size == 1)
            val funcName = boundNames[0]

            load(varEnv)
            ldc(funcName)
            construct(compiledFunc.name, Realm::class) {
                pushRealm
            }
            ldc(false)
            invokevirtual(EnvRecord::class, "setMutableBinding", void, String::class, JSValue::class, Boolean::class)
        }
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

    private fun MethodAssembly.shouldThrow(errorName: String) {
        construct(java.lang.IllegalStateException::class, String::class) {
            ldc("FIXME: This should have instead thrown a JS $errorName")
        }
        athrow
    }

    private val MethodAssembly.pushRunningContext: Unit
        get() {
            invokestatic(Agent::class, "getRunningContext", ExecutionContext::class)
        }

    private val MethodAssembly.pushAgent: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "agent", Agent::class)
        }

    private val MethodAssembly.pushRealm: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "realm", Realm::class)
        }

    private val MethodAssembly.pushFunction: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "function", JSFunction::class)
        }

    private val MethodAssembly.pushLexicalEnv: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "lexicalEnv", EnvRecord::class)
        }

    private val MethodAssembly.pushVariableEnv: Unit
        get() {
            pushRunningContext
            getfield(ExecutionContext::class, "variableEnv", EnvRecord::class)
        }

    private val MethodAssembly.pushEmpty: Unit
        get() {
            getstatic(JSEmpty::class, "INSTANCE", JSEmpty::class)
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
