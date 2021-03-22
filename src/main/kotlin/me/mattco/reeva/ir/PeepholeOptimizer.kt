package me.mattco.reeva.ir

import me.mattco.reeva.ir.opcodes.IrOpcode
import me.mattco.reeva.ir.opcodes.IrOpcodeType.Ldar
import me.mattco.reeva.ir.opcodes.IrOpcodeType.Star
import me.mattco.reeva.ir.opcodes.IrOpcodeList
import me.mattco.reeva.ir.opcodes.IrOpcodeVisitor
import me.mattco.reeva.ir.opcodes.IrOpcodeType

class PeepholeOptimizer private constructor(private val input: IrOpcodeList) : IrOpcodeVisitor() {
    private var cursor = 0
    private var skip = false

    fun optimize() {
//        return

        while (cursor < input.size) {
            visit(input[cursor])
            if (skip)
                input.removeAt(cursor)
            skip = false
            cursor++
        }
    }

    override fun visitLdar(opcode: IrOpcode) {
        val prev = previous()

        if (prev?.type == Star && prev.regAt(0) == opcode.regAt(0))
            skip()
    }

    override fun visitStar(opcode: IrOpcode) {
        val prev = previous()

        if (prev?.type == Ldar && prev.regAt(0) == opcode.regAt(0))
            skip(2)
    }

    override fun visitReturn() {
        trimForFunctionExit()
    }

    override fun visitThrow() {
        trimForFunctionExit()
    }

    private fun trimForFunctionExit() {
        fun shouldRemove(type: IrOpcodeType): Boolean {
            if (!type.hasSideEffects && !type.writesToAcc)
                return true

            // TODO: Generalize
            if (type == Star)
                return true

            return false
        }

        while (input.lastOrNull()?.let { shouldRemove(it.type) } == true)
            input.removeLast()
    }

    private fun skip(n: Int = 1) {
        skip = true
        if (n > 1)
            cursor += n - 1
    }

    private fun previous() = if (cursor == 0) null else input[cursor - 1]

    private fun next() = if (cursor >= input.size - 1) null else input[cursor + 1]

    companion object {
        fun optimize(code: IrOpcodeList) = PeepholeOptimizer(code).optimize()
    }
}
