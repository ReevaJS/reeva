package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.ast.expressions.*
import com.reevajs.reeva.ast.literals.BooleanLiteralNode
import com.reevajs.reeva.ast.literals.NumericLiteralNode
import com.reevajs.reeva.ast.literals.StringLiteralNode
import com.reevajs.reeva.ast.statements.BlockNode
import com.reevajs.reeva.ast.statements.IfStatementNode
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.parsing.HoistingScope
import com.reevajs.reeva.parsing.Scope
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

class Transformer(val executable: Executable) : ASTVisitor {
    private lateinit var builder: IRBuilder
    private var currentScope: Scope? = null

    fun transform(): TransformerResult {
        expect(executable.script != null)
        expect(!::builder.isInitialized, "Cannot reuse a Transformer")

        return try {
            val script = executable.script!!
            builder = IRBuilder(RESERVED_LOCALS, script.scope.inlineableLocalCount)

            globalDeclarationInstantiation(script.scope as HoistingScope) {
                visit(script.statements)
                +Return
            }

            TransformerResult.Success(FunctionInfo(
                executable.name,
                builder.getOpcodes(),
                builder.getLocals(),
                builder.argCount,
                script.scope.isStrict,
                isTopLevel = true,
            ))
        } catch (e: Throwable) {
            TransformerResult.InternalError(e)
        }
    }

    private fun enterScope(scope: Scope) {
        currentScope = scope

        if (scope.requiresEnv())
            +PushDeclarativeEnvRecord(scope.slotCount)
    }

    private fun exitScope(scope: Scope) {
        currentScope = scope.outer

        if (scope.requiresEnv())
            +PopEnvRecord
    }

    private fun globalDeclarationInstantiation(scope: HoistingScope, block: () -> Unit) {
        enterScope(scope)

        val variables = scope.variableSources

        val varVariables = variables.filter { it.type == VariableType.Var }
        val lexVariables = variables.filter { it.type != VariableType.Var }

        val varNames = varVariables.map { it.name() }
        val lexNames = lexVariables.map { it.name() }

        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        for (decl in varVariables.asReversed()) {
            if (decl !is FunctionDeclarationNode)
                continue

            val name = decl.name()
            if (name in functionNames)
                continue

            functionNames.add(0, name)

            // We only care about top-level functions. Functions in nested block
            // scopes get initialized in BlockDeclarationInstantiation
            if (decl !in scope.hoistedVariables)
                functionsToInitialize.add(0, decl)
        }

        val declaredVarNames = mutableListOf<String>()

        for (decl in varVariables) {
            if (decl is FunctionDeclarationNode)
                continue

            val name = decl.name()
            if (name in functionNames || name in declaredVarNames)
                continue

            declaredVarNames.add(name)
        }

        if (declaredVarNames.isNotEmpty() || lexNames.isNotEmpty() || functionNames.isNotEmpty())
            +DeclareGlobals(declaredVarNames, lexNames, functionNames)

        for (func in functionsToInitialize) {
            visitFunctionHelper(
                func.identifier.name,
                func.parameters,
                func.body,
                func.functionScope,
                func.body.scope,
                func.body.scope.isStrict,
                func.kind,
            )

            storeToSource(func)
        }

        block()

        exitScope(scope)
    }

    private fun visitFunctionHelper(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        functionScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        kind: Operations.FunctionKind,
        classConstructorKind: JSFunction.ConstructorKind? = null,
    ): FunctionInfo {
        val prevBuilder = builder
        builder = IRBuilder(
            parameters.size + RESERVED_LOCALS,
            functionScope.inlineableLocalCount,
            classConstructorKind == JSFunction.ConstructorKind.Derived,
        )

        val functionPackage = makeFunctionInfo(
            name,
            parameters,
            body,
            functionScope,
            bodyScope,
            isStrict,
            isAsync = kind.isAsync,
            classConstructorKind,
        )

        builder = prevBuilder
        val closureOp = when {
            classConstructorKind != null -> ::CreateClassConstructor
            kind.isGenerator && kind.isAsync -> ::CreateAsyncGeneratorClosure
            kind.isGenerator -> ::CreateGeneratorClosure
            kind.isAsync -> ::CreateAsyncClosure
            else -> ::CreateClosure
        }
        +closureOp(functionPackage)
        return functionPackage
    }

    private fun callClassInstanceFieldInitializer() {
        +PushClosure
        +GetNamedProperty(Realm.`@@classInstanceFields`)
        +LoadValue(RECEIVER_LOCAL)
        +Call(0)
    }

    private fun makeImplicitClassConstructor(
        name: String,
        constructorKind: JSFunction.ConstructorKind,
        hasInstanceFields: Boolean,
    ): FunctionInfo {
        // One for the receiver/new.target
        var argCount = RESERVED_LOCALS
        if (constructorKind == JSFunction.ConstructorKind.Derived) {
            // ...and one for the rest param, if necessary
            argCount++
        }

        val prevBuilder = builder
        builder = IRBuilder(argCount, 0, constructorKind == JSFunction.ConstructorKind.Derived)

        if (constructorKind == JSFunction.ConstructorKind.Base) {
            if (hasInstanceFields)
                callClassInstanceFieldInitializer()
            +PushUndefined
            +Return
        } else {
            // Initializer the super constructor
            +GetSuperConstructor
            +LoadValue(NEW_TARGET_LOCAL)
            +CreateRestParam
            +ConstructArray
            if (hasInstanceFields)
                callClassInstanceFieldInitializer()
            +Return
        }

        return FunctionInfo(
            name,
            builder.getOpcodes(),
            builder.getLocals(),
            argCount,
            isStrict = true,
            isTopLevel = false,
        ).also {
            builder = prevBuilder
        }
    }

    private fun makeFunctionInfo(
        name: String,
        parameters: ParameterList,
        body: ASTNode,
        functionScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        isAsync: Boolean,
        classConstructorKind: JSFunction.ConstructorKind?,
        hasClassFields: Boolean = false,
    ): FunctionInfo {
        functionDeclarationInstantiation(
            parameters,
            functionScope,
            bodyScope,
            isStrict
        ) {
            if (hasClassFields && classConstructorKind == JSFunction.ConstructorKind.Base) {
                // We can't load fields here if we are in a derived constructor as super() hasn't
                // been called
                callClassInstanceFieldInitializer()
            }

            // body's scope is the same as the function's scope (the scope we receive
            // as a parameter). We don't want to re-enter the same scope, so we explicitly
            // call visitASTListNode instead, which skips the {enter,exit}Scope calls.
            if (body is BlockNode) {
                visitASTListNode(body.statements)
            } else visit(body)

            if (classConstructorKind == JSFunction.ConstructorKind.Derived) {
                expect(body is BlockNode)
                // TODO: Check to see if this is redundant
                +LoadValue(RECEIVER_LOCAL)
                +ThrowSuperNotInitializedIfEmpty
            } else if (body is BlockNode) {
                +PushUndefined
            }

            +Return
        }

        return FunctionInfo(
            name,
            builder.getOpcodes(),
            builder.getLocals(),
            builder.argCount,
            isStrict,
            isTopLevel = false,
        )
    }

    private fun functionDeclarationInstantiation(
        parameters: ParameterList,
        functionScope: Scope,
        bodyScope: Scope,
        isStrict: Boolean,
        evaluationBlock: () -> Unit,
    ) {
        expect(functionScope is HoistingScope)
        expect(bodyScope is HoistingScope)

        val variables = bodyScope.variableSources
        val varVariables = variables.filter { it.type == VariableType.Var }
        val functionNames = mutableListOf<String>()
        val functionsToInitialize = mutableListOf<FunctionDeclarationNode>()

        for (decl in varVariables.asReversed()) {
            if (decl !is FunctionDeclarationNode)
                continue

            val name = decl.name()
            if (name in functionNames)
                continue

            functionNames.add(0, name)

            // We only care about top-level functions. Functions in nested block
            // scopes get initialized in BlockDeclarationInstantiation
            if (decl !in bodyScope.hoistedVariables)
                functionsToInitialize.add(0, decl)
        }

        when (functionScope.argumentsMode) {
            HoistingScope.ArgumentsMode.None -> {
            }
            HoistingScope.ArgumentsMode.Unmapped -> {
                +CreateUnmappedArgumentsObject
                storeToSource(functionScope.argumentsSource)
            }
            HoistingScope.ArgumentsMode.Mapped -> {
                +CreateMappedArgumentsObject
                storeToSource(functionScope.argumentsSource)
            }
        }

        enterScope(functionScope)

        if (parameters.containsDuplicates())
            TODO("Handle duplicate parameter names")

        val receiver = functionScope.receiverVariable

        if (receiver != null && !receiver.isInlineable) {
            +LoadValue(RECEIVER_LOCAL)
            storeToSource(receiver)
        }

        parameters.forEachIndexed { index, param ->
            val register = RESERVED_LOCALS + index

            when (param) {
                is SimpleParameter -> {
                    if (param.initializer != null) {
                        +LoadValue(register)
                        builder.ifHelper(::JumpIfNotUndefined) {
                            visit(param.initializer)
                            storeToSource(param)
                        }
                    } else if (!param.isInlineable) {
                        +LoadValue(register)
                        storeToSource(param)
                    }
                }
                is BindingParameter -> {
                    if (param.initializer != null) {
                        +LoadValue(register)
                        builder.ifHelper(::JumpIfNotUndefined) {
                            visit(param.initializer)
                            +StoreValue(register)
                        }
                    }
                    TODO()
                    // assign(param.pattern, register)
                }
                is RestParameter -> {
                    TODO()
                    // +CreateRestParam
                    // assign(param.declaration.node)
                }
            }
        }

        for (func in functionsToInitialize) {
            visitFunctionHelper(
                func.identifier.name,
                func.parameters,
                func.body,
                func.functionScope,
                func.body.scope,
                isStrict,
                func.kind,
            )

            storeToSource(func)
        }

        if (bodyScope != functionScope)
            enterScope(bodyScope)

        evaluationBlock()

        if (bodyScope != functionScope)
            exitScope(bodyScope)

        exitScope(functionScope)
    }

    private fun loadFromSource(source: VariableSourceNode) {
        if (source.mode == VariableMode.Global) {
            if (source.name() == "undefined") {
                +PushUndefined
            } else {
                expect(source.type == VariableType.Var)
                +LoadGlobal(source.name())
            }

            return
        }

        expect(source.index != -1)

        if (source.isInlineable) {
            +LoadValue(source.index)
        } else {
            val distance = currentScope!!.envDistanceFrom(source.scope)
            if (distance == 0) {
                +LoadCurrentEnvSlot(source.index)
            } else {
                +LoadEnvSlot(source.index, distance)
            }
        }
    }

    private fun storeToSource(source: VariableSourceNode) {
        if (source.mode == VariableMode.Global) {
            if (source.name() == "undefined") {
                if (source.scope.isStrict) {
                    +ThrowConstantError("cannot assign to constant variable \"undefined\"")
                } else return
            } else {
                expect(source.type == VariableType.Var)
                +StoreGlobal(source.name())
            }

            return
        }

        expect(source.index != -1)

        if (source.isInlineable) {
            +StoreValue(source.index)
        } else {
            val distance = currentScope!!.envDistanceFrom(source.scope)
            if (distance == 0) {
                +StoreCurrentEnvSlot(source.index)
            } else {
                +StoreEnvSlot(source.index, distance)
            }
        }
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visitExpression(node.condition)
        if (node.falseBlock == null) {
            builder.ifHelper(::JumpIfToBooleanFalse) {
                visit(node.trueBlock)
            }
        } else {
            builder.ifElseHelper(
                ::JumpIfToBooleanFalse,
                { visit(node.trueBlock) },
                { visit(node.falseBlock) },
            )
        }
    }

    override fun visitCommaExpression(node: CommaExpressionNode) {
        for (expression in node.expressions) {
            visitExpression(expression)
            +Pop
        }
    }

    override fun visitBinaryExpression(node: BinaryExpressionNode) {
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
                visit(node.lhs)
                builder.ifHelper(::JumpIfToBooleanTrue) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Or -> {
                visit(node.lhs)
                builder.ifHelper(::JumpIfToBooleanFalse) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Coalesce -> {
                visit(node.lhs)
                builder.ifHelper(::JumpIfNotNullish) {
                    visit(node.rhs)
                }
                return
            }
        }

        visitExpression(node.lhs)
        visitExpression(node.rhs)
        +op
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        if (node.op == UnaryOperator.Delete) {
            when (val expr = node.expression) {
                is IdentifierReferenceNode -> +PushConstant(false)
                !is MemberExpressionNode -> +PushConstant(true)
                else -> if (expr.type == MemberExpressionNode.Type.Tagged) {
                    +PushConstant(true)
                } else {
                    visitExpression(expr.lhs)

                    if (expr.type == MemberExpressionNode.Type.Computed) {
                        visit(expr.rhs)
                    } else {
                        +PushConstant((expr.rhs as IdentifierNode).name)
                    }

                    +if (node.scope.isStrict) DeletePropertyStrict else DeletePropertySloppy
                }
            }

            return
        }

        visitExpression(node.expression)

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

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        val op = if (node.isIncrement) Inc else Dec

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visitExpression(target)
                +ToNumber

            }
        }
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        pushMemberExpression(node, pushReceiver = false)
    }

    private fun pushMemberExpression(node: MemberExpressionNode, pushReceiver: Boolean) {
        visitExpression(node.lhs)

        if (node.isOptional) {
            builder.ifHelper(::JumpIfNotNullish) {
                +PushUndefined
            }
        }

        if (pushReceiver)
            +Dup

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visitExpression(node.rhs)
                +GetKeyedProperty
            }
            MemberExpressionNode.Type.NonComputed -> {
                +GetNamedProperty((node.rhs as IdentifierNode).name)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }

        if (pushReceiver)
            +Swap
    }

    enum class ArgumentsMode {
        Spread,
        Normal,
    }

    private fun argumentsMode(arguments: ArgumentList): ArgumentsMode {
        return if (arguments.any { it.isSpread }) {
            ArgumentsMode.Spread
        } else ArgumentsMode.Normal
    }

    private fun pushArguments(arguments: ArgumentList): ArgumentsMode {
        val mode = argumentsMode(arguments)
        when (mode) {
            ArgumentsMode.Spread -> TODO()
            ArgumentsMode.Normal -> {
                for (argument in arguments)
                    visitExpression(argument)
            }
        }
        return mode
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        if (node.target is MemberExpressionNode) {
            pushMemberExpression(node.target, pushReceiver = true)
        } else {
            visitExpression(node.target)
            +PushUndefined
        }

        fun buildCall() {
            if (pushArguments(node.arguments) == ArgumentsMode.Normal) {
                +Call(node.arguments.size)
            } else {
                +CallArray
            }
        }

        if (node.isOptional) {
            builder.ifElseHelper(
                ::JumpIfNotNullish,
                { +PushUndefined },
                { buildCall() },
            )
        } else {
            buildCall()
        }
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        visitExpression(node.target)
        // TODO: Property new.target
        +Dup

        if (pushArguments(node.arguments) == ArgumentsMode.Normal) {
            +Construct(node.arguments.size)
        } else {
            +ConstructArray
        }
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visitNullLiteral() {
        +PushNull
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        +PushConstant(node.value)
    }

    private fun unsupported(message: String): Nothing {
        throw NotImplementedError(message)
    }

    private operator fun Opcode.unaryPlus() {
        builder.addOpcode(this)
    }

    companion object {
        const val RECEIVER_LOCAL = 0
        const val NEW_TARGET_LOCAL = 0 // Does this need to be its own local?
        const val RESERVED_LOCALS = 1
    }
}
