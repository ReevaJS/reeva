package me.mattco.reeva.interpreter

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.*
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.ir.*
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue
import me.mattco.reeva.utils.unreachable
import java.io.File

fun main() {
    val source = File("./demo/index.js").readText()

    val parser = Parser(source)
    val parsed = parser.parseScript()
    if (parser.syntaxErrors.isNotEmpty()) {
        println(parser.syntaxErrors.first())
        return
    }
    ScopeResolver().resolve(parsed)
    println(parsed.dump(0))
    val info = IRTransformer().transform(parsed)

    println("\n\n")

    OpcodePrinter.printFunctionInfo(info)

    println("\n\n")

    Reeva.setup()

    val realm = Reeva.makeRealm()
    val task = IRInterpreter.consume(info, realm)
    val result = Reeva.getAgent().runTask(task)

    Reeva.with(realm) {
        val str = Operations.toPrintableString(result.value)
        if (result.isError) {
            println("\u001b[31m${Operations.toString(result.value).string}\u001B[0m")
        } else {
            println(str)
        }
    }

    Reeva.teardown()
}

object IRInterpreter : IRConsumer {
    override fun consume(info: FunctionInfo, realm: Realm) = IRInterpreterTask(info, realm)
}

class IRInterpreterTask(topLevelInfo: FunctionInfo, val realm: Realm) : Task<Reeva.Result>() {
    private val stack = InterpreterStack()

    private val mappedCPool
        get() = stack.frame.mappedCPool
    private var ip: Int
        get() = stack.frame.ip
        set(v) { stack.frame.ip = v }
    private var isDone: Boolean
        get() = stack.frame.isDone
        set(v) { stack.frame.isDone = v }
    private var exception: ThrowException?
        get() = stack.frame.exception
        set(v) { stack.frame.exception = v }
    private val info: FunctionInfo
        get() = stack.frame.functionInfo

    init {
        expect(topLevelInfo.isTopLevelScript)
        expect(topLevelInfo.argCount == 1)

        stack.pushFrame(InterpreterStack.StackFrame(topLevelInfo))
    }

    override fun makeContext(): ExecutionContext {
        val context = ExecutionContext(realm, null)

        if (!realm.isGloballyInitialized) {
            realm.initObjects()
            realm.setGlobalObject(JSGlobalObject.create(realm))
        }

        context.variableEnv = realm.globalEnv
        context.lexicalEnv = realm.globalEnv

        return context
    }

    override fun execute(): Reeva.Result {
        while (!isDone) {
            try {
                visit(info.code[ip++])
            } catch (e: ThrowException) {
                exception = e
                isDone = true
            }
        }

        return if (exception != null) {
            Reeva.Result(exception!!.value, isError = true)
        } else Reeva.Result(stack.accumulator, isError = false)
    }

    private fun visit(opcode: Opcode) {
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (opcode) {
            LdaZero -> {
                stack.accumulator = JSNumber.ZERO
            }
            LdaUndefined -> {
                stack.accumulator = JSUndefined
            }
            LdaNull -> {
                stack.accumulator = JSNull
            }
            LdaTrue -> {
                stack.accumulator = JSTrue
            }
            LdaFalse -> {
                stack.accumulator = JSFalse
            }
            is LdaConstant -> {
                stack.accumulator = getMappedConstant(opcode.cpIndex)
            }
            is Ldar -> {
                stack.accumulator = stack.getRegister(opcode.reg)
            }
            is Star -> {
                stack.setRegister(opcode.reg, stack.accumulator)
            }
            is Mov -> {
                stack.setRegister(opcode.toReg, stack.getRegister(opcode.fromReg))
            }
            is LdaNamedProperty -> {
                val name = info.constantPool[opcode.nameCpIndex] as String
                val obj = stack.getRegister(opcode.objectReg) as JSObject
                stack.accumulator = obj.get(name)
            }
            is LdaKeyedProperty -> {
                val obj = stack.getRegister(opcode.objectReg) as JSObject
                stack.accumulator = obj.get(Operations.toPropertyKey(stack.accumulator))
            }
            is StaNamedProperty -> {
                val name = info.constantPool[opcode.nameCpIndex] as String
                val obj = stack.getRegister(opcode.objectReg) as JSObject
                obj.set(name, stack.accumulator)
            }
            is StaKeyedProperty -> {
                val obj = stack.getRegister(opcode.objectReg) as JSObject
                val key = stack.getRegister(opcode.keyReg)
                obj.set(Operations.toPropertyKey(key), obj)
            }
            is Add -> binaryOp(opcode.valueReg, "+")
            is Sub -> binaryOp(opcode.valueReg, "-")
            is Mul -> binaryOp(opcode.valueReg, "*")
            is Div -> binaryOp(opcode.valueReg, "/")
            is Mod -> binaryOp(opcode.valueReg, "%")
            is Exp -> binaryOp(opcode.valueReg, "**")
            is BitwiseOr -> binaryOp(opcode.valueReg, "|")
            is BitwiseXor -> binaryOp(opcode.valueReg, "^")
            is BitwiseAnd -> binaryOp(opcode.valueReg, "&")
            is ShiftLeft -> binaryOp(opcode.valueReg, "<<")
            is ShiftRight -> binaryOp(opcode.valueReg, ">>")
            is ShiftRightUnsigned -> binaryOp(opcode.valueReg, ">>>")
            Inc -> TODO()
            Dec -> TODO()
            Negate -> TODO()
            BitwiseNot -> TODO()
            ToBooleanLogicalNot -> {
                stack.accumulator = (!Operations.toBoolean(stack.accumulator)).toValue()
            }
            LogicalNot -> {
                stack.accumulator = (!stack.accumulator.asBoolean).toValue()
            }
            TypeOf -> {
                stack.accumulator = Operations.typeofOperator(stack.accumulator)
            }
            is DeletePropertyStrict -> TODO()
            is DeletePropertySloppy -> TODO()
            is LdaGlobal -> {
                val name = info.constantPool[opcode.nameCpIndex] as String
                stack.accumulator = realm.globalEnv.getBindingValue(name, throwOnNotFound = true)
            }
            is CallAnyReceiver -> call(opcode.callableReg, opcode.receiverReg, opcode.argCount, CallMode.AnyReceiver)
            is CallProperty -> call(opcode.callableReg, opcode.receiverReg, opcode.argCount, CallMode.Property)
            is CallProperty0 -> call(opcode.callableReg, opcode.receiverReg, 0, CallMode.Property)
            is CallProperty1 -> call(opcode.callableReg, opcode.receiverReg, 1, CallMode.Property)
            is CallUndefinedReceiver -> call(opcode.callableReg, opcode.firstArgReg, opcode.argCount, CallMode.UndefinedReceiver)
            is CallUndefinedReceiver0 -> call(opcode.callableReg, -1, 0, CallMode.UndefinedReceiver)
            is CallUndefinedReceiver1 -> call(opcode.callableReg, opcode.argReg, 1, CallMode.UndefinedReceiver)
            is CallWithSpread -> TODO()
            is CallRuntime -> {
                val args = getRegisterBlock(opcode.firstArgReg, opcode.argCount)
                stack.accumulator = InterpRuntime.values()[opcode.id].function(args)
            }
            is Construct0 -> {
                stack.accumulator = Operations.construct(
                    stack.getRegister(opcode.targetReg),
                    emptyList(),
                    stack.accumulator
                )
            }
            is Construct -> {
                stack.accumulator = Operations.construct(
                    stack.getRegister(opcode.targetReg),
                    emptyList(),
                    stack.accumulator
                )
            }
            is ConstructWithSpread -> TODO()
            is TestEqual -> TODO()
            is TestNotEqual -> TODO()
            is TestEqualStrict -> TODO()
            is TestNotEqualStrict -> TODO()
            is TestLessThan -> {
                val lhs = stack.getRegister(opcode.targetReg)
                val rhs = stack.accumulator
                val result = Operations.abstractRelationalComparison(lhs, rhs, true)
                stack.accumulator = if (result == JSUndefined) JSFalse else result
            }
            is TestGreaterThan -> {
                val lhs = stack.getRegister(opcode.targetReg)
                val rhs = stack.accumulator
                val result = Operations.abstractRelationalComparison(rhs, lhs, false)
                stack.accumulator = if (result == JSUndefined) JSFalse else result
            }
            is TestLessThanOrEqual -> {
                val lhs = stack.getRegister(opcode.targetReg)
                val rhs = stack.accumulator
                val result = Operations.abstractRelationalComparison(rhs, lhs, false)
                stack.accumulator = if (result == JSFalse) JSTrue else JSFalse
            }
            is TestGreaterThanOrEqual -> {
                val lhs = stack.getRegister(opcode.targetReg)
                val rhs = stack.accumulator
                val result = Operations.abstractRelationalComparison(lhs, rhs, true)
                stack.accumulator = if (result == JSFalse) JSTrue else JSFalse
            }
            is TestReferenceEqual -> {
                stack.accumulator = (stack.accumulator == stack.getRegister(opcode.targetReg)).toValue()
            }
            is TestInstanceOf -> {
                stack.accumulator = Operations.instanceofOperator(stack.getRegister(opcode.targetReg), stack.accumulator)
            }
            is TestIn -> TODO()
            TestNullish -> {
                stack.accumulator = stack.accumulator.let {
                    it == JSNull || it == JSUndefined
                }.toValue()
            }
            TestNull -> {
                stack.accumulator = (stack.accumulator == JSNull).toValue()
            }
            TestUndefined -> {
                stack.accumulator = (stack.accumulator == JSUndefined).toValue()
            }
            ToBoolean -> {
                stack.accumulator = Operations.toBoolean(stack.accumulator).toValue()
            }
            ToNumber -> {
                stack.accumulator = Operations.toNumber(stack.accumulator)
            }
            ToNumeric -> {
                stack.accumulator = Operations.toNumeric(stack.accumulator)
            }
            ToObject -> {
                stack.accumulator = Operations.toObject(stack.accumulator)
            }
            ToString -> {
                stack.accumulator = Operations.toString(stack.accumulator)
            }
            is Jump -> {
                ip = opcode.offset
            }
            is JumpIfTrue -> {
                if (stack.accumulator == JSTrue)
                    ip = opcode.offset
            }
            is JumpIfFalse -> {
                if (stack.accumulator == JSFalse)
                    ip = opcode.offset
            }
            is JumpIfToBooleanTrue -> {
                if (Operations.toBoolean(stack.accumulator))
                    ip = opcode.offset
            }
            is JumpIfToBooleanFalse -> {
                if (!Operations.toBoolean(stack.accumulator))
                    ip = opcode.offset
            }
            is JumpIfNull -> {
                if (stack.accumulator == JSNull)
                    ip = opcode.offset
            }
            is JumpIfNotNull -> {
                if (stack.accumulator != JSNull)
                    ip = opcode.offset
            }
            is JumpIfUndefined -> {
                if (stack.accumulator == JSUndefined)
                    ip = opcode.offset
            }
            is JumpIfNotUndefined -> {
                if (stack.accumulator != JSUndefined)
                    ip = opcode.offset
            }
            is JumpIfObject -> {
                if (stack.accumulator is JSObject)
                    ip = opcode.offset
            }
            is JumpIfNullish -> {
                if (stack.accumulator.let { it == JSNull || it == JSUndefined })
                    ip = opcode.offset
            }
            is JumpIfNotNullish -> {
                if (!stack.accumulator.let { it == JSNull || it == JSUndefined })
                    ip = opcode.offset
            }
            JumpPlaceholder -> throw IllegalStateException("Illegal opcode: JumpPlaceholder")
            Throw -> TODO()
            Return -> {
                isDone = true
            }
            is CreateClosure -> {
                val newInfo = info.constantPool[opcode.cpIndex] as FunctionInfo
                stack.accumulator = IRFunction(Agent.runningContext.realm, newInfo)
            }
            DebugBreakpoint -> TODO()
            else -> TODO()
        }
    }

    private fun binaryOp(lhs: Int, op: String) {
        stack.accumulator = Operations.applyStringOrNumericBinaryOperator(
            stack.getRegister(lhs), stack.accumulator, op
        )
    }

    private fun call(callableReg: Int, firstArgReg: Int, argCount: Int, mode: CallMode) {
        if (mode == CallMode.Spread || mode == CallMode.Runtime)
            TODO()

        // TODO: Treat AnyReceiver differently than Property?

        val target = stack.getRegister(callableReg)
        val receiver = if (mode != CallMode.UndefinedReceiver) {
            stack.getRegister(firstArgReg)
        } else JSUndefined

        val args = if (argCount > 0) {
            getRegisterBlock(firstArgReg + 1, argCount)
        } else emptyList()

        stack.accumulator = Operations.call(target, receiver, args)
    }

    enum class CallMode {
        AnyReceiver,
        Property,
        UndefinedReceiver,
        Spread,
        Runtime
    }

    private fun getRegisterBlock(reg: Int, argCount: Int): List<JSValue> {
        val args = mutableListOf<JSValue>()
        for (i in reg until (reg + argCount))
            args.add(stack.getRegister(i))
        return args
    }

    private fun getMappedConstant(index: Int): JSValue {
        return mappedCPool[index] ?: when (val value = info.constantPool[index]) {
            is Int -> JSNumber(value)
            is Double -> JSNumber(value)
            is String -> JSString(value)
            is FunctionInfo -> TODO()
            else -> unreachable()
        }.also { mappedCPool[index] = it }
    }

    inner class IRFunction(
        realm: Realm,
        private val info: FunctionInfo,
    ) : JSFunction(realm, ThisMode.Lexical, Agent.runningContext.variableEnv) {
        init {
            isConstructable = true
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            stack.pushFrame(InterpreterStack.StackFrame(info))

            stack.setRegister(0, Operations.resolveThisBinding())
            arguments.forEachIndexed { index, arg ->
                stack.setRegister(index + 1, arg)
            }

            val result = execute()

            return try {
                // TODO: Actually use the error in a handleable way
                if (result.isError) {
                    exception = ThrowException(result.value)
                    isDone = true
                    JSUndefined
                } else result.value
            } finally {
                stack.popFrame()
            }
        }
    }
}
