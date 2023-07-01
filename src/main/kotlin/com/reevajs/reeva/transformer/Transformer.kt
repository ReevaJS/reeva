package com.reevajs.reeva.transformer

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.*
import com.reevajs.reeva.ast.literals.*
import com.reevajs.reeva.ast.statements.*
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.parsing.*
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable
import java.math.BigInteger

class Transformer(val parsedSource: ParsedSource) : AstVisitor {
    private lateinit var builder: IRBuilder
    private var currentScope: Scope? = null

    private val controlFlowScopes = mutableListOf<ControlFlowScope>()

    fun transform(isEval: Boolean = false): TransformedSource {
        expect(!::builder.isInitialized, "Cannot reuse a Transformer")

        val rootNode = parsedSource.node
        builder = IRBuilder(RESERVED_LOCALS_COUNT, 0, rootNode.scope.isStrict)

        globalDeclarationInstantiation(rootNode.scope as HoistingScope, isEval) {
            rootNode.children.forEach { it.accept(this) }

            if (!builder.activeBlockReturns()) {
                if (!builder.removeLastOpcodeIfPop())
                    +PushUndefined
                +Return
            }
        }

        return TransformedSource(
            parsedSource.sourceInfo,
            FunctionInfo(
                parsedSource.sourceInfo.name,
                builder.build(),
                rootNode.scope.isStrict,
                0,
                isTopLevel = true,
                isGenerator = false,
                isArrow = false,
            )
        )
    }

    private fun enterScope(scope: Scope) {
        currentScope = scope
        +PushDeclarativeEnvRecord
    }

    private fun exitScope(scope: Scope) {
        currentScope = scope.outer

        if (!builder.activeBlockIsTerminated())
            +PopEnvRecord
    }

    private fun buildIRScope(scope: Scope): IRScope {
        val sources = scope.variableSources.filter { it.mode != VariableMode.Import }.toMutableList()
        if (scope is HoistingScope)
            sources.addAll(scope.hoistedVariables)

        val (varSources, lexSources) = sources.partition { it.type == VariableType.Var }

        return IRScope(
            varSources.map { VarName(it.name(), it is FunctionDeclarationNode) },
            lexSources.map { LexName(it.name(), it.type == VariableType.Const) },
        )
    }

    private fun globalDeclarationInstantiation(scope: HoistingScope, isEval: Boolean, block: () -> Unit) {
        currentScope = scope

        val irScope = buildIRScope(scope)
        if (scope is GlobalScope) {
            +GlobalDeclarationInstantiation(irScope)
        } else {
            require(scope is ModuleScope)
            +ModuleEnvironmentInitialization(irScope)
        }

        val functions = scope.variableSources
            .filterIsInstance<FunctionDeclarationNode>()
            .asReversed()
            .distinctBy { it.name() }
            .asReversed()

        for (function in functions) {
            functionDeclarationInstantiation(function)
            if (scope is GlobalScope) {
                +DeclareGlobalFunc(function.identifier!!.processedName)
            } else {
                +DeclareModuleFunc(function.identifier!!.processedName)
            }
        }

        block()
    }

    private fun functionDeclarationInstantiation(
        func: GenericFunctionNode,
        name: String = func.name(),
        instantiate: Boolean = true,
        classConstructorKind: JSFunction.ConstructorKind? = null,
    ): FunctionInfo {
        val functionScope = func.functionScope
        val body = func.body
        val bodyScope = (body as? NodeWithScope)?.scope ?: functionScope
        require(functionScope is FunctionScope)

        val strict = functionScope.isStrict
        val hasParameterExpressions = func.parameters.containsExpressions()

        val (varSources, lexSources) = functionScope.variableSources.partition { it.type == VariableType.Var }
        val varNames = varSources.filter { it !is GenericFunctionNode }.mapTo(mutableSetOf()) { it.name() }
        val funcNames = varSources.filter { it is GenericFunctionNode }.mapTo(mutableSetOf()) { it.name() }
        val lexNames = lexSources.mapTo(mutableSetOf()) { it.name() }

        val parameterNames = func.parameters.boundNames()

        val argumentsObjectNeeded = when {
            func is ArrowFunctionNode -> false
            "arguments" in parameterNames -> false
            !hasParameterExpressions -> "arguments" !in funcNames && "arguments" !in lexNames
            else -> true
        }

        val argumentsMode = if (argumentsObjectNeeded) {
            if (functionScope.isStrict || !func.parameters.isSimple()) {
                HoistingScope.ArgumentsMode.Unmapped
            } else HoistingScope.ArgumentsMode.Mapped
        } else HoistingScope.ArgumentsMode.None

        val parameterBindings = if (argumentsMode != HoistingScope.ArgumentsMode.None) {
            parameterNames + "arguments"
        } else parameterNames

        val prevBuilder = builder
        builder = IRBuilder(
            func.parameters.parameters.size + RESERVED_LOCALS_COUNT,
            functionScope.inlineableLocalCount,
            strict,
        )
        enterScope(functionScope)

        if (parameterNames.isNotEmpty() || argumentsObjectNeeded)
            +InitializeFunctionParameters(parameterNames, argumentsMode)

        // Assign parameter values
        func.parameters.parameters.forEachIndexed { index, param ->
            val local = Local(RESERVED_LOCALS_COUNT + index)

            when (param) {
                is SimpleParameter -> {
                    if (param.initializer != null) {
                        +LoadValue(local)
                        +Dup

                        val ifUndefinedBlock = builder.makeBlock("Parameter${index}Undefined")
                        val continuationBlock = builder.makeBlock("Parameter${index}Continuation")

                        +JumpIfUndefined(ifUndefinedBlock, continuationBlock)
                        builder.enterBlock(ifUndefinedBlock)
                        +Pop
                        param.initializer.accept(this)
                        +Jump(continuationBlock)
                        builder.enterBlock(continuationBlock)
                        storeToSource(param)
                    } else {
                        +LoadValue(local)
                        storeToSource(param)
                    }
                }
                is BindingParameter -> {
                    if (param.initializer != null) {
                        +LoadValue(local)

                        val ifUndefinedBlock = builder.makeBlock("Parameter${index}Undefined")
                        val continuationBlock = builder.makeBlock("Parameter${index}Continuation")

                        +JumpIfUndefined(ifUndefinedBlock, continuationBlock)
                        builder.enterBlock(ifUndefinedBlock)
                        param.initializer.accept(this)
                        +StoreValue(local)
                        +Jump(continuationBlock)
                        builder.enterBlock(continuationBlock)
                    }
                    assign(param.pattern, local)
                }
                is RestParameter -> {
                    +CollectRestArgs
                    assign(param.declaration.node)
                }
            }
        }

        if (bodyScope !== functionScope)
            enterScope(bodyScope)

        // Initialize var bindings
        val funcVarBindings = mutableListOf<VarBinding>()

        val instantiatedVarNames = parameterBindings.toMutableSet()
        for (varName in varNames) {
            if (varName !in instantiatedVarNames) {
                instantiatedVarNames.add(varName)
                funcVarBindings.add(VarBinding(varName, false))
            }
        }

        if (funcVarBindings.isNotEmpty())
            +InitializeFunctionVarBindings(funcVarBindings)

        // Initialize lex bindings
        val lexBindings = mutableListOf<LexBinding>()

        for (lexSource in lexSources)
            lexBindings.add(LexBinding(lexSource.name(), lexSource.type == VariableType.Const))

        if (lexBindings.isNotEmpty())
            +InitializeLexBindings(lexBindings)

        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()
        val functionNames = mutableSetOf<String>()
        for (varSource in varSources.asReversed()) {
            if (varSource !is FunctionDeclarationNode || varSource.name() in functionNames)
                continue

            functionNames.add(varSource.name())

            if (varSource !in functionScope.hoistedVariables)
                functionsToInitialize.add(0, varSource)
        }

        for (function in functionsToInitialize) {
            functionDeclarationInstantiation(function)
            storeToSource(function)
        }

        if (body is BlockNode) {
            body.statements.forEach { it.accept(this) }
        } else {
            expect(func is ArrowFunctionNode)
            body.accept(this)
            +Return
        }

        if (classConstructorKind == JSFunction.ConstructorKind.Derived) {
            expect(body is BlockNode)
            // TODO: Check to see if this is redundant
            +LoadValue(RECEIVER_LOCAL)
            +ThrowSuperNotInitializedIfEmpty
        }

        if (body is BlockNode) {
            +PushUndefined
            +Return
        }

        if (bodyScope !== functionScope)
            exitScope(bodyScope)
        exitScope(functionScope)

        val functionInfo = FunctionInfo(
            name,
            builder.build(),
            functionScope.isStrict,
            func.parameters.expectedArgumentCount(),
            isTopLevel = false,
            isGenerator = func.kind.isGenerator,
            isArrow = func is ArrowFunctionNode,
        )

        builder = prevBuilder

        if (instantiate) {
            when {
                classConstructorKind != null -> +CreateMethod(functionInfo)
                func.kind.isGenerator && func.kind.isAsync -> +CreateAsyncGeneratorClosure(functionInfo)
                func.kind.isGenerator -> +CreateGeneratorClosure(functionInfo)
                func.kind.isAsync -> +CreateAsyncClosure(functionInfo)
                else -> +CreateClosure(functionInfo)
            }
        }

        builder.addNestedFunction(functionInfo)
        return functionInfo
    }

    override fun visit(node: FunctionDeclarationNode) {
        // nop
    }

    override fun visit(node: BlockNode) {
        visitBlock(node)
    }

    private fun visitBlock(node: BlockNode, preEvaluationCallback: () -> Unit = {}) {
        enterScope(node.scope)

        try {
            val continuationBlock = if (node.labels.isNotEmpty()) {
                val continuationBlock = builder.makeBlock("BlockContinuation")
                enterControlFlowScope(node.labels, continuationBlock, null)
                continuationBlock
            } else null

            blockDeclarationInstantiation(node.scope)

            preEvaluationCallback()
            node.statements.forEach { it.accept(this) }

            if (continuationBlock != null) {
                exitControlFlowScope()
                +Jump(continuationBlock)
                builder.enterBlock(continuationBlock)
            }
        } finally {
            exitScope(node.scope)
        }
    }

    private fun blockDeclarationInstantiation(scope: Scope) {
        val lexDecls = scope.variableSources.filter { it.type != VariableType.Var }
        val funcDecls = scope.variableSources.filterIsInstance<GenericFunctionNode>()
        val lexBindings = mutableListOf<LexBinding>()

        for (decl in lexDecls)
            lexBindings.add(LexBinding(decl.name(), decl.type == VariableType.Const))

        if (lexBindings.isNotEmpty())
            +InitializeLexBindings(lexBindings)

        for (decl in funcDecls) {
            functionDeclarationInstantiation(decl)
            storeToSource(decl as VariableSourceNode)
        }
    }

    private fun loadFromSource(source: VariableSourceNode) {
        if (source is Import) {
            +LoadModuleVar(source.name())
            return
        }

        if (source.mode == VariableMode.Global) {
            if (source.name() == "undefined") {
                +PushUndefined
            } else {
                expect(source.type == VariableType.Var)
                +LoadGlobal(source.name())
            }

            return
        }

        val distance = currentScope!!.envDistanceFrom(source.scope)
        val name = source.name()

        if (distance == 0) {
            +LoadCurrentEnvName(name)
        } else +LoadEnvName(name, distance)
    }

    private fun storeToSource(source: VariableSourceNode) {
        if (source.mode == VariableMode.Global) {
            if (source.name() == "undefined") {
                if (source.scope.isStrict) {
                    +ThrowConstantReassignmentError("undefined")
                } else return
            } else {
                expect(source.type == VariableType.Var)
                if (source is GenericFunctionNode) {
                    +DeclareGlobalFunc(source.name())
                } else {
                    +StoreGlobal(source.name())
                }
            }

            return
        }

        val distance = currentScope!!.envDistanceFrom(source.scope)
        val name = source.name()

        if (source.type != VariableType.Var && !source.isInitialized) {
            +InitializeEnvName(name, distance)
            source.isInitialized = true
        } else {
            if (distance == 0) {
                +StoreCurrentEnvName(name)
            } else +StoreEnvName(name, distance)
        }
    }

    override fun visit(node: ExpressionStatementNode) {
        node.node.accept(this)
        +Pop
    }

    override fun visit(node: IfStatementNode) {
        node.condition.accept(this)

        val trueBlock = builder.makeBlock("IfTrue")
        val falseBlock = if (node.falseBlock != null) builder.makeBlock("IfFalse") else null
        val continuationBlock = builder.makeBlock("IfContinuation")

        +JumpIfToBooleanTrue(trueBlock, falseBlock ?: continuationBlock)

        builder.enterBlock(trueBlock)
        node.trueBlock.accept(this)
        +Jump(continuationBlock)

        if (node.falseBlock != null) {
            builder.enterBlock(falseBlock!!)
            node.falseBlock.accept(this)
            +Jump(continuationBlock)
        }

        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: WhileStatementNode) {
        val conditionBlock = builder.makeBlock("WhileCondition")
        val bodyBlock = builder.makeBlock("WhileBody")
        val continuationBlock = builder.makeBlock("WhileContinuation")

        +Jump(conditionBlock)
        builder.enterBlock(conditionBlock)
        node.condition.accept(this)
        +JumpIfToBooleanTrue(bodyBlock, continuationBlock)

        builder.enterBlock(bodyBlock)
        enterControlFlowScope(node.labels, continuationBlock, conditionBlock)
        node.body.accept(this)
        exitControlFlowScope()
        +Jump(conditionBlock)

        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: DoWhileStatementNode) {
        val conditionBlock = builder.makeBlock("DoWhileCondition")
        val bodyBlock = builder.makeBlock("DoWhileBody")
        val continuationBlock = builder.makeBlock("DoWhileContinuation")

        +Jump(bodyBlock)

        builder.enterBlock(bodyBlock)
        enterControlFlowScope(node.labels, continuationBlock, conditionBlock)
        node.body.accept(this)
        exitControlFlowScope()
        +Jump(conditionBlock)

        builder.enterBlock(conditionBlock)
        node.condition.accept(this)
        +JumpIfToBooleanTrue(bodyBlock, continuationBlock)

        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: ForStatementNode) {
        node.initializerScope?.also {
            enterScope(it)
            blockDeclarationInstantiation(it)
        }
        node.initializer?.accept(this)

        val conditionBlock = builder.makeBlock("ForCondition")
        val bodyBlock = builder.makeBlock("ForBody")
        val incrementBlock = if (node.incrementer != null) builder.makeBlock("ForIncrement") else null
        val continuationBlock = builder.makeBlock("ForContinuation")

        +Jump(conditionBlock)
        builder.enterBlock(conditionBlock)

        enterControlFlowScope(node.labels, continuationBlock, incrementBlock ?: conditionBlock)

        if (node.condition != null) {
            node.condition.accept(this)
            +JumpIfToBooleanTrue(bodyBlock, continuationBlock)
        } else {
            +Jump(bodyBlock)
        }

        builder.enterBlock(bodyBlock)
        node.body.accept(this)

        exitControlFlowScope()

        if (node.incrementer != null) {
            +Jump(incrementBlock!!)
            builder.enterBlock(incrementBlock)
            node.incrementer.accept(this)
            +Pop
            +Jump(conditionBlock)
        } else {
            +Jump(continuationBlock)
        }

        builder.enterBlock(continuationBlock)

        node.initializerScope?.also(::exitScope)
    }

    override fun visit(node: SwitchStatementNode) {
        node.target.accept(this)
        val target = builder.newLocalSlot(LocalKind.Value)
        +StoreValue(target)

        data class ClauseWithBlocks(val clause: SwitchClause, val testBlock: BlockIndex?, val bodyBlock: BlockIndex?)

        var defaultClause: ClauseWithBlocks? = null

        // We make all blocks up front to keep them ordered, which will ensure they are printed
        // (when debugging) in a logical order
        val clauses = node.clauses.mapIndexedNotNull { index, clause ->
            if (clause.target == null) {
                defaultClause = ClauseWithBlocks(
                    clause,
                    null,
                    if (clause.body != null) builder.makeBlock("SwitchBodyDefault") else null
                )
                null
            } else {
                ClauseWithBlocks(
                    clause,
                    builder.makeBlock("SwitchTest$index"),
                    if (clause.body != null) builder.makeBlock("SwitchBody$index") else null
                )
            }
        }

        val continuationBlock = builder.makeBlock("SwitchContinuation")

        if (clauses.isNotEmpty()) {
            +Jump(clauses[0].testBlock!!)

            for ((index, clause) in clauses.withIndex()) {
                builder.enterBlock(clause.testBlock!!)

                val nextTestBlock = clauses.drop(index + 1).map { it.testBlock!! }.firstOrNull()
                val nextBodyBlock = clauses.drop(index + 1).firstNotNullOfOrNull { it.bodyBlock }

                if (clause.bodyBlock == null) {
                    if (nextBodyBlock == null) {
                        // Evaluate the expression for side effects and jump to the default clause (or the
                        // continuation if there is no default
                        clause.clause.target!!.accept(this)
                        +Pop
                        +Jump(nextTestBlock ?: defaultClause?.bodyBlock ?: continuationBlock)
                    } else {
                        +LoadValue(target)
                        clause.clause.target!!.accept(this)
                        +TestEqualStrict

                        // nextTestBlock is guaranteed to be non-null since nextBodyBlock is not null
                        +JumpIfToBooleanTrue(nextBodyBlock, nextTestBlock!!)
                    }
                } else {
                    +LoadValue(target)
                    clause.clause.target!!.accept(this)
                    +TestEqualStrict
                    +JumpIfToBooleanTrue(clause.bodyBlock, nextTestBlock ?: defaultClause?.bodyBlock ?: continuationBlock)

                    builder.enterBlock(clause.bodyBlock)
                    enterControlFlowScope(clause.clause.labels, continuationBlock, null)
                    clause.clause.body!!.forEach { it.accept(this) }
                    exitControlFlowScope()
                    +Jump(continuationBlock)
                }
            }
        } else {
            +Jump(defaultClause?.bodyBlock ?: continuationBlock)
        }

        if (defaultClause != null) {
            builder.enterBlock(defaultClause!!.bodyBlock!!)
            enterControlFlowScope(defaultClause!!.clause.labels, continuationBlock, null)
            defaultClause!!.clause.body!!.forEach { it.accept(this) }
            exitControlFlowScope()
            +Jump(continuationBlock)
        }

        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: ForInNode) {
        val local = builder.newLocalSlot(LocalKind.Value)
        node.expression.accept(this)
        +Dup
        +StoreValue(local)

        val bodyBlock = builder.makeBlock("ForInBody")
        val continuationBlock = builder.makeBlock("ForInContinuation")

        +JumpIfUndefined(continuationBlock, bodyBlock)
        builder.enterBlock(bodyBlock)

        +LoadValue(local)
        +ForInEnumerate
        iterateForEach(node)

        +Jump(continuationBlock)
        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: ForOfNode) {
        node.expression.accept(this)
        +GetIterator
        iterateForEach(node)
    }

    private fun iterateForEach(node: ForEachNode) {
        val iteratorLocal = builder.newLocalSlot(LocalKind.Value)
        +StoreValue(iteratorLocal)

        iterateValues(node.labels, iteratorLocal) {
            node.initializerScope?.let {
                enterScope(it)
                blockDeclarationInstantiation(it)
            }
            when (val decl = node.decl) {
                is DeclarationNode -> assign(decl.declarations[0])
                else -> assign(decl)
            }
            node.body.accept(this)
            node.initializerScope?.also(::exitScope)
        }
    }

    private fun iterateValues(
        labels: Set<String>,
        iteratorLocal: Local,
        action: () -> Unit,
    ) {
        val iterationTestBlock = builder.makeBlock("IterationTestBlock")
        val iterationAdvanceBlock = builder.makeBlock("IterationAdvanceBlock")
        val continuationBlock = builder.makeBlock("IterationContinuation")

        +Jump(iterationTestBlock)
        builder.enterBlock(iterationTestBlock)

        val resultLocal = builder.newLocalSlot(LocalKind.Value)
        +LoadValue(iteratorLocal)
        +IteratorNext
        +Dup
        +StoreValue(resultLocal)
        +IteratorResultDone

        +JumpIfTrue(continuationBlock, iterationAdvanceBlock)
        builder.enterBlock(iterationAdvanceBlock)

        +LoadValue(resultLocal)
        +IteratorResultValue

        enterControlFlowScope(labels, continuationBlock, iterationTestBlock)
        action()
        exitControlFlowScope()
        +Jump(iterationTestBlock)

        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: BreakStatementNode) {
        // Guaranteed to succeed as the Parser catches invalid labels
        val breakableScope = if (node.label != null) {
            controlFlowScopes.asReversed().first { node.label in it.labels }
        } else controlFlowScopes.last()

        val continuationBlock = builder.makeBlock("BreakContinuation")
        +Jump(breakableScope.breakBlock)
        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: ContinueStatementNode) {
        // Guaranteed to succeed as Parser catches invalid labels
        val continuableScope = if (node.label != null) {
            controlFlowScopes.asReversed().first { node.label in it.labels }
        } else controlFlowScopes.last { it.continueBlock != null }

        val continuationBlock = builder.makeBlock("ContinueContinuation")
        +Jump(continuableScope.continueBlock!!)
        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: CommaExpressionNode) {
        for (expression in node.expressions.dropLast(1)) {
            expression.accept(this)
            +Pop
        }

        node.expressions.last().accept(this)
    }

    override fun visit(node: BinaryExpressionNode) {
        val op = when (node.operator) {
            BinaryOperator.Add -> Add
            BinaryOperator.Sub -> Sub
            BinaryOperator.Mul -> Mul
            BinaryOperator.Div -> Div
            BinaryOperator.Exp -> Exp
            BinaryOperator.Mod -> Mod
            BinaryOperator.BitwiseAnd -> BitwiseAnd
            BinaryOperator.BitwiseOr -> BitwiseOr
            BinaryOperator.BitwiseXor -> BitwiseXor
            BinaryOperator.Shl -> ShiftLeft
            BinaryOperator.Shr -> ShiftRight
            BinaryOperator.UShr -> ShiftRightUnsigned
            BinaryOperator.StrictEquals -> TestEqualStrict
            BinaryOperator.StrictNotEquals -> TestNotEqualStrict
            BinaryOperator.SloppyEquals -> TestEqual
            BinaryOperator.SloppyNotEquals -> TestNotEqual
            BinaryOperator.LessThan -> TestLessThan
            BinaryOperator.LessThanEquals -> TestLessThanOrEqual
            BinaryOperator.GreaterThan -> TestGreaterThan
            BinaryOperator.GreaterThanEquals -> TestGreaterThanOrEqual
            BinaryOperator.Instanceof -> TestInstanceOf
            BinaryOperator.In -> TestIn
            BinaryOperator.And -> {
                node.lhs.accept(this)
                +Dup

                val ifTrueBlock = builder.makeBlock("LogicalAndTrueBlock")
                val continuationBlock = builder.makeBlock("LogicalAndContinuation")

                +JumpIfToBooleanTrue(ifTrueBlock, continuationBlock)
                builder.enterBlock(ifTrueBlock)
                +Pop
                node.rhs.accept(this)
                +Jump(continuationBlock)

                builder.enterBlock(continuationBlock)
                return
            }

            BinaryOperator.Or -> {
                node.lhs.accept(this)
                +Dup

                val ifFalseBlock = builder.makeBlock("LogicalOrFalseBlock")
                val continuationBlock = builder.makeBlock("LogicalFalseContinuation")

                +JumpIfToBooleanTrue(continuationBlock, ifFalseBlock)
                builder.enterBlock(ifFalseBlock)
                +Pop
                node.rhs.accept(this)
                +Jump(continuationBlock)

                builder.enterBlock(continuationBlock)
                return
            }

            BinaryOperator.Coalesce -> {
                node.lhs.accept(this)
                +Dup

                val ifNullishBlock = builder.makeBlock("LogicalCoalesceNullishBlock")
                val continuationBlock = builder.makeBlock("LogicalNullishContinuation")

                +JumpIfNullish(ifNullishBlock, continuationBlock)
                builder.enterBlock(ifNullishBlock)
                +Pop
                node.rhs.accept(this)
                +Jump(continuationBlock)

                builder.enterBlock(continuationBlock)
                return
            }
        }

        node.lhs.accept(this)
        node.rhs.accept(this)
        +op
    }

    override fun visit(node: UnaryExpressionNode) {
        if (node.op == UnaryOperator.Delete) {
            when (val expr = node.expression) {
                is IdentifierReferenceNode -> +PushConstant(false)
                !is MemberExpressionNode -> +PushConstant(true)
                else -> if (expr.type == MemberExpressionNode.Type.Tagged) {
                    +PushConstant(true)
                } else {
                    expr.lhs.accept(this)

                    if (expr.type == MemberExpressionNode.Type.Computed) {
                        expr.rhs.accept(this)
                    } else {
                        +PushConstant((expr.rhs as IdentifierNode).processedName)
                    }

                    +DeleteProperty
                }
            }

            return
        }

        if (node.expression is IdentifierReferenceNode && node.op == UnaryOperator.Typeof &&
            node.expression.source.mode == VariableMode.Global
        ) {
            +TypeOfGlobal(node.expression.processedName)
            return
        }

        node.expression.accept(this)

        when (node.op) {
            UnaryOperator.Void -> {
                +Pop
                +PushUndefined
            }

            UnaryOperator.Typeof -> +TypeOf
            UnaryOperator.Plus -> +ToNumber
            UnaryOperator.Minus -> +Negate
            UnaryOperator.BitwiseNot -> +BitwiseNot
            UnaryOperator.Not -> +ToBooleanLogicalNot
            else -> unreachable()
        }
    }

    override fun visit(node: UpdateExpressionNode) {
        val op = if (node.isIncrement) Inc else Dec

        fun execute(duplicator: Opcode) {
            if (node.isPostfix) {
                +duplicator
                +op
            } else {
                +op
                +duplicator
            }
        }

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                target.accept(this)
                +ToNumeric
                execute(Dup)
                storeToSource(target.source)
            }

            is MemberExpressionNode -> {
                target.lhs.accept(this)
                +Dup
                // lhs lhs

                when (target.type) {
                    MemberExpressionNode.Type.Computed -> {
                        target.rhs.accept(this)
                        val tmp = builder.newLocalSlot(LocalKind.Value)
                        +Dup
                        +StoreValue(tmp)

                        // lhs lhs rhs
                        +LoadKeyedProperty
                        // lhs value
                        +ToNumeric
                        // lhs value
                        execute(DupX1)
                        // value lhs value
                        +LoadValue(tmp)
                        +Swap
                        // value lhs key value
                        +StoreKeyedProperty
                    }

                    MemberExpressionNode.Type.NonComputed -> {
                        val name = (target.rhs as IdentifierNode).processedName
                        +LoadNamedProperty(name)
                        execute(DupX1)
                        +StoreNamedProperty(name)
                    }

                    MemberExpressionNode.Type.Tagged -> TODO()
                }
            }

            else -> TODO()
        }
    }

    override fun visit(node: LexicalDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    override fun visit(node: VariableDeclarationNode) {
        node.declarations.forEach(::visitDeclaration)
    }

    private fun visitDeclaration(declaration: Declaration) {
        if (declaration.initializer != null) {
            declaration.initializer!!.accept(this)
        } else {
            +PushUndefined
        }

        when (declaration) {
            is NamedDeclaration -> assign(declaration)
            is DestructuringDeclaration -> assign(declaration.pattern)
        }
    }

    private fun assign(node: AstNode, bindingPatternLocal: Local? = null) {
        when (node) {
            is VariableSourceNode -> storeToSource(node)
            is BindingPatternNode -> {
                val valueLocal = bindingPatternLocal ?: builder.newLocalSlot(LocalKind.Value)
                if (bindingPatternLocal == null)
                    +StoreValue(valueLocal)
                assignToBindingPattern(node, valueLocal)
            }

            is DestructuringDeclaration -> assign(node.pattern)
            is IdentifierReferenceNode -> storeToSource(node.source)
            is ExpressionStatementNode -> assign(node.node, bindingPatternLocal)
            else -> TODO()
        }
    }

    private fun assignToBindingPattern(node: BindingPatternNode, valueLocal: Local) {
        when (node.kind) {
            BindingKind.Object -> assignToObjectBindingPattern(node, valueLocal)
            BindingKind.Array -> assignToArrayBindingPattern(node, valueLocal)
        }
    }

    private fun assignToObjectBindingPattern(node: BindingPatternNode, valueLocal: Local) {
        val properties = node.bindingProperties
        val hasRest = properties.lastOrNull() is BindingRestProperty
        val excludedPropertiesLocal = if (hasRest) builder.newLocalSlot(LocalKind.Value) else null

        if (hasRest) {
            +CreateArray
            +StoreValue(excludedPropertiesLocal!!)
        }

        for ((index, property) in properties.withIndex()) {
            val alias = when (property) {
                is BindingRestProperty -> {
                    +LoadValue(valueLocal)
                    +CopyObjectExcludingProperties(excludedPropertiesLocal!!)
                    storeToSource(property.declaration)
                    return
                }
                is SimpleBindingProperty -> {
                    val name = property.declaration.identifier.processedName
                    +LoadValue(valueLocal)
                    +LoadNamedProperty(name)

                    if (property.initializer != null) {
                        val ifUndefinedBlock = builder.makeBlock("SimpleBindingIfUndefined")
                        val continuationBlock = builder.makeBlock("SimpleBindingContinuation")

                        +Dup
                        +JumpIfUndefined(ifUndefinedBlock, continuationBlock)
                        builder.enterBlock(ifUndefinedBlock)
                        +Pop
                        property.initializer.accept(this)
                        +Jump(continuationBlock)
                        builder.enterBlock(continuationBlock)
                    }

                    if (hasRest) {
                        +PushConstant(name)
                        +StoreArrayIndexed(excludedPropertiesLocal!!, index)
                    }

                    property.alias ?: BindingDeclarationOrPattern(property.declaration)
                }
                is ComputedBindingProperty -> {
                    expect(property.name.type != PropertyName.Type.Identifier)

                    +LoadValue(valueLocal)
                    property.name.expression.accept(this)
                    +LoadKeyedProperty

                    if (hasRest) {
                        +Dup
                        +StoreArrayIndexed(excludedPropertiesLocal!!, index)
                    }

                    if (property.initializer != null) {
                        val ifNotUndefinedBlock = builder.makeBlock("ComputedBindingIfNotUndefined")
                        val continuationBlock = builder.makeBlock("ComputedBindingContinuation")

                        +JumpIfUndefined(continuationBlock, ifNotUndefinedBlock)
                        builder.enterBlock(ifNotUndefinedBlock)
                        property.initializer.accept(this)
                        +Jump(continuationBlock)
                        builder.enterBlock(continuationBlock)
                    }

                    property.alias
                }
            }

            assign(alias.node)
        }
    }

    private fun assignToArrayBindingPattern(node: BindingPatternNode, valueLocal: Local) {
        val isExhaustedLocal = builder.newLocalSlot(LocalKind.Boolean)

        +LoadValue(valueLocal)
        +GetIterator
        // iter

        fun exhaustionHelper(ifExhausted: () -> Unit, ifNotExhausted: () -> Unit) {
            val ifExhaustedBlock = builder.makeBlock("IfExhaustedBlock")
            val ifNotExhaustedBlock = builder.makeBlock("IfNotExhaustedBlock")
            val continuationBlock = builder.makeBlock("ExhaustionContinuation")

            +LoadBoolean(isExhaustedLocal)
            +JumpIfTrue(ifExhaustedBlock, ifNotExhaustedBlock)

            builder.enterBlock(ifExhaustedBlock)
            ifExhausted()
            +Jump(continuationBlock)

            builder.enterBlock(ifNotExhaustedBlock)
            ifNotExhausted()
            +Jump(continuationBlock)

            builder.enterBlock(continuationBlock)
        }

        var first = true

        for (element in node.bindingElements) {
            // iter
            if (element is BindingRestElement) {
                val iterLocal = builder.newLocalSlot(LocalKind.Value)
                +StoreValue(iterLocal)

                if (first) {
                    // The iterator hasn't been called, so we can skip the exhaustion check
                    iteratorToArray(iterLocal)
                } else {
                    exhaustionHelper({
                        +CreateArray
                    }, {
                        iteratorToArray(iterLocal)
                    })
                }

                assign(element.declaration.node)
                return
            }

            // iter

            // If this is the first iteration, we haven't called the iterator yet. We have
            // to call it at least once
            if (first) {
                // iter
                +Dup
                +IteratorNext
                // iter result

                if (node.bindingElements.size > 1) {
                    // If we only have one element, we don't need to store whether the iterator
                    // is exhausted
                    +Dup
                    +IteratorResultDone
                    // iter result isDone

                    // We don't need another if-else here on the isDone status, since if the
                    // iterator _is_ done, the result will just be undefined anyways
                    +StoreBoolean(isExhaustedLocal)
                }

                +IteratorResultValue
            } else {
                exhaustionHelper({
                    +PushUndefined
                }, {
                    +Dup
                    +IteratorNext
                    +Dup
                    +IteratorResultDone

                    // We don't need another if-else here on the isDone status, since if the
                    // iterator _is_ done, the result will just be undefined anyways
                    +StoreBoolean(isExhaustedLocal)

                    +IteratorResultValue
                })
            }

            if (element is SimpleBindingElement) {
                assign(element.alias.node)
            } else if (element is BindingElisionElement) {
                +Pop
            }

            first = false
        }

        +Pop
    }

    private fun iteratorToArray(iteratorLocal: Local, arrayLocal: Local = builder.newLocalSlot(LocalKind.Value)) {
        val indexLocal = builder.newLocalSlot(LocalKind.Int)
        +PushJVMInt(0)
        +StoreInt(indexLocal)

        +CreateArray
        +StoreValue(arrayLocal)

        iterateValues(setOf(), iteratorLocal) {
            +StoreArray(arrayLocal, indexLocal)
        }

        +LoadValue(arrayLocal)
    }

    override fun visit(node: AssignmentExpressionNode) {
        val lhs = node.lhs
        val rhs = node.rhs

        expect(node.op == null || node.op.isAssignable)

        fun pushRhs() {
            if (node.op != null) {
                // First figure out the new value
                BinaryExpressionNode(lhs, rhs, node.op, node.sourceLocation).accept(this)
            } else {
                rhs.accept(this)
            }
        }

        when (lhs) {
            is IdentifierReferenceNode -> {
                if (checkForConstReassignment(lhs))
                    return

                pushRhs()
                +Dup
                storeToSource(lhs.source)
            }

            is MemberExpressionNode -> {
                lhs.lhs.accept(this)

                when (lhs.type) {
                    MemberExpressionNode.Type.Computed -> {
                        lhs.rhs.accept(this)
                        pushRhs()
                        // lhs rhs value
                        +DupX2
                        // value lhs rhs value
                        +StoreKeyedProperty
                    }

                    MemberExpressionNode.Type.NonComputed -> {
                        pushRhs()
                        +DupX1
                        +StoreNamedProperty((lhs.rhs as IdentifierNode).processedName)
                    }

                    MemberExpressionNode.Type.Tagged -> TODO()
                }
            }

            else -> TODO()
        }
    }

    private fun checkForConstReassignment(node: VariableRefNode): Boolean {
        return if (node.source.type == VariableType.Const) {
            +ThrowConstantReassignmentError(node.source.name())
            true
        } else false
    }

    override fun visit(node: MemberExpressionNode) {
        pushMemberExpression(node, pushReceiver = false)
    }

    private fun pushMemberExpression(node: MemberExpressionNode, pushReceiver: Boolean) {
        node.lhs.accept(this)

        if (pushReceiver)
            +Dup

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                node.rhs.accept(this)
                +LoadKeyedProperty
            }

            MemberExpressionNode.Type.NonComputed -> {
                +LoadNamedProperty((node.rhs as IdentifierNode).processedName)
            }

            MemberExpressionNode.Type.Tagged -> TODO()
        }

        if (pushReceiver)
            +Swap
    }

    override fun visit(node: OptionalChainNode) {
        pushOptionalChain(node, false)
    }

    private fun pushOptionalChain(node: OptionalChainNode, pushReceiver: Boolean) {
        val firstNeedsReceiver = node.parts[0] is OptionalCallChain

        if (node.base is MemberExpressionNode) {
            pushMemberExpression(node.base, firstNeedsReceiver)
        } else {
            node.base.accept(this)
            if (firstNeedsReceiver)
                +PushUndefined
        }

        var receiverLocal: Local? = null

        if (firstNeedsReceiver) {
            receiverLocal = builder.newLocalSlot(LocalKind.Value)
            +StoreValue(receiverLocal)
        }

        val ifNullishBlock = builder.makeBlock("OptionalChainIfNullish")
        val continuationBlock = builder.makeBlock("OptionalContinuation")

        for ((index, part) in node.parts.withIndex()) {
            if (part.isOptional) {
                +Dup

                val ifNotNullishBlock = builder.makeBlock("Optional${index}NotNullish")
                +JumpIfNullish(ifNullishBlock, ifNotNullishBlock)
                builder.enterBlock(ifNotNullishBlock)
            }

            val needsReceiver =
                (index < node.parts.lastIndex && node.parts[index + 1] is OptionalCallChain) ||
                    (index == node.parts.lastIndex && pushReceiver)

            if (needsReceiver) {
                if (receiverLocal == null)
                    receiverLocal = builder.newLocalSlot(LocalKind.Value)
                +Dup
                +StoreValue(receiverLocal)
            }

            when (part) {
                is OptionalAccessChain -> +LoadNamedProperty(part.identifier.processedName)
                is OptionalCallChain -> {
                    +LoadValue(receiverLocal!!)
                    if (pushArguments(part.arguments) == ArgumentsMode.Normal) {
                        +Call(part.arguments.size)
                    } else {
                        +CallArray
                    }
                }

                is OptionalComputedAccessChain -> {
                    part.expr.accept(this)
                    +LoadKeyedProperty
                }
            }
        }

        +Jump(continuationBlock)

        builder.enterBlock(ifNullishBlock)
        +Pop
        +PushUndefined
        +Jump(continuationBlock)

        builder.enterBlock(continuationBlock)

        if (pushReceiver)
            +LoadValue(receiverLocal!!)
    }

    override fun visit(node: ReturnStatementNode) {
        if (node.expression == null) {
            +PushUndefined
        } else {
            node.expression.accept(this)
        }

        +Return

        // Make another block for anything after the throw. This block will be eliminated by the
        // BlockOptimizer.
        builder.enterBlock(builder.makeBlock("ReturnContinuation"))
    }

    override fun visit(node: IdentifierReferenceNode) {
        loadFromSource(node.source)

        if (node.source.mode == VariableMode.Global || node.source.type == VariableType.Var)
            return

        // We need to check if the variable has been initialized
        +Dup
        +ThrowLexicalAccessErrorIfEmpty(node.processedName)
    }

    enum class ArgumentsMode {
        Spread,
        Normal,
    }

    private fun argumentsMode(arguments: List<ArgumentNode>): ArgumentsMode {
        return if (arguments.any { it.isSpread }) {
            ArgumentsMode.Spread
        } else ArgumentsMode.Normal
    }

    private fun pushArguments(arguments: List<ArgumentNode>): ArgumentsMode {
        val mode = argumentsMode(arguments)
        when (mode) {
            ArgumentsMode.Spread -> {
                val arrayLocal = builder.newLocalSlot(LocalKind.Value)
                val indexLocal = builder.newLocalSlot(LocalKind.Int)
                val iteratorLocal = builder.newLocalSlot(LocalKind.Value)

                +CreateArray
                +StoreValue(arrayLocal)

                +PushJVMInt(0)
                +StoreInt(indexLocal)

                for (argument in arguments) {
                    argument.expression.accept(this)

                    if (argument.isSpread) {
                        +GetIterator
                        +StoreValue(iteratorLocal)
                        iterateValues(setOf(), iteratorLocal) {
                            +StoreArray(arrayLocal, indexLocal)
                        }
                        +LoadValue(arrayLocal)
                    } else {
                        +StoreArray(arrayLocal, indexLocal)
                    }
                }
            }

            ArgumentsMode.Normal -> {
                for (argument in arguments)
                    argument.accept(this)
            }
        }
        return mode
    }

    override fun visit(node: ArgumentNode) = node.expression.accept(this)

    override fun visit(node: CallExpressionNode) {
        when (node.target) {
            is MemberExpressionNode -> pushMemberExpression(node.target, pushReceiver = true)
            is OptionalChainNode -> pushOptionalChain(node.target, true)
            else -> {
                node.target.accept(this)
                +PushUndefined
            }
        }

        val argumentsMode = pushArguments(node.arguments)

        if (node.target is IdentifierReferenceNode && node.target.identifierNode.rawName == "eval") {
            +CallWithDirectEvalCheck(node.arguments.size, argumentsMode != ArgumentsMode.Normal)
        } else if (argumentsMode == ArgumentsMode.Normal) {
            +Call(node.arguments.size)
        } else {
            +CallArray
        }
    }

    override fun visit(node: NewExpressionNode) {
        node.target.accept(this)
        // TODO: Property new.target
        +Dup

        if (pushArguments(node.arguments) == ArgumentsMode.Normal) {
            +Construct(node.arguments.size)
        } else {
            +ConstructArray
        }
    }

    override fun visit(node: StringLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visit(node: BooleanLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visit(node: NullLiteralNode) {
        +PushNull
    }

    override fun visit(node: NumericLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visit(node: ForAwaitOfNode) {
        TODO()
    }

    override fun visit(node: ThrowStatementNode) {
        node.expr.accept(this)
        +Throw

        // Make another block for anything after the throw. This block will be eliminated by the
        // BlockOptimizer.
        builder.enterBlock(builder.makeBlock("ThrowContinuation"))
    }

    override fun visit(node: TryStatementNode) {
        if (node.finallyBlock != null)
            unsupported("Try-statement finally blocks")

        expect(node.catchNode != null)

        val catchBlock = builder.makeBlock("CatchBlock")
        builder.pushHandlerBlock(catchBlock)
        val tryBlock = builder.makeBlock("TryBlock")

        +Jump(tryBlock)
        builder.enterBlock(tryBlock)
        node.tryBlock.accept(this)

        builder.popHandlerBlock()
        val continuationBlock = builder.makeBlock("TryContinuationBlock")
        +Jump(continuationBlock)

        builder.enterBlock(catchBlock)

        visitBlock(node.catchNode.block) {
            if (node.catchNode.parameter == null) {
                +Pop
            } else {
                assign(node.catchNode.parameter.declaration.node)
            }
        }

        +Jump(continuationBlock)
        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: NamedDeclaration) {
        TODO()
    }

    override fun visit(node: DebuggerStatementNode) {
        TODO()
    }

    override fun visit(node: ImportNode) {
        // nop
    }

    override fun visit(node: ExportNode) {
        for (export in node.exports) {
            when (export) {
                is Export.Expr -> {
                    export.expr.accept(this)
                    +StoreModuleVar("default")
                }

                is Export.Named -> +StoreModuleVar(export.localIdent.processedName)

                is Export.Node -> {
                    when (val decl = export.node) {
                        is ClassDeclarationNode -> {
                            decl.accept(this)
                            loadFromSource(decl)
                            if (export.default) {
                                +StoreModuleVar("default")
                            } else {
                                expect(decl.identifier != null)
                                +StoreModuleVar(decl.identifier.processedName)
                            }
                        }
                        is FunctionDeclarationNode -> {
                            // The function has been created in the prologue of this IR
                            loadFromSource(decl)
                            if (export.default) {
                                +StoreModuleVar("default")
                            } else {
                                expect(decl.identifier != null)
                                +StoreModuleVar(decl.identifier.processedName)
                            }
                        }
                        is LexicalDeclarationNode, is VariableSourceNode -> {
                            decl.accept(this)
                            decl.declarations.flatMap { it.sources() }.forEach {
                                loadFromSource(it)
                                +StoreModuleVar(it.name())
                            }
                        }
                        else -> TODO()
                    }
                }
                is Export.Namespace -> {
                    // These are treated mostly like imports, so all of the work is done
                    // in SourceTextModuleRecord
                }
            }
        }
    }

    override fun visit(node: PropertyName) {
        if (node.type == PropertyName.Type.Identifier) {
            +PushConstant((node.expression as IdentifierNode).processedName)
        } else node.expression.accept(this)
    }

    override fun visit(node: FunctionExpressionNode) {
        functionDeclarationInstantiation(node, name = node.identifier?.processedName ?: "")

        // If the function is inlineable, that means there are no recursive references inside it,
        // meaning that we don't need to worry about storing it in the EnvRecord
        if (node.identifier != null) {
            +Dup
            storeToSource(node)
        }
    }

    override fun visit(node: ArrowFunctionNode) {
        functionDeclarationInstantiation(node)
    }

    override fun visit(node: ClassDeclarationNode) {
        expect(node.identifier != null)
        visitClassImpl(node.identifier.processedName, node.classNode)
        storeToSource(node)
    }

    override fun visit(node: ClassExpressionNode) {
        visitClassImpl(node.identifier?.processedName, node.classNode)
    }

    private fun visitClassImpl(name: String?, node: ClassNode) {
        val instanceFields = mutableListOf<ClassFieldNode>()
        val staticFields = mutableListOf<ClassFieldNode>()
        val methods = mutableListOf<ClassMethodNode>()
        var constructor: ClassMethodNode? = null

        node.body.forEach {
            if (it is ClassFieldNode) {
                if (it.isStatic) {
                    staticFields.add(it)
                } else {
                    instanceFields.add(it)
                }
            } else {
                val method = it as ClassMethodNode
                if (method.isConstructor()) {
                    expect(constructor == null)
                    constructor = method
                } else {
                    methods.add(method)
                }
            }
        }

        val constructorKind = if (node.heritage == null) {
            JSFunction.ConstructorKind.Base
        } else JSFunction.ConstructorKind.Derived

        if (constructor != null) {
            val method = constructor!!.method
            require(method.methodKind == MethodDefinitionNode.Kind.Normal)

            functionDeclarationInstantiation(
                method,
                name = name ?: "<anonymous class constructor>",
                classConstructorKind = constructorKind,
            )
        } else {
            val info = makeImplicitClassConstructor(
                name ?: "<anonymous class constructor>",
                constructorKind,
                instanceFields.isNotEmpty(),
            )
            +CreateMethod(info)
        }

        if (node.heritage != null) {
            node.heritage.accept(this)
        } else {
            +PushEmpty
        }

        // ctor superCtor

        +CreateClass

        // class

        for (classMethod in methods) {
            +Dup

            val method = classMethod.method
            val propName = method.propName
            val isComputed = propName.type == PropertyName.Type.Computed

            val functionInfo = functionDeclarationInstantiation(
                method,
                name = propName.asString(),
                classConstructorKind = constructorKind,
                instantiate = false,
            )

            // If the name is computed, that comes before the method register
            if (isComputed) {
                // TODO: Cast to property name
                propName.expression.accept(this)
                +AttachComputedClassMethod(classMethod.isStatic, method.methodKind, functionInfo)
            } else {
                +AttachClassMethod(propName.asString(), classMethod.isStatic, method.methodKind, functionInfo)
            }
        }

        +FinalizeClass

        // Process fields
        // Instance fields are initialized in a dedicated method, whereas static fields
        // are created on the class after it is created with CreateClass

        if (instanceFields.isEmpty() && staticFields.isEmpty())
            return

        if (instanceFields.isNotEmpty()) {
            +Dup
            val instanceFieldInitializerMethod = makeClassFieldInitializerMethod(instanceFields)
            +CreateClosure(instanceFieldInitializerMethod)
            +Dup
            +PushEmpty // Value doesn't matter, just needs to be not undefined
            +StoreNamedProperty(Realm.InternalSymbols.isClassInstanceFieldInitializer)
            +StoreNamedProperty(Realm.InternalSymbols.classInstanceFields)
        }

        for (field in staticFields) {
            +Dup
            storeClassField(field)
        }
    }

    private fun makeClassFieldInitializerMethod(fields: List<ClassFieldNode>): FunctionInfo {
        val prevBuilder = builder
        builder = IRBuilder(RESERVED_LOCALS_COUNT, 0, true)

        for (field in fields) {
            +LoadValue(RECEIVER_LOCAL)
            storeClassField(field)
        }

        +PushUndefined
        +Return

        return FunctionInfo(
            "<class instance field initializer>",
            builder.build(),
            isStrict = true,
            0,
            isTopLevel = false,
            isGenerator = false,
            isArrow = false,
        ).also {
            builder = prevBuilder
            builder.addNestedFunction(it)
        }
    }

    private fun callClassInstanceFieldInitializer() {
        +PushClosure
        +LoadNamedProperty(Realm.InternalSymbols.classInstanceFields)
        +LoadValue(RECEIVER_LOCAL)
        +Call(0)
        +Pop
    }

    private fun makeImplicitClassConstructor(
        name: String,
        constructorKind: JSFunction.ConstructorKind,
        hasInstanceFields: Boolean,
    ): FunctionInfo {
        // One for the receiver/new.target
        var argCount = RESERVED_LOCALS_COUNT
        if (constructorKind == JSFunction.ConstructorKind.Derived) {
            // ...and one for the rest param, if necessary
            argCount++
        }

        val prevBuilder = builder
        builder = IRBuilder(argCount, 0, true)

        if (constructorKind == JSFunction.ConstructorKind.Base) {
            if (hasInstanceFields)
                callClassInstanceFieldInitializer()
            +PushUndefined
            +Return
        } else {
            // Initializer the super constructor
            +GetSuperConstructor
            +LoadValue(NEW_TARGET_LOCAL)
            +CollectRestArgs
            +ConstructArray
            if (hasInstanceFields)
                callClassInstanceFieldInitializer()
            +Return
        }

        return FunctionInfo(
            name,
            builder.build(),
            isStrict = true,
            0,
            isTopLevel = false,
            isGenerator = false,
            isArrow = false,
        ).also {
            builder = prevBuilder
            builder.addNestedFunction(it)
        }
    }

    private fun storeClassField(field: ClassFieldNode) {
        fun loadValue() {
            if (field.initializer != null) {
                field.initializer.accept(this)
            } else {
                +PushUndefined
            }
        }

        if (field.identifier.type == PropertyName.Type.Identifier) {
            val name = (field.identifier.expression as IdentifierNode).processedName
            loadValue()
            +StoreNamedProperty(name)
        } else {
            field.identifier.expression.accept(this)
            loadValue()
            +StoreKeyedProperty
        }
    }

    override fun visit(node: AwaitExpressionNode) {
        val continuationBlock = builder.makeBlock("AwaitContinuation")
        node.expression.accept(this)
        +Await(continuationBlock)
        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: ConditionalExpressionNode) {
        node.predicate.accept(this)

        val ifTrueBlock = builder.makeBlock("ConditionTrueBlock")
        val ifFalseBlock = builder.makeBlock("ConditionFalseBlock")
        val continuationBlock = builder.makeBlock("ConditionalContinuation")

        +JumpIfToBooleanTrue(ifTrueBlock, ifFalseBlock)
        builder.enterBlock(ifTrueBlock)
        node.ifTrue.accept(this)
        +Jump(continuationBlock)

        builder.enterBlock(ifFalseBlock)
        node.ifFalse.accept(this)
        +Jump(continuationBlock)

        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: SuperPropertyExpressionNode) {
        +LoadValue(RECEIVER_LOCAL)
        +ThrowSuperNotInitializedIfEmpty

        +GetSuperBase
        if (node.isComputed) {
            node.target.accept(this)
            +LoadKeyedProperty
        } else {
            +LoadNamedProperty((node.target as IdentifierNode).processedName)
        }
    }

    override fun visit(node: SuperCallExpressionNode) {
        +GetSuperConstructor
        +Dup
        +ThrowSuperNotInitializedIfEmpty

        +LoadValue(NEW_TARGET_LOCAL)

        if (pushArguments(node.arguments) == ArgumentsMode.Normal) {
            +Construct(node.arguments.size)
        } else {
            +ConstructArray
        }
    }

    override fun visit(node: ImportCallExpressionNode) {
        TODO()
    }

    override fun visit(node: YieldExpressionNode) {
        if (node.expression == null) {
            +PushUndefined
        } else {
            node.expression.accept(this)
        }

        val continuationBlock = builder.makeBlock("YieldContinuation")
        +Yield(continuationBlock)
        builder.enterBlock(continuationBlock)
    }

    override fun visit(node: ImportMetaExpressionNode) {
        TODO()
    }

    override fun visit(node: ParenthesizedExpressionNode) {
        node.expression.accept(this)
    }

    override fun visit(node: TemplateLiteralNode) {
        for (part in node.parts) {
            if (part is StringLiteralNode) {
                +PushConstant(part.value)
            } else {
                part.accept(this)
                +ToString
            }
        }

        +CreateTemplateLiteral(node.parts.size)
    }

    override fun visit(node: RegExpLiteralNode) {
        +CreateRegExpObject(node.source, node.flags, node.regexp)
    }

    override fun visit(node: NewTargetNode) {
        +LoadValue(NEW_TARGET_LOCAL)
    }

    override fun visit(node: ArrayLiteralNode) {
        val arrayLocal = builder.newLocalSlot(LocalKind.Value)

        +CreateArray
        +StoreValue(arrayLocal)

        var index: Int? = 0
        var indexLocal: Local? = null
        var iteratorLocal: Local? = null

        for (element in node.elements) {
            when (element.type) {
                ArrayElementNode.Type.Elision, ArrayElementNode.Type.Normal -> {
                    if (element.type == ArrayElementNode.Type.Elision) {
                        +PushEmpty
                    } else {
                        element.expression!!.accept(this)
                    }

                    if (index != null) {
                        +StoreArrayIndexed(arrayLocal, index)
                        index++
                    } else {
                        +StoreArray(arrayLocal, indexLocal!!)
                    }
                }
                ArrayElementNode.Type.Spread -> {
                    if (indexLocal == null) {
                        indexLocal = builder.newLocalSlot(LocalKind.Int)
                        +PushJVMInt(index!!)
                        +StoreInt(indexLocal)
                        index = null
                    }

                    if (iteratorLocal == null)
                        iteratorLocal = builder.newLocalSlot(LocalKind.Value)

                    element.expression!!.accept(this)

                    +GetIterator
                    +StoreValue(iteratorLocal)
                    iterateValues(setOf(), iteratorLocal) {
                        +StoreArray(arrayLocal, indexLocal)
                    }
                }
            }
        }

        +LoadValue(arrayLocal)
    }

    override fun visit(node: ObjectLiteralNode) {
        +CreateObject

        for (property in node.properties) {
            +Dup
            when (property) {
                is KeyValueProperty -> {
                    storeObjectProperty(property.key) {
                        property.value.accept(this)
                    }
                }

                is ShorthandProperty -> {
                    property.key.accept(this)
                    +StoreNamedProperty(property.key.processedName)
                }

                is MethodProperty -> {
                    val method = property.method

                    fun makeFunction() {
                        // TODO: This probably isn't correct
                        val functionNode = FunctionExpressionNode(
                            null,
                            method.parameters,
                            method.body,
                            method.methodKind.toFunctionKind(),
                            SourceLocation.EMPTY,
                        )

                        functionNode.functionScope = method.functionScope
                        functionNode.scope = method.scope
                        functionNode.accept(this)
                    }

                    when (method.methodKind) {
                        MethodDefinitionNode.Kind.Normal,
                        MethodDefinitionNode.Kind.Generator,
                        MethodDefinitionNode.Kind.Async,
                        MethodDefinitionNode.Kind.AsyncGenerator, -> storeObjectProperty(
                            method.propName,
                            ::makeFunction,
                        )

                        MethodDefinitionNode.Kind.Getter, MethodDefinitionNode.Kind.Setter -> {
                            method.propName.accept(this)
                            makeFunction()
                            if (method.methodKind == MethodDefinitionNode.Kind.Getter) {
                                +DefineGetterProperty
                            } else +DefineSetterProperty
                        }
                    }
                }

                is SpreadProperty -> TODO()
            }
        }
    }

    // Consumes object from the stack
    private fun storeObjectProperty(property: PropertyName, valueProducer: () -> Unit) {
        if (property.type == PropertyName.Type.Identifier) {
            valueProducer()
            val name = (property.expression as IdentifierNode).processedName
            +StoreNamedProperty(name)
            return
        } else {
            property.expression.accept(this)
        }

        valueProducer()
        +StoreKeyedProperty
    }

    override fun visit(node: BigIntLiteralNode) {
        +PushBigInt(BigInteger(node.value, node.type.radix))
    }

    override fun visit(node: ThisLiteralNode) {
        +LoadValue(RECEIVER_LOCAL)
    }

    override fun visit(node: EmptyStatementNode) {}

    private fun unsupported(message: String): Nothing {
        throw NotImplementedError(message)
    }
    private fun enterControlFlowScope(labels: Set<String>, breakBlock: BlockIndex, continueBlock: BlockIndex?) {
        controlFlowScopes.add(ControlFlowScope(labels, breakBlock, continueBlock))
    }

    private fun exitControlFlowScope() {
        controlFlowScopes.removeLast()
    }

    data class ControlFlowScope(val labels: Set<String>, val breakBlock: BlockIndex, val continueBlock: BlockIndex?)

    private operator fun <T : Opcode> T.unaryPlus() = apply {
        builder.addOpcode(this)
    }

    // We don't expect to hit any of these...
    // TODO: Ideally these would be unreachable(), but because of a poor implementation of
    //       global/functionDeclarationInstantiation, some of these do get hit. The
    //       Transformer should be restructured so this doesn't happen
    override fun visit(node: ClassFieldNode) {}
    override fun visit(node: ClassMethodNode) {}
    override fun visit(node: ClassNode) {}
    override fun visit(node: DestructuringDeclaration) {}
    override fun visit(node: ScriptNode) {}
    override fun visit(node: SimpleBindingElement) {}
    override fun visit(node: SimpleBindingProperty) {}
    override fun visit(node: SimpleParameter) {}
    override fun visit(node: OptionalComputedAccessChain) {}
    override fun visit(node: ParameterList) {}
    override fun visit(node: MethodDefinitionNode) {}
    override fun visit(node: ModuleNode) {}
    override fun visit(node: BindingDeclaration) {}
    override fun visit(node: BindingDeclarationOrPattern) {}
    override fun visit(node: BindingElisionElement) {}
    override fun visit(node: BindingParameter) {}
    override fun visit(node: BindingPatternNode) {}
    override fun visit(node: BindingRestElement) {}
    override fun visit(node: BindingRestProperty) {}
    override fun visit(node: ComputedBindingProperty) {}
    override fun visit(node: Export) {}
    override fun visit(node: Import) {}
    override fun visit(node: OptionalAccessChain) {}
    override fun visit(node: OptionalCallChain) {}
    override fun visit(node: KeyValueProperty) {}
    override fun visit(node: ShorthandProperty) {}
    override fun visit(node: MethodProperty) {}
    override fun visit(node: SpreadProperty) {}
    override fun visit(node: WithStatementNode) {}
    override fun visit(node: CatchNode) {}
    override fun visit(node: CatchParameter) {}
    override fun visit(node: SwitchClause) {}
    override fun visit(node: RestParameter) {}
    override fun visit(node: ArrayElementNode) {}
    override fun visit(node: IdentifierNode) {}

    companion object {
        val RECEIVER_LOCAL = Local(0)
        val NEW_TARGET_LOCAL = Local(1)

        const val RESERVED_LOCALS_COUNT = 2
    }
}
