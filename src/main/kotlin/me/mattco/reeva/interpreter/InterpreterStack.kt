package me.mattco.reeva.interpreter

import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSEmpty
import java.util.*

class InterpreterStack {
    private val stack = Stack<StackFrame>()

    val frame: StackFrame
        get() = stack.peek()
    internal val frameSize: Int
        get() = stack.size

    var accumulator: JSValue
        get() = frame.registers.accumulator
        set(value) {
            frame.registers.accumulator = value
        }

    fun getRegister(index: Int) = frame.registers[index]

    fun setRegister(index: Int, value: JSValue) = apply {
        frame.registers[index] = value
    }

    fun pushFrame(frame: StackFrame) {
        stack.push(frame)
    }

    fun popFrame(): JSValue {
        return stack.pop().registers.accumulator
    }

    class StackFrame(
        envRecord: EnvRecord,
        val functionInfo: FunctionInfo,
        arguments: List<JSValue> = emptyList(),
    ) {
        val registers = Registers(functionInfo.registerCount)
        var ip = 0
        var isDone = false
        var exception: ThrowException? = null
        val mappedCPool = Array<JSValue?>(functionInfo.constantPool.size) { null }

        private val envRecordStack = Stack<EnvRecord>()
        val currentEnv: EnvRecord
            get() = envRecordStack.peek()

        init {
            envRecordStack.push(envRecord)
            arguments.forEachIndexed { index, value ->
                registers[index] = value
            }
        }

        fun pushEnv(env: EnvRecord) {
            envRecordStack.push(env)
        }

        fun popEnv() {
            envRecordStack.pop()
        }
    }

    class Registers(size: Int) {
        var accumulator: JSValue = JSEmpty
        private val registers = Array<JSValue>(size) { JSEmpty }

        operator fun get(index: Int) = registers[index]

        operator fun set(index: Int, value: JSValue) {
            registers[index] = value
        }
    }
}
