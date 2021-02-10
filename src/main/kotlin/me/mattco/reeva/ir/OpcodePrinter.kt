package me.mattco.reeva.ir

import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable

object OpcodePrinter {
    fun printFunctionInfo(info: FunctionInfo) {
        val name = if (info.isTopLevelScript) {
            "top-level script"
        } else "function \"${info.name}\""
        println("FunctionInfo for $name")

        println("Parameter count: ${info.argCount}")
        println("Register count: ${info.registerCount}")

        println("Bytecode:")
        info.code.forEachIndexed { index, opcode ->
            println("\t$index.   ${stringifyOpcode(opcode, info.argCount)}")
        }

        val childFunctions = mutableListOf<FunctionInfo>()

        println("Constant pool (size = ${info.constantPool.size})")
        info.constantPool.forEachIndexed { index, value ->
            print("\t$index.   ")

            when (value) {
                is Int -> "Int $value"
                is Double -> "Double $value"
                is String -> "String \"$value\""
                is FunctionInfo -> {
                    childFunctions.add(value)
                    "<FunctionInfo ${value.name}>"
                }
                is DeclarationsArray -> buildString {
                    append("DeclarationsArray ")
                    val vars = value.varIterator().joinToString(", ")
                    val lets = value.letIterator().joinToString(", ")
                    val consts = value.constIterator().joinToString(", ")

                    if (vars.isNotBlank()) {
                        append("vars={")
                        append(vars)
                        append("}")
                    }

                    if (lets.isNotBlank()) {
                        append("lets={")
                        append(lets)
                        append("}")
                    }

                    if (consts.isNotBlank()) {
                        append("consts={")
                        append(consts)
                        append("}")
                    }
                }
                else -> unreachable()
            }.also(::println)
        }

        childFunctions.forEach {
            println()
            printFunctionInfo(it)
        }
    }

    fun stringifyOpcode(opcode: Opcode, argCount: Int): String {
        return buildString {
            append(opcode::class.simpleName)
            opcode::class.java.declaredFields.filter {
                it.name != "INSTANCE"
            }.forEach {
                it.isAccessible = true
                append(' ')
                append(formatArgument(it.get(opcode) as Int, it.name, argCount))
            }
        }
    }

    private fun formatArgument(value: Int, fieldName: String, argCount: Int) = fieldName.toLowerCase().let {
        // Many bugs were and are caused by this... so let's just throw here
        expect(value != -1)

        when {
            "cp" in it -> "[$value]"
            "reg" in it -> when {
                value == 0 -> "<receiver>"
                value < argCount -> "a${argCount - value - 1}"
                else -> "r${value - argCount}"
            }
            else -> "#$value"
        }
    }
}
