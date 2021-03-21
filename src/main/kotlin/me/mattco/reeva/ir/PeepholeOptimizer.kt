package me.mattco.reeva.ir

import me.mattco.reeva.ir.OpcodeType.*
import me.mattco.reeva.ir.opcodes.OpcodeVisitor

class PeepholeOptimizer private constructor(private val input: Array<Opcode>) : OpcodeVisitor() {
    private val output = mutableListOf<Opcode>()
    private var cursor = 0
    private var skip = false

    fun optimize(): Array<Opcode> {
//        return input

        while (cursor < input.size) {
            visit(input[cursor])
            if (!skip)
                output.add(input[cursor])
            skip = false
            cursor++
        }

        return output.toTypedArray()
    }

    override fun visitLdar(opcode: Opcode) {
        val prev = previous()
        val next = next()

        if (prev?.type == Star && prev.regAt(0) == opcode.regAt(0)) {
            skip()
        } else if (next?.type == Star) {
            skip(2)
            if (next.regAt(0) != opcode.regAt(0)) {
                output.add(Opcode(Mov, opcode.regAt(0), next.regAt(0)))
            }
        }
    }

    override fun visitStar(opcode: Opcode) {
        val prev = previous()

        if (prev?.type == Ldar && prev.regAt(0) == opcode.regAt(0)) {
            skip(2)
        }
    }

    override fun visitReturn() {
        trimForFunctionExit()
    }

    override fun visitThrow() {
        trimForFunctionExit()
    }

    private fun trimForFunctionExit() {
        fun shouldRemove(type: OpcodeType): Boolean {
            if (!type.hasSideEffects && !type.writesToAcc)
                return true

            // TODO: Generalize
            if (type == Star)
                return true

            return false
        }

        while (output.lastOrNull()?.let { shouldRemove(it.type) } == true)
            output.removeLast()
    }

    private fun OpcodeType.matches(vararg types: OpcodeType) = this in types

    private fun skip(n: Int = 1) {
        skip = true
        if (n > 1)
            cursor += n - 1
    }

    private fun add(opcode: Opcode) {
        output.add(opcode)
    }

    private fun previous() = if (cursor == 0) null else input[cursor - 1]

    private fun next() = if (cursor >= input.size - 1) null else input[cursor + 1]

    companion object {
        fun optimize(code: Array<Opcode>) = PeepholeOptimizer(code).optimize()
    }
}
