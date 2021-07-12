package me.mattco.reeva.interpreter.transformer.optimization

import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.opcodes.Register
import me.mattco.reeva.utils.expect

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

    private fun initializeLiveness(info: OptInfo) {
        for (block in info.opcodes.blocks) {
            for ((index, opcode) in block.withIndex()) {
                val readRegisters = opcode.readRegisters()
                val writeRegisters = opcode.writeRegisters()

                for (readReg in readRegisters) {
                    if (readReg < info.opcodes.argCount)
                        continue

                    expect(registerHasBeenWritten[readReg], "Register $readReg read before it has been written to")
                    registerLiveness[readReg].end = Location(block, index)
                }

                for (writeReg in writeRegisters) {
                    if (writeReg < info.opcodes.argCount)
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

    private fun reduceRegisters(info: OptInfo) {
        for (block in info.opcodes.blocks) {
            for ((index, opcode) in block.withIndex()) {
                for (readReg in opcode.readRegisters()) {
                    if (readReg < info.opcodes.argCount)
                        continue

                    val liveness = registerLiveness[readReg]
                    expect(liveness.start!!.block != block || liveness.start!!.opcodeOffset != index)
                    if (liveness.end!!.block == block && liveness.end!!.opcodeOffset == index)
                        freeRegister(newRegisters[readReg])

                    opcode.replaceRegisters(readReg, newRegisters[readReg])
                }

                for (writeReg in opcode.writeRegisters()) {
                    if (writeReg < info.opcodes.argCount)
                        continue

                    val liveness = registerLiveness[writeReg]
                    if (liveness.start!!.block == block && liveness.start!!.opcodeOffset == index)
                        newRegisters[writeReg] = nextFreeRegister()

                    opcode.replaceRegisters(writeReg, newRegisters[writeReg])
                }
            }
        }
    }

    private fun assignRegisters(info: OptInfo) {
        for (block in info.opcodes.blocks) {
            for (opcode in block) {
                for (readReg in opcode.readRegisters())
                    opcode.replaceRegisters(readReg, newRegisters[readReg])
                for (writeReg in opcode.writeRegisters())
                    opcode.replaceRegisters(writeReg, newRegisters[writeReg])
            }
        }
    }

    override fun evaluate(info: OptInfo) {
        if (info.opcodes.registerCount <= Interpreter.RESERVED_REGISTERS + 1)
            return

        repeat(info.opcodes.registerCount) {
            registerLiveness.add(LiveRange(it))
            registerHasBeenWritten.add(false)
            newRegisters.add(-1)
            freeRegisters.add(false)
        }

        // Exclude arguments from this allocation
        val opcodeStart = info.opcodes.blocks.first().let { Location(it, 0) }
        val opcodeEnd = info.opcodes.blocks.last().let { Location(it, it.lastIndex) }

        repeat(info.opcodes.argCount) {
            newRegisters[it] = it
            freeRegisters[it] = true
            registerHasBeenWritten[it] = true
            registerLiveness[it] = LiveRange(it, opcodeStart, opcodeEnd)
        }

        initializeLiveness(info)
        reduceRegisters(info)
        // assignRegisters(info)

        info.opcodes.registerCount = (newRegisters.maxOrNull()!! + 1).coerceAtLeast(Interpreter.RESERVED_REGISTERS)
    }
}