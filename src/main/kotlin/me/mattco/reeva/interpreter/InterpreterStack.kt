package me.mattco.reeva.interpreter

import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSEmpty
import java.util.*

class InterpreterStack {
    private val stack = Stack<StackFrame>()

    val frame: StackFrame
        get() = stack.peek()

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

    class StackFrame(val functionInfo: FunctionInfo, arguments: List<JSValue> = emptyList()) {
        val registers = Registers(functionInfo.registerCount)
        var ip = 0
        var isDone = false
        var exception: ThrowException? = null
        val mappedCPool = Array<JSValue?>(functionInfo.constantPool.size) { null }

        init {
            arguments.forEachIndexed { index, value ->
                registers[index] = value
            }
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
