package com.reevajs.reeva.interpreter.transformer.optimization

import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.transformer.Block
import com.reevajs.reeva.interpreter.transformer.FunctionOpcodes
import com.reevajs.reeva.interpreter.transformer.opcodes.Register
import com.reevajs.reeva.utils.expect

class RegisterAllocation : Pass {
    private val registerLiveness = mutableListOf<LiveRange>()
    private val registerHasBeenWritten = mutableListOf<Boolean>()
    private val newRegisters = mutableListOf<Int>()

    private val freeRegisters = mutableListOf<Boolean>()

    private fun nextFreeRegister(): Register {
        return freeRegisters.indexOfFirst { !it }.let {
            expect(it != -1)
            freeRegisters[it] = true
            it
        }
    }

    private fun freeRegister(register: Register) {
        freeRegisters[register] = false
    }

    private data class Location(
        var block: Block,
        var opcodeOffset: Int,
    )

    private data class LiveRange(
        val register: Register,
        var start: Location? = null,
        var end: Location? = null,
    )

    private fun initializeLiveness(opcodes: FunctionOpcodes) {
        for (block in opcodes.blocks) {
            for ((index, opcode) in block.withIndex()) {
                val readRegisters = opcode.readRegisters()
                val writeRegisters = opcode.writeRegisters()

                for (readReg in readRegisters) {
                    if (readReg < opcodes.argCount)
                        continue

                    expect(registerHasBeenWritten[readReg], "Register $readReg read before it has been written to")
                    registerLiveness[readReg].end = Location(block, index)
                }

                for (writeReg in writeRegisters) {
                    if (writeReg < opcodes.argCount)
                        continue

                    registerHasBeenWritten[writeReg] = true
                    val liveness = registerLiveness[writeReg]
                    if (liveness.start == null) {
                        liveness.start = Location(block, index)
                    } else {
                        liveness.end = Location(block, index)
                    }
                }
            }
        }
    }

    private fun reduceRegisters(opcodes: FunctionOpcodes) {
        for (block in opcodes.blocks) {
            for ((index, opcode) in block.withIndex()) {
                for (readReg in opcode.readRegisters()) {
                    if (readReg < opcodes.argCount)
                        continue

                    val liveness = registerLiveness[readReg]
                    expect(liveness.start!!.block != block || liveness.start!!.opcodeOffset != index)
                    if (liveness.end!!.block == block && liveness.end!!.opcodeOffset == index)
                        freeRegister(newRegisters[readReg])

                    opcode.replaceRegisters(readReg, newRegisters[readReg])
                }

                for (writeReg in opcode.writeRegisters()) {
                    if (writeReg < opcodes.argCount)
                        continue

                    val liveness = registerLiveness[writeReg]
                    if (liveness.start!!.block == block && liveness.start!!.opcodeOffset == index)
                        newRegisters[writeReg] = nextFreeRegister()

                    opcode.replaceRegisters(writeReg, newRegisters[writeReg])
                }
            }
        }
    }

    override fun evaluate(opcodes: FunctionOpcodes) {
        if (opcodes.registerCount <= Interpreter.RESERVED_REGISTERS + 1)
            return

        repeat(opcodes.registerCount) {
            registerLiveness.add(LiveRange(it))
            registerHasBeenWritten.add(false)
            newRegisters.add(-1)
            freeRegisters.add(false)
        }

        // Exclude arguments from this allocation
        val opcodeStart = opcodes.blocks.first().let { Location(it, 0) }
        val opcodeEnd = opcodes.blocks.last().let { Location(it, it.lastIndex) }

        repeat(opcodes.argCount) {
            newRegisters[it] = it
            freeRegisters[it] = true
            registerHasBeenWritten[it] = true
            registerLiveness[it] = LiveRange(it, opcodeStart, opcodeEnd)
        }

        initializeLiveness(opcodes)
        reduceRegisters(opcodes)

        opcodes.registerCount = (newRegisters.maxOrNull()!! + 1).coerceAtLeast(Interpreter.RESERVED_REGISTERS)
    }
}