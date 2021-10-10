package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.core.lifecycle.Executable

class IRPrinter(private val executable: Executable) {
    private val info = executable.ir!!

    fun print() {
        val header = buildString {
            append("=== ")
            if (executable.file != null) {
                append(executable.file.name)
                append(": ")
            }

            if (info.isTopLevel) {
                append("<top-level script>")
            } else {
                append(executable.name)
            }

            append(" ===")
        }

        println(header)
        println("Parameter count: ${info.argCount}")
        println("Local count: ${info.localSlots.size}")
        println("Opcode count: ${info.opcodes.size}")

        println("Opcodes:")
        for ((index, opcode) in info.opcodes.withIndex()) {
            print("  ")
            print("%3d".format(index))
            print(".  ")

            print(opcode::class.simpleName)

            when (opcode) {
                is Call -> println(" ${opcode.argCount}")
                is Construct -> println(" ${opcode.argCount}")
                is CreateAsyncClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateAsyncGeneratorClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateClassConstructor -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is CreateGeneratorClosure -> println(" <FunctionInfo ${opcode.ir.name}>")
                is DeclareGlobals -> {
                    print(" ")
                    if (opcode.vars.isNotEmpty()) {
                        print("var={")
                        print(opcode.vars.joinToString(separator = " "))
                        print("} ")
                    }
                    if (opcode.lexs.isNotEmpty()) {
                        print("lex={")
                        print(opcode.lexs.joinToString(separator = " "))
                        print("} ")
                    }
                    if (opcode.funcs.isNotEmpty()) {
                        print("func={")
                        print(opcode.funcs.joinToString(separator = " "))
                        print("} ")
                    }
                    println()
                }
                is LoadNamedProperty -> println(" \"${opcode.name}\"")
                is IncInt -> println(" [${opcode.slot}]")
                is JumpInstr -> println(" @${opcode.to}")
                is LoadCurrentEnvSlot -> println(" #${opcode.slot}")
                is LoadEnvSlot -> println(" (${opcode.slot}) #${opcode.distance}")
                is LoadGlobal -> println(" \"${opcode.name}\"")
                is LoadInt -> println(" #${opcode.slot}")
                is LoadValue -> println(" #${opcode.slot}")
                is PushConstant -> {
                    if (opcode.literal is String) {
                        println(" \"${opcode.literal}\"")
                    } else println(" ${opcode.literal}")
                }
                is PushDeclarativeEnvRecord -> println(" #${opcode.slotCount}")
                is StoreCurrentEnvSlot -> println(" #${opcode.slot}")
                is StoreEnvSlot -> println(" #${opcode.slot} #${opcode.distance}")
                is StoreGlobal -> println(" \"${opcode.name}\"")
                is StoreInt -> println(" #${opcode.slot}")
                is StoreValue -> println(" #${opcode.slot}")
                is ThrowConstantError -> println(" \"${opcode.message}\"")
                else -> println()
            }
        }
    }
}