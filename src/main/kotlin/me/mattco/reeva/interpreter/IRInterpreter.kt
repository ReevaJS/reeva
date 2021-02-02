package me.mattco.reeva.interpreter

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.IRConsumer
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.ir.*
import me.mattco.reeva.parser.Parser
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
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

class IRInterpreterTask(val info: FunctionInfo, val realm: Realm) : Task<Reeva.Result>() {
    private val stack = InterpreterStack()
    private var ip = 0
    private var isDone = false
    private var exception: ThrowException? = null

    private val mappedCPool = Array<JSValue?>(info.constantPool.size) { null }

    init {
        expect(info.isTopLevelScript)
        expect(info.argCount == 1)
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
        stack.pushFrame(
            InterpreterStack.StackFrame(
            info.name ?: "<anonymous>",
            info.registerCount
        ))

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
            is Add -> TODO()
            is Sub -> TODO()
            is Mul -> TODO()
            is Div -> TODO()
            is Mod -> TODO()
            is Exp -> TODO()
            is BitwiseOr -> TODO()
            is BitwiseXor -> TODO()
            is BitwiseAnd -> TODO()
            is ShiftLeft -> TODO()
            is ShiftRight -> TODO()
            is ShiftRightUnsigned -> TODO()
            Inc -> TODO()
            Dec -> TODO()
            Negate -> TODO()
            BitwiseNot -> TODO()
            ToBooleanLogicalNot -> TODO()
            LogicalNot -> TODO()
            TypeOf -> TODO()
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
            is CallRuntime -> TODO()
            is Construct0 -> TODO()
            is Construct -> TODO()
            is ConstructWithSpread -> TODO()
            is TestEqual -> TODO()
            is TestNotEqual -> TODO()
            is TestEqualStrict -> TODO()
            is TestNotEqualStrict -> TODO()
            is TestLessThan -> TODO()
            is TestGreaterThan -> TODO()
            is TestLessThanOrEqual -> TODO()
            is TestGreaterThanOrEqual -> TODO()
            is TestReferenceEqual -> TODO()
            is TestInstanceOf -> TODO()
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
            is ThrowStaticError -> TODO()
            is CreateClosure -> TODO()
            DebugBreakpoint -> TODO()
            else -> TODO()
        }
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
            val firstArg = firstArgReg + 1
            val range = firstArg until (firstArg + argCount)
            range.map { stack.getRegister(it) }
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

    private fun getMappedConstant(index: Int): JSValue {
        return mappedCPool[index] ?: when (val value = info.constantPool[index]) {
            is Int -> JSNumber(value)
            is Double -> JSNumber(value)
            is String -> JSString(value)
            is FunctionInfo -> TODO()
            else -> unreachable()
        }.also { mappedCPool[index] = it }
    }
}
