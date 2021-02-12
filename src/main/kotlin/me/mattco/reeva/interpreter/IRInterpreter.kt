package me.mattco.reeva.interpreter

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.ir.*
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue
import me.mattco.reeva.utils.unreachable
import java.io.File

fun main() {
    val script = File("./demo/index.js").readText()

    Reeva.setup()

    val agent = Agent().apply {
        // settings here
    }

    Reeva.setAgent(agent)
    val realm = Reeva.makeRealm()
    agent.run(script, realm)

    Reeva.teardown()
}

class IRInterpreter(private val function: IRFunction, private val arguments: List<JSValue>) {
    private val globalEnv: GlobalEnvRecord
    
    private val info = function.info
    
    private var accumulator: JSValue = JSEmpty
    private val registers = Registers(info.registerCount)
    private var ip = 0
    private var isDone = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.constantPool.size) { null }
    
    private val envStack = mutableListOf<EnvRecord>()
    private var currentEnv = function.envRecord

    init {
        var env: EnvRecord? = currentEnv
        while (env != null) {
            envStack.add(env)
            env = env.outer
        }
        envStack.reverse()
        val topEnv = envStack.first()
        expect(topEnv is GlobalEnvRecord)
        globalEnv = topEnv
    }

    fun interpret(): Reeva.Result {
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
        } else Reeva.Result(accumulator, isError = false)
    }

    private fun visit(opcode: Opcode) {
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (opcode) {
            LdaZero -> {
                accumulator = JSNumber.ZERO
            }
            LdaUndefined -> {
                accumulator = JSUndefined
            }
            LdaNull -> {
                accumulator = JSNull
            }
            LdaTrue -> {
                accumulator = JSTrue
            }
            LdaFalse -> {
                accumulator = JSFalse
            }
            is LdaConstant -> {
                accumulator = getMappedConstant(opcode.cpIndex)
            }
            is Ldar -> {
                accumulator = registers[opcode.reg]
            }
            is Star -> {
                registers[opcode.reg] = accumulator
            }
            is Mov -> {
                registers[opcode.toReg] = registers[opcode.fromReg]
            }
            is LdaNamedProperty -> {
                val name = info.constantPool[opcode.nameCpIndex] as String
                val obj = registers[opcode.objectReg] as JSObject
                accumulator = obj.get(name)
            }
            is LdaKeyedProperty -> {
                val obj = registers[opcode.objectReg] as JSObject
                accumulator = obj.get(Operations.toPropertyKey(accumulator))
            }
            is StaNamedProperty -> {
                val name = info.constantPool[opcode.nameCpIndex] as String
                val obj = registers[opcode.objectReg] as JSObject
                obj.set(name, accumulator)
            }
            is StaKeyedProperty -> {
                val obj = registers[opcode.objectReg] as JSObject
                val key = registers[opcode.keyReg]
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
                accumulator = (!Operations.toBoolean(accumulator)).toValue()
            }
            LogicalNot -> {
                accumulator = (!accumulator.asBoolean).toValue()
            }
            TypeOf -> {
                accumulator = Operations.typeofOperator(accumulator)
            }
            is DeletePropertyStrict -> TODO()
            is DeletePropertySloppy -> TODO()
            is LdaGlobal -> {
                val name = info.constantPool[opcode.nameCpIndex] as String
                accumulator = globalEnv.extension().get(name)
            }
            is LdaCurrentEnv -> {
                accumulator = currentEnv.getBinding(opcode.slot)
            }
            is StaCurrentEnv -> {
                currentEnv.setBinding(opcode.slot, accumulator)
            }
            is LdaEnv -> {
                val envIndex = envStack.lastIndex - opcode.depthOffset
                accumulator = envStack[envIndex].getBinding(opcode.slot)
            }
            is StaEnv -> {
                val envIndex = envStack.lastIndex - opcode.depthOffset
                envStack[envIndex].setBinding(opcode.slot, accumulator)
            }
            is PushEnv -> {
                val newEnv = EnvRecord(currentEnv, currentEnv.isStrict, opcode.numSlots)
                envStack.add(newEnv)
                currentEnv = newEnv
            }
            is PopEnv -> {
                currentEnv = envStack.removeLast()
            }
            is CallAnyReceiver -> call(opcode.callableReg, opcode.receiverReg, opcode.argCount, CallMode.AnyReceiver)
            is CallProperty -> call(opcode.callableReg, opcode.receiverReg, opcode.argCount, CallMode.Property)
            is CallProperty0 -> call(opcode.callableReg, opcode.receiverReg, 0, CallMode.Property)
            is CallProperty1 -> call(opcode.callableReg, opcode.receiverReg, 1, CallMode.Property)
            is CallUndefinedReceiver -> call(opcode.callableReg, opcode.firstArgReg, opcode.argCount, CallMode.UndefinedReceiver)
            is CallUndefinedReceiver0 -> call(opcode.callableReg, -1, 0, CallMode.UndefinedReceiver)
            is CallUndefinedReceiver1 -> call(opcode.callableReg, opcode.argReg, 1, CallMode.UndefinedReceiver)
            is CallWithSpread -> TODO()
//            is CallRuntime -> {
//                val args = getRegisterBlock(opcode.firstArgReg, opcode.argCount)
//                accumulator = InterpRuntime.values()[opcode.id].function(args)
//            }
            is Construct0 -> {
                accumulator = Operations.construct(
                    registers[opcode.targetReg],
                    emptyList(),
                    accumulator
                )
            }
            is Construct -> {
                accumulator = Operations.construct(
                    registers[opcode.targetReg],
                    emptyList(),
                    accumulator
                )
            }
            is ConstructWithSpread -> TODO()
            is TestEqual -> TODO()
            is TestNotEqual -> TODO()
            is TestEqualStrict -> TODO()
            is TestNotEqualStrict -> TODO()
            is TestLessThan -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(lhs, rhs, true)
                accumulator = if (result == JSUndefined) JSFalse else result
            }
            is TestGreaterThan -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(rhs, lhs, false)
                accumulator = if (result == JSUndefined) JSFalse else result
            }
            is TestLessThanOrEqual -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(rhs, lhs, false)
                accumulator = if (result == JSFalse) JSTrue else JSFalse
            }
            is TestGreaterThanOrEqual -> {
                val lhs = registers[opcode.targetReg]
                val rhs = accumulator
                val result = Operations.abstractRelationalComparison(lhs, rhs, true)
                accumulator = if (result == JSFalse) JSTrue else JSFalse
            }
            is TestReferenceEqual -> {
                accumulator = (accumulator == registers[opcode.targetReg]).toValue()
            }
            is TestInstanceOf -> {
                accumulator = Operations.instanceofOperator(registers[opcode.targetReg], accumulator)
            }
            is TestIn -> TODO()
            TestNullish -> {
                accumulator = accumulator.let {
                    it == JSNull || it == JSUndefined
                }.toValue()
            }
            TestNull -> {
                accumulator = (accumulator == JSNull).toValue()
            }
            TestUndefined -> {
                accumulator = (accumulator == JSUndefined).toValue()
            }
            ToBoolean -> {
                accumulator = Operations.toBoolean(accumulator).toValue()
            }
            ToNumber -> {
                accumulator = Operations.toNumber(accumulator)
            }
            ToNumeric -> {
                accumulator = Operations.toNumeric(accumulator)
            }
            ToObject -> {
                accumulator = Operations.toObject(accumulator)
            }
            ToString -> {
                accumulator = Operations.toString(accumulator)
            }
            is Jump -> {
                ip = opcode.offset
            }
            is JumpIfTrue -> {
                if (accumulator == JSTrue)
                    ip = opcode.offset
            }
            is JumpIfFalse -> {
                if (accumulator == JSFalse)
                    ip = opcode.offset
            }
            is JumpIfToBooleanTrue -> {
                if (Operations.toBoolean(accumulator))
                    ip = opcode.offset
            }
            is JumpIfToBooleanFalse -> {
                if (!Operations.toBoolean(accumulator))
                    ip = opcode.offset
            }
            is JumpIfNull -> {
                if (accumulator == JSNull)
                    ip = opcode.offset
            }
            is JumpIfNotNull -> {
                if (accumulator != JSNull)
                    ip = opcode.offset
            }
            is JumpIfUndefined -> {
                if (accumulator == JSUndefined)
                    ip = opcode.offset
            }
            is JumpIfNotUndefined -> {
                if (accumulator != JSUndefined)
                    ip = opcode.offset
            }
            is JumpIfObject -> {
                if (accumulator is JSObject)
                    ip = opcode.offset
            }
            is JumpIfNullish -> {
                if (accumulator.let { it == JSNull || it == JSUndefined })
                    ip = opcode.offset
            }
            is JumpIfNotNullish -> {
                if (!accumulator.let { it == JSNull || it == JSUndefined })
                    ip = opcode.offset
            }
            JumpPlaceholder -> throw IllegalStateException("Illegal opcode: JumpPlaceholder")
            Throw -> TODO()
            Return -> {
                isDone = true
            }
            is CreateClosure -> {
                val newInfo = info.constantPool[opcode.cpIndex] as FunctionInfo
                val newEnv = EnvRecord(currentEnv, currentEnv.isStrict || newInfo.isStrict, newInfo.topLevelSlots)
                accumulator = IRFunction(function.realm, newInfo, newEnv)
            }
            DebugBreakpoint -> TODO()
            else -> TODO("Unrecognized opcode: ${opcode::class.simpleName}")
        }
    }

    private fun binaryOp(lhs: Int, op: String) {
        accumulator = Operations.applyStringOrNumericBinaryOperator(
            registers[lhs], accumulator, op
        )
    }

    private fun call(callableReg: Int, firstArgReg: Int, argCount: Int, mode: CallMode) {
        if (mode == CallMode.Spread || mode == CallMode.Runtime)
            TODO()

        // TODO: Treat AnyReceiver differently than Property?

        val target = registers[callableReg]
        val receiver = if (mode != CallMode.UndefinedReceiver) {
            registers[firstArgReg]
        } else JSUndefined

        val args = if (argCount > 0) {
            getRegisterBlock(firstArgReg + 1, argCount)
        } else emptyList()

        accumulator = Operations.call(target, receiver, args)
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
            args.add(registers[i])
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

    inner class Registers(size: Int) {
        var accumulator: JSValue = JSEmpty
        private val registers = Array<JSValue>(size) { JSEmpty }

        init {
            arguments.forEachIndexed { index, value ->
                registers[index] = value
            }
        }

        operator fun get(index: Int) = registers[index]

        operator fun set(index: Int, value: JSValue) {
            registers[index] = value
        }
    }

    class IRFunction(
        realm: Realm,
        val info: FunctionInfo,
        val envRecord: EnvRecord,
    ) : JSFunction(realm, info.isStrict) {
        init {
            isConstructable = true
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue) + arguments
            val result = IRInterpreter(this, args).interpret()
            if (result.isError)
                throw ThrowException(result.value)
            return result.value
        }
    }

    companion object {
        fun wrap(info: FunctionInfo, realm: Realm): JSFunction {
            return IRFunction(
                realm,
                info,
                GlobalEnvRecord(realm, info.isStrict, info.topLevelSlots)
            )
        }
    }
}
