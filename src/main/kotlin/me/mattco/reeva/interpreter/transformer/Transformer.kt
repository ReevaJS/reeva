package me.mattco.reeva.interpreter.transformer

import me.mattco.reeva.ast.*
import me.mattco.reeva.ast.expressions.*
import me.mattco.reeva.ast.literals.*
import me.mattco.reeva.ast.statements.*
import me.mattco.reeva.interpreter.DeclarationsArray
import me.mattco.reeva.interpreter.transformer.opcodes.*
import me.mattco.reeva.parser.GlobalScope
import me.mattco.reeva.parser.Scope
import me.mattco.reeva.parser.Variable
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable
import java.math.BigInteger
import kotlin.math.floor

data class FunctionInfo(
    val name: String?,
    val code: FunctionOpcodes,
    val argCount: Int,
    val isStrict: Boolean,
    val isTopLevelScript: Boolean,
)

class Transformer : ASTVisitor {
    private lateinit var generator: Generator

    fun transform(node: ASTNode): FunctionInfo {
        if (::generator.isInitialized)
            throw IllegalStateException("Cannot re-use an IRTransformer")

        generator = Generator()

        if (node is ModuleNode)
            TODO()

        expect(node is ScriptNode)

        globalDeclarationInstantiation(node.scope) {
            visit(node.statements)
            generator.add(Return)
        }

        return FunctionInfo(
            null,
            generator.finish(),
            1,
            node.scope.isStrict,
            isTopLevelScript = true
        )
    }

    private fun enterScope(scope: Scope) {
        if (scope !is GlobalScope)
            generator.add(PushLexicalEnv)
    }

    private fun exitScope(scope: Scope) {
        if (scope !is GlobalScope)
            generator.add(PopEnv)
    }

    override fun visitBlock(node: BlockNode) {
        enterScope(node.scope)
        super.visitBlock(node)
        exitScope(node.scope)
    }

    override fun visitExpressionStatement(node: ExpressionStatementNode) {
        visit(node.node)
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visit(node.condition)

        if (node.falseBlock == null) {
            generator.ifHelper(::JumpIfTrue) {
                visit(node.trueBlock)
            }
        } else {
            generator.ifElseHelper(::JumpIfTrue, {
                visit(node.trueBlock)
            }, {
                visit(node.falseBlock)
            })
        }
    }

    override fun visitDoWhileStatement(node: DoWhileStatementNode) {
        val headBlock = generator.makeBlock()
        val doneBlock = generator.makeBlock()

        generator.currentBlock = headBlock
        visit(node.body)
        visit(node.condition)
        generator.add(JumpIfTrue(headBlock, doneBlock))
        generator.currentBlock = doneBlock
    }

    override fun visitWhileStatement(node: WhileStatementNode) {
        val testBlock = generator.makeBlock()
        val bodyBlock = generator.makeBlock()
        val doneBlock = generator.makeBlock()

        generator.currentBlock = testBlock
        visit(node.condition)
        generator.add(JumpIfTrue(bodyBlock, doneBlock))
        generator.currentBlock = bodyBlock
        visit(node.body)
        generator.add(Jump(testBlock))
        generator.currentBlock = doneBlock
    }

    override fun visitForStatement(node: ForStatementNode) {
        if (node.initScope != null)
            enterScope(node.initScope)

        if (node.initializer != null)
            visit(node.initializer)

        var testBlock: Block? = null

        if (node.condition != null) {
            testBlock = generator.makeBlock()
        }

        val bodyBlock = generator.makeBlock()
        val doneBlock = generator.makeBlock()

        if (node.condition != null) {
            generator.currentBlock = testBlock!!
            visit(node.condition)
            generator.add(JumpIfTrue(bodyBlock, doneBlock))
        }

        generator.currentBlock = bodyBlock
        generator.enterBreakableScope(doneBlock)
        generator.enterContinuableScope(testBlock ?: bodyBlock)
        visit(node.body)
        generator.exitBreakableScope()
        generator.exitContinuableScope()

        if (node.incrementer != null)
            visit(node.incrementer)

        generator.add(Jump(testBlock ?: bodyBlock))

        generator.currentBlock = doneBlock

        if (node.initScope != null)
            exitScope(node.initScope)
    }

    override fun visitForIn(node: ForInNode) {
        TODO()
    }

    override fun visitForOf(node: ForOfNode) {
        TODO()
//        visit(node.expression)
//        add(GetIterator)
//        val iter = generator.reserveRegister()
//        add(Star, iter)
//
//        val next = generator.reserveRegister()
//        add(LdaNamedProperty, iter, generator.intern("next"))
//        add(Star, next)
//        add(CallRuntime, InterpRuntime.ThrowIfIteratorNextNotCallable, next, 1)
//
//        // TODO: This isn't right, ForOfNode should have an optional scope for the initializer
//        if (node.scope.requiresEnv) {
//            add(PushEnv, builder.enterScope(node.scope))
//            node.scope.declaredVariables.forEachIndexed { index, variable ->
//                variable.slot = index
//            }
//        }
//
//        val loopStart = label()
//        val loopEnd = label()
//
//        place(loopStart)
//
//        // TODO: ???
////        if (node.scope.requiresEnv)
////            add(PushEnv)
//
//        add(Call0, next, iter)
//        val nextResult = generator.reserveRegister()
//        add(Star, nextResult)
//        add(CallRuntime, InterpRuntime.ThrowIfIteratorReturnNotObject, nextResult, 1)
//        add(LdaNamedProperty, nextResult, generator.intern("done"))
//        jump(loopEnd, JumpIfToBooleanTrue)
//
//        add(LdaNamedProperty, nextResult, generator.intern("value"))
//
//        when (node.decl) {
//            is VariableDeclarationNode -> {
//                val decl = node.decl.declarations[0]
//                val variable = decl.identifier.variable
//                storeEnvVariableRef(variable, node.scope)
//            }
//            is LexicalDeclarationNode -> {
//                val decl = node.decl.declarations[0]
//                val variable = decl.identifier.variable
//                storeEnvVariableRef(variable, node.scope)
//            }
//            is BindingIdentifierNode -> {
//                val variable = node.decl.variable
//                storeEnvVariableRef(variable, node.scope)
//            }
//        }
//
//        visit(node.body)
//
//        if (node.scope.requiresEnv)
//            add(PopCurrentEnv, builder.exitScope(node.scope))
//
//        jump(loopStart)
//        place(loopEnd)
    }

    override fun visitForAwaitOf(node: ForAwaitOfNode) {
        TODO()
    }

    override fun visitLabelledStatement(node: LabelledStatementNode) {
        TODO()
    }

    override fun visitThrowStatement(node: ThrowStatementNode) {
        visit(node.expr)
        generator.add(Throw)
    }

    override fun visitTryStatement(node: TryStatementNode) {
        TODO()
    }

    override fun visitBreakStatement(node: BreakStatementNode) {
        if (node.label != null)
            TODO()

        generator.add(Jump(generator.breakableScope))
        generator.currentBlock = generator.makeBlock()
    }

    override fun visitContinueStatement(node: ContinueStatementNode) {
        if (node.label != null)
            TODO()

        generator.add(Jump(generator.continuableScope))
        generator.currentBlock = generator.makeBlock()
    }

    override fun visitReturnStatement(node: ReturnStatementNode) {
        if (node.expression != null)
            visit(node.expression)
        generator.add(Return)
    }

    override fun visitLexicalDeclaration(node: LexicalDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    override fun visitVariableDeclaration(node: VariableDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    private fun visitDeclaration(declaration: Declaration) {
        val variable = declaration.variable

        if (declaration.initializer != null) {
            visit(declaration.initializer)
        } else {
            generator.add(LdaUndefined)
        }

        storeVariable(variable, declaration.scope)
    }

    override fun visitDebuggerStatement() {
        generator.add(DebugBreakpoint)
    }

    override fun visitImportDeclaration(node: ImportDeclarationNode) {
        TODO()
    }

    override fun visitExport(node: ExportNode) {
        TODO()
    }

    override fun visitBindingIdentifier(node: BindingIdentifierNode) {
        TODO()
    }

    override fun visitIdentifierReference(node: IdentifierReferenceNode) {
        val variable = node.targetVar
        loadVariable(node.targetVar, node.scope)
        if (node.targetVar.mode == Variable.Mode.Global)
            return

        val declarationStart = node.targetVar.source.sourceStart
        val useStart = node.sourceStart
        if (useStart.index < declarationStart.index && variable.type != Variable.Type.Var) {
            // We need to check if the variable has been initialized
            generator.add(ThrowUseBeforeInitIfEmpty(generator.intern(node.identifierName)))
        }
    }

    override fun visitFunctionDeclaration(node: FunctionDeclarationNode) {
        // nop
    }

    override fun visitFunctionExpression(node: FunctionExpressionNode) {
        visitFunctionHelper(
            node.identifier?.identifierName ?: "<anonymous>",
            node.parameters,
            node.body,
            node.parameterScope,
            node.bodyScope,
            node.scope.isStrict,
            false,
        )
    }

    override fun visitArrowFunction(node: ArrowFunctionNode) {
        visitFunctionHelper(
            "<anonymous>",
            node.parameters,
            node.body,
            node.parameterScope,
            node.bodyScope,
            node.scope.isStrict,
            true,
        )
    }

    private fun globalDeclarationInstantiation(
        scope: Scope,
        evaluationBlock: () -> Unit
    ) {
        enterScope(scope)

        val varDecls = scope.declaredVariables.filter { it.type == Variable.Type.Var }.map { it.source }
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val declaredFunctionNames = mutableSetOf<String>()

        for (varDecl in varDecls.asReversed()) {
            if (varDecl !is FunctionDeclarationNode || varDecl.identifier.identifierName in declaredFunctionNames)
                continue

            functionsToInitialize.add(0, varDecl)
            declaredFunctionNames.add(varDecl.identifier.identifierName)
        }

        val declaredVarNames = mutableSetOf<String>()

        for (varDecl in varDecls) {
            if (varDecl is FunctionDeclarationNode)
                continue

            val name = varDecl.variable.name
            if (name !in declaredFunctionNames && name !in declaredVarNames) {
                declaredVarNames.add(name)
            }
        }

        val lexNames = scope.declaredVariables.filter { it.type != Variable.Type.Var }.map { it.name }

        val array = DeclarationsArray(declaredVarNames.toList(), lexNames, declaredFunctionNames.toList())
        generator.add(DeclareGlobals(generator.intern(array)))

        commonInstantiation(varDecls, declaredFunctionNames, functionsToInitialize)

        evaluationBlock()

        exitScope(scope)
    }

    private fun functionDeclarationInstantiation(
        parameters: ParameterList,
        parameterScope: Scope,
        bodyScope: Scope,
        isLexical: Boolean,
        isStrict: Boolean,
        evaluationBlock: () -> Unit,
    ) {
        val parameterNames = parameters.map { it.name }
        val simpleParameterList = parameters.isSimple()
        val hasParameterExpressions = parameters.any { it.initializer != null }

        val lexNames = bodyScope.declaredVariables.filter { it.type != Variable.Type.Var }.map { it.name }
        val varDecls = bodyScope.declaredVariables.filter {
            it.type == Variable.Type.Var && it.mode != Variable.Mode.Parameter
        }.map { it.source }

        val functionNames = mutableSetOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        for (decl in varDecls.asReversed()) {
            if (decl !is FunctionDeclarationNode || decl.identifier.identifierName in functionNames)
                continue

            functionsToInitialize.add(0, decl)
            functionNames.add(decl.identifier.identifierName)
        }

        val argumentsObjectNeeded = when {
            isLexical -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions && ("arguments" in functionNames || "arguments" in lexNames) -> false
            else -> true
        }

        if (argumentsObjectNeeded && bodyScope.possiblyReferencesArguments) {
            if (isStrict || !simpleParameterList) {
                generator.add(CreateUnmappedArgumentsObject)
            } else {
                generator.add(CreateMappedArgumentsObject)
            }
        }

        enterScope(parameterScope)

        parameters.distinctBy { it.identifier.identifierName }.forEachIndexed { index, param ->
            val register = index + 1

            val name = generator.intern(param.variable.name)

            generator.add(Ldar(register))

            if (param.initializer != null) {
                generator.ifHelper(::JumpIfUndefined) {
                    visit(param.initializer)
                }
            }

            generator.add(StaCurrentEnv(name))
        }

        if (bodyScope != parameterScope)
            enterScope(bodyScope)

        commonInstantiation(varDecls, functionNames, functionsToInitialize)

        evaluationBlock()

        if (bodyScope != parameterScope)
            exitScope(bodyScope)
        exitScope(parameterScope)
    }

    private fun commonInstantiation(
        varDecls: List<VariableSourceNode>,
        functionNames: Set<String>,
        functions: List<FunctionDeclarationNode>,
    ) {
        val instantiatedVarNames = functionNames.toMutableSet()

        for (varDecl in varDecls) {
            if (varDecl is FunctionDeclarationNode || varDecl.name in instantiatedVarNames)
                continue

            instantiatedVarNames.add(varDecl.name)
            val variable = varDecl.variable

            if (variable.name !in instantiatedVarNames) {
                instantiatedVarNames.add(variable.name)

                if (variable.possiblyUsedBeforeDecl) {
                    generator.add(LdaUndefined)
                    storeVariable(variable, varDecl.scope)
                }
            }
        }

        for (function in functions) {
            visitFunctionHelper(
                function.name,
                function.parameters,
                function.body,
                function.parameterScope,
                function.bodyScope,
                function.scope.isStrict || function.parameterScope.isStrict || function.bodyScope.isStrict,
                false, // TODO
            )

            storeVariable(function.variable, function.scope)
        }
    }

    private fun visitFunctionHelper(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        parameterScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        isLexical: Boolean,
    ) {
        val prevGenerator = generator
        generator = Generator()

        functionDeclarationInstantiation(
            parameters,
            parameterScope,
            bodyScope,
            isLexical,
            isStrict
        ) {
            // body's scope is the same as the function's scope (the scope we receive
            // as a parameter). We don't want to re-enter the same scope, so we explicitly
            // call super.visitBlock if necessary.
            if (body is BlockNode) {
                super.visitBlock(body)
            } else visit(body)

            if (generator.currentBlock.lastOrNull() !is Return) {
                generator.add(LdaUndefined)
                generator.add(Return)
            }
        }

        val info = FunctionInfo(
            name,
            generator.finish(),
            parameters.size + 1,
            isStrict,
            isTopLevelScript = false
        )

        generator = prevGenerator
        generator.add(CreateClosure(generator.intern(info)))
    }

    override fun visitClassDeclaration(node: ClassDeclarationNode) {
        TODO()
    }

    override fun visitClassExpression(node: ClassExpressionNode) {
        TODO()
    }

    override fun visitBinaryExpression(node: BinaryExpressionNode) {
        val op = when (node.operator) {
            BinaryOperator.Add -> ::Add
            BinaryOperator.Sub -> ::Sub
            BinaryOperator.Mul -> ::Mul
            BinaryOperator.Div -> ::Div
            BinaryOperator.Exp -> ::Exp
            BinaryOperator.Mod -> ::Mod
            BinaryOperator.BitwiseAnd -> ::BitwiseAnd
            BinaryOperator.BitwiseOr -> ::BitwiseOr
            BinaryOperator.BitwiseXor -> ::BitwiseXor
            BinaryOperator.Shl -> ::ShiftLeft
            BinaryOperator.Shr -> ::ShiftRight
            BinaryOperator.UShr -> ::ShiftRightUnsigned
            BinaryOperator.StrictEquals -> ::TestEqualStrict
            BinaryOperator.StrictNotEquals -> ::TestNotEqualStrict
            BinaryOperator.SloppyEquals -> ::TestEqual
            BinaryOperator.SloppyNotEquals -> ::TestNotEqual
            BinaryOperator.LessThan -> ::TestLessThan
            BinaryOperator.LessThanEquals -> ::TestLessThanOrEqual
            BinaryOperator.GreaterThan -> ::TestGreaterThan
            BinaryOperator.GreaterThanEquals -> ::TestGreaterThanOrEqual
            BinaryOperator.Instanceof -> ::TestInstanceOf
            BinaryOperator.In -> ::TestIn
            BinaryOperator.And -> {
                visit(node.lhs)
                generator.add(ToBoolean)
                generator.ifHelper(::JumpIfTrue) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Or -> {
                visit(node.lhs)
                generator.add(ToBoolean)
                generator.ifHelper(::JumpIfTrue, negateOp = true) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Coalesce -> {
                visit(node.lhs)
                generator.ifHelper(::JumpIfNullish) {
                    visit(node.rhs)
                }
                return
            }
        }

        visit(node.lhs)

        val lhsReg = generator.reserveRegister()
        generator.add(Star(lhsReg))
        visit(node.rhs)
        generator.add(op(lhsReg))
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        if (node.op == UnaryOperator.Delete) {
            when (val expr = node.expression) {
                is IdentifierReferenceNode -> generator.add(LdaFalse)
                !is MemberExpressionNode -> generator.add(LdaTrue)
                else -> if (expr.type == MemberExpressionNode.Type.Tagged) {
                    generator.add(LdaTrue)
                } else {
                    visit(expr.lhs)
                    val target = generator.reserveRegister()
                    generator.add(Star(target))

                    if (expr.type == MemberExpressionNode.Type.Computed) {
                        visit(expr.rhs)
                    } else {
                        val cpIndex = generator.intern((expr.rhs as IdentifierNode).identifierName)
                        generator.add(LdaConstant(cpIndex))
                    }

                    val deleteOp = if (node.scope.isStrict) ::DeletePropertyStrict else ::DeletePropertySloppy
                    generator.add(deleteOp(target))
                }
            }

            return
        }

        visit(node.expression)

        when (node.op) {
            UnaryOperator.Void -> generator.add(LdaUndefined)
            UnaryOperator.Typeof -> generator.add(TypeOf)
            UnaryOperator.Plus -> TODO()
            UnaryOperator.Minus -> generator.add(Negate)
            UnaryOperator.BitwiseNot -> generator.add(BitwiseNot)
            UnaryOperator.Not -> generator.add(ToBooleanLogicalNot)
            else -> unreachable()
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        val op = if (node.isIncrement) Inc else Dec

        fun postfixGuard(action: () -> Unit) {
            val originalValue = if (node.isPostfix) {
                generator.reserveRegister().also {
                    generator.add(Star(it))
                }
            } else -1

            action()

            if (node.isPostfix)
                generator.add(Ldar(originalValue))
        }

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visit(target)
                generator.add(ToNumeric)

                postfixGuard {
                    generator.add(op)
                    storeVariable(target.targetVar, target.scope)
                }
            }
            is MemberExpressionNode -> {
                val objectReg = generator.reserveRegister()
                visit(target.lhs)
                generator.add(Star(objectReg))

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = generator.reserveRegister()
                        visit(target.rhs)
                        generator.add(Star(keyReg))
                        generator.add(LdaKeyedProperty(objectReg))
                        postfixGuard {
                            generator.add(ToNumeric)
                            generator.add(op)
                            generator.add(StaKeyedProperty(objectReg, keyReg))
                        }
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        val nameIndex = generator.intern((target.rhs as IdentifierNode).identifierName)
                        generator.add(LdaNamedProperty(objectReg, nameIndex))
                        postfixGuard {
                            generator.add(ToNumeric)
                            generator.add(op)
                            generator.add(StaNamedProperty(objectReg, nameIndex))
                        }
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }
            }
            else -> TODO()
        }
    }

    override fun visitAssignmentExpression(node: AssignmentExpressionNode) {
        val lhs = node.lhs
        val rhs = node.rhs

        expect(node.op == null || node.op.isAssignable)

        fun loadRhsIntoAcc() {
            if (node.op != null) {
                // First figure out the new value
                visitBinaryExpression(BinaryExpressionNode(lhs, rhs, node.op))
            } else {
                visit(rhs)
            }
        }

        when (lhs) {
            is IdentifierReferenceNode -> {
                if (checkForConstReassignment(lhs))
                    return

                loadRhsIntoAcc()
                storeVariable(lhs.targetVar, lhs.scope)

                return
            }
            is MemberExpressionNode -> {
                val objectReg = generator.reserveRegister()
                visit(lhs.lhs)
                generator.add(Star(objectReg))

                when (lhs.type) {
                    MemberExpressionNode.Type.Computed -> {
                        val keyReg = generator.reserveRegister()
                        visit(lhs.rhs)
                        generator.add(Star(keyReg))
                        loadRhsIntoAcc()
                        generator.add(StaKeyedProperty(objectReg, keyReg))
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        loadRhsIntoAcc()
                        val nameIndex = generator.intern((lhs.rhs as IdentifierNode).identifierName)
                        generator.add(StaNamedProperty(objectReg, nameIndex))
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }
            }
            else -> TODO()
        }
    }

    private fun loadVariable(variable: Variable, currentScope: Scope) {
        if (variable.mode == Variable.Mode.Global) {
            if (variable.name == "undefined") {
                generator.add(LdaUndefined)
            } else generator.add(LdaGlobal(generator.intern(variable.name)))
        } else loadEnvVariableRef(variable, currentScope)
    }

    private fun storeVariable(variable: Variable, currentScope: Scope) {
        if (variable.mode == Variable.Mode.Global) {
            generator.add(StaGlobal(generator.intern(variable.name)))
        } else storeEnvVariableRef(variable, currentScope)
    }

    private fun loadEnvVariableRef(variable: Variable, currentScope: Scope) {
        val name = generator.intern(variable.name)
        val distance = currentScope.envDistanceFrom(variable.source.scope)
        if (distance == 0) {
            generator.add(LdaCurrentEnv(name))
        } else {
            generator.add(LdaEnv(name, distance))
        }
    }

    private fun storeEnvVariableRef(variable: Variable, currentScope: Scope) {
        val name = generator.intern(variable.name)
        val distance = currentScope.envDistanceFrom(variable.source.scope)
        if (distance == 0) {
            generator.add(StaCurrentEnv(name))
        } else {
            generator.add(StaEnv(name, distance))
        }
    }

    private fun checkForConstReassignment(node: VariableRefNode): Boolean {
        return if (node.targetVar.type == Variable.Type.Const) {
            generator.add(ThrowConstReassignment(generator.intern(node.targetVar.name)))
            true
        } else false
    }

    override fun visitAwaitExpression(node: AwaitExpressionNode) {
        TODO()
    }

    override fun visitArgument(node: ArgumentNode) {
        if (node.isSpread)
            TODO()
        visit(node.expression)
    }

    override fun visitCommaExpression(node: CommaExpressionNode) {
        node.expressions.forEach(::visit)
    }

    override fun visitConditionalExpression(node: ConditionalExpressionNode) {
        visit(node.predicate)
        generator.add(ToBoolean)
        generator.ifElseHelper(::JumpIfTrue, {
            visit(node.ifTrue)
        }, {
            visit(node.ifFalse)
        })
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        // TODO: Deal with assigning to a MemberExpression

        val objectReg = generator.reserveRegister()
        visit(node.lhs)
        generator.add(Star(objectReg))

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visit(node.rhs)
                generator.add(LdaKeyedProperty(objectReg))
            }
            MemberExpressionNode.Type.NonComputed -> {
                val cpIndex = generator.intern((node.rhs as IdentifierNode).identifierName)
                generator.add(LdaNamedProperty(objectReg, cpIndex))
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }
    }

    private fun requiredRegisters(arguments: ArgumentList): Int {
        return if (argumentsMode(arguments) == ArgumentsMode.Spread) 1 else arguments.size
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        val callableReg = generator.reserveRegister()
        val receiverReg = generator.reserveRegister()

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visit(target)
                generator.add(Star(callableReg))
                generator.add(LdaUndefined)
                generator.add(Star(receiverReg))
            }
            is MemberExpressionNode -> {
                visit(target.lhs)
                generator.add(Star(receiverReg))

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        visit(target.rhs)
                        generator.add(LdaKeyedProperty(receiverReg))
                    }
                    MemberExpressionNode.Type.NonComputed -> {
                        val cpIndex = generator.intern((target.rhs as IdentifierNode).identifierName)
                        generator.add(LdaNamedProperty(receiverReg, cpIndex))
                    }
                    MemberExpressionNode.Type.Tagged -> TODO()
                }

                generator.add(Star(callableReg))
            }
        }

        val (argumentsMode, argumentRegisters) = loadArguments(node.arguments)

        if (argumentsMode == ArgumentsMode.Normal) {
            generator.add(Call(callableReg, receiverReg, argumentRegisters))
        } else {
            expect(argumentRegisters.size == 1)
            generator.add(CallWithArgArray(callableReg, receiverReg, argumentRegisters[0]))
        }
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        val target = generator.reserveRegister()
        visit(node.target)
        generator.add(Star(target))

        val (argumentsMode, argumentRegisters) = loadArguments(node.arguments)

        // TODO: Proper new.target
        if (argumentsMode == ArgumentsMode.Normal) {
            generator.add(Construct(target, target, argumentRegisters))
        } else {
            expect(argumentRegisters.size == 1)
            generator.add(ConstructWithArgArray(target, target, argumentRegisters[0]))
        }
    }

    enum class ArgumentsMode {
        Normal,
        Spread,
    }

    private fun argumentsMode(arguments: ArgumentList): ArgumentsMode {
        return if (arguments.any { it.isSpread }) {
            ArgumentsMode.Spread
        } else ArgumentsMode.Normal
    }

    private fun loadArguments(arguments: ArgumentList): Pair<ArgumentsMode, List<Register>> {
        val mode = argumentsMode(arguments)
        val registers = mutableListOf<Register>()

        return if (mode == ArgumentsMode.Spread) {
            return ArgumentsMode.Spread to listOf(loadArgumentsWithSpread(arguments))
        } else {
            for (argument in arguments) {
                visit(argument.expression)
                val argReg = generator.reserveRegister()
                registers.add(argReg)
                generator.add(Star(argReg))
            }

            ArgumentsMode.Normal to registers
        }
    }

    private fun loadArgumentsWithSpread(arguments: ArgumentList): Register {
        val arrayReg = generator.reserveRegister()
        generator.add(CreateArray)
        generator.add(Star(arrayReg))
        var indexReg = -1
        var indexUsable = true

        for ((index, argument) in arguments.withIndex()) {
            if (argument.isSpread) {
                indexUsable = false
                indexReg = generator.reserveRegister()
                generator.add(LdaInt(index))
                generator.add(Star(indexReg))

                visit(argument.expression)
                iterateValues {
                    generator.add(StaArray(arrayReg, indexReg))
                    generator.add(Ldar(indexReg))
                    generator.add(Inc)
                    generator.add(Star(indexReg))
                }
            } else {
                visit(argument.expression)
                if (indexUsable) {
                    generator.add(StaArrayIndex(arrayReg, index))
                } else {
                    generator.add(StaArray(arrayReg, indexReg))
                    generator.add(Ldar(indexReg))
                    generator.add(Inc)
                    generator.add(Star(indexReg))
                }
            }
        }

        return arrayReg
    }

    private fun iterateValues(action: () -> Unit) {
        generator.add(GetIterator)
        val iteratorReg = generator.reserveRegister()
        generator.add(Star(iteratorReg))

        val headBlock = generator.makeBlock()
        val isExhaustedBlock = generator.makeBlock()
        val isNotExhaustedBlock = generator.makeBlock()

        generator.add(Jump(headBlock))

        generator.currentBlock = headBlock
        val iteratorResultReg = generator.reserveRegister()
        generator.add(Ldar(iteratorReg))
        generator.add(IteratorNext)
        generator.add(Star(iteratorResultReg))
        generator.add(IteratorResultDone)
        generator.add(JumpIfTrue(isExhaustedBlock, isNotExhaustedBlock))

        generator.currentBlock = isNotExhaustedBlock
        generator.add(Ldar(iteratorResultReg))
        generator.add(IteratorResultValue)
        action()
        generator.add(Jump(headBlock))

        generator.currentBlock = isExhaustedBlock
    }

    override fun visitOptionalExpression(node: OptionalExpressionNode) {
        TODO()
    }

    override fun visitSuperPropertyExpression(node: SuperPropertyExpressionNode) {
        TODO()
    }

    override fun visitSuperCallExpression(node: SuperCallExpressionNode) {
        TODO()
    }

    override fun visitImportCallExpression(node: ImportCallExpressionNode) {
        TODO()
    }

    override fun visitYieldExpression(node: YieldExpressionNode) {
        TODO()
    }

    override fun visitParenthesizedExpression(node: ParenthesizedExpressionNode) {
        visit(node.expression)
    }

    override fun visitTemplateLiteral(node: TemplateLiteralNode) {
        val templateLiteral = generator.reserveRegister()

        for ((index, part) in node.parts.withIndex()) {
            if (part is StringLiteralNode) {
                val reg = generator.intern(part.value)
                generator.add(LdaConstant(reg))
            } else {
                visit(part)
                generator.add(ToString)
            }

            if (index != 0) {
                generator.add(StringAppend(templateLiteral))
            } else {
                generator.add(Star(templateLiteral))
            }
        }

        generator.add(Ldar(templateLiteral))
    }

    override fun visitRegExpLiteral(node: RegExpLiteralNode) {
        TODO()
    }

    override fun visitImportMetaExpression() {
        TODO()
    }

    override fun visitNewTargetExpression() {
        TODO()
    }

    override fun visitArrayLiteral(node: ArrayLiteralNode) {
        generator.add(CreateArray)
        val arrayReg = generator.reserveRegister()
        generator.add(Star(arrayReg))
        for ((index, element) in node.elements.withIndex()) {
            when (element.type) {
                ArrayElementNode.Type.Elision -> continue
                ArrayElementNode.Type.Spread -> TODO()
                ArrayElementNode.Type.Normal -> {
                    visit(element.expression!!)
                    generator.add(StaArrayIndex(arrayReg, index))
                }
            }
        }
        generator.add(Ldar(arrayReg))
    }

    override fun visitObjectLiteral(node: ObjectLiteralNode) {
        generator.add(CreateObject)
        val objectReg = generator.reserveRegister()
        generator.add(Star(objectReg))

        for (property in node.list) {
            when (property) {
                is SpreadProperty -> TODO()
                is MethodProperty -> {
                    val method = property.method

                    fun makeFunction() {
                        // TODO: This probably isn't correct
                        val functionNode = FunctionExpressionNode(
                            null,
                            method.parameters,
                            method.body,
                            method.parameterScope,
                            method.bodyScope,
                        )
                        functionNode.scope = method.scope
                        visitFunctionExpression(functionNode)
                    }

                    when (method.type) {
                        MethodDefinitionNode.Type.Normal -> storeObjectProperty(
                            objectReg,
                            method.propName,
                            ::makeFunction
                        )
                        MethodDefinitionNode.Type.Getter, MethodDefinitionNode.Type.Setter -> {
                            val propertyReg = generator.reserveRegister()
                            val methodReg = generator.reserveRegister()
                            val op = if (method.type == MethodDefinitionNode.Type.Getter) {
                                ::DefineGetterProperty
                            } else ::DefineSetterProperty

                            visitPropertyName(method.propName)
                            generator.add(Star(propertyReg))

                            makeFunction()
                            generator.add(Star(methodReg))

                            generator.add(op(objectReg, propertyReg, methodReg))
                        }
                        else -> TODO()
                    }
                }
                is ShorthandProperty -> {
                    visit(property.key)
                    generator.add(StaNamedProperty(objectReg, generator.intern(property.key.identifierName)))
                }
                is KeyValueProperty -> {
                    storeObjectProperty(objectReg, property.key) {
                        visit(property.value)
                    }
                }
            }
        }

        generator.add(Ldar(objectReg))
    }

    private fun visitPropertyName(property: PropertyName) {
        if (property.type == PropertyName.Type.Identifier) {
            val nameIndex = generator.intern((property.expression as IdentifierNode).identifierName)
            generator.add(LdaConstant(nameIndex))
        } else visit(property.expression)
    }

    private fun storeObjectProperty(objectReg: Int, property: PropertyName, valueProducer: () -> Unit) {
        if (property.type == PropertyName.Type.Identifier) {
            valueProducer()
            val name = (property.expression as IdentifierNode).identifierName
            generator.add(StaNamedProperty(objectReg, generator.intern(name)))
            return
        }

        when (property.type) {
            PropertyName.Type.String -> visit(property.expression)
            PropertyName.Type.Number -> visit(property.expression)
            PropertyName.Type.Computed -> {
                visit(property.expression)
                generator.add(ToString)
            }
            PropertyName.Type.Identifier -> unreachable()
        }

        val keyReg = generator.reserveRegister()
        generator.add(Star(keyReg))
        valueProducer()
        generator.add(StaKeyedProperty(objectReg, keyReg))
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        generator.add(if (node.value) LdaTrue else LdaFalse)
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        generator.add(LdaConstant(generator.intern(node.value)))
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        val value = node.value
        if (value.isFinite() && floor(value) == value && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            generator.add(LdaInt(value.toInt()))
        } else {
            generator.add(LdaConstant(generator.intern(value)))
        }
    }

    override fun visitBigIntLiteral(node: BigIntLiteralNode) {
        val bigint = BigInteger(node.value, node.type.radix)
        generator.add(LdaConstant(generator.intern(bigint)))
    }

    override fun visitNullLiteral() {
        generator.add(LdaNull)
    }

    override fun visitThisLiteral() {
        TODO()
    }
}
