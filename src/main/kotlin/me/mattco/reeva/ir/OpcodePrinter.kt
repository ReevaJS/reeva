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

        val indexWidth = listOf(
            info.code.size.toString().length,
            info.constantPool.size.toString().length,
            info.handlers.size.toString().length,
        ).maxOrNull()!!

        println("Bytecode:")
        val fmt = "\t%${indexWidth}d.    %s"
        info.code.forEachIndexed { index, opcode ->
            println(fmt.format(index, stringifyOpcode(opcode, info.argCount)))
        }

        val childFunctions = mutableListOf<FunctionInfo>()

        if (info.constantPool.isNotEmpty()) {
            println("\nConstant pool (size = ${info.constantPool.size})")
            info.constantPool.forEachIndexed { index, value ->
                print("\t%${indexWidth}d.    ".format(index))

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
                        val lexs = value.lexIterator().joinToString(", ")
                        val funcs = value.funcIterator().joinToString(", ")

                        if (vars.isNotBlank()) {
                            append("var={")
                            append(vars)
                            append("}")
                        }

                        if (lexs.isNotBlank()) {
                            append("lex={")
                            append(lexs)
                            append("}")
                        }

                        if (funcs.isNotBlank()) {
                            append("func={")
                            append(funcs)
                            append("}")
                        }
                    }
                    else -> unreachable()
                }.also(::println)
            }

            if (info.handlers.isNotEmpty()) {
                println("\nHandler table (size = ${info.handlers.size})")

                val startWidth = info.handlers.map { it.start.toString().length }.maxOrNull()!!
                val endWidth = info.handlers.map { it.end.toString().length }.maxOrNull()!!
                val handlerWidth = info.handlers.map { it.handler.toString().length }.maxOrNull()!!

                val fmtString = "\t%${indexWidth}d.    [%${startWidth}d, %${endWidth}d] -> %${handlerWidth}d"

                for ((index, handler) in info.handlers.withIndex()) {
                    print(fmtString.format(index, handler.start, handler.end, handler.handler))
                    println(" (${if (handler.isCatch) "catch" else "finally"})")
                }
            }
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
                val value = it.get(opcode)
                if (value is Int) {
                    append(formatArgument(it.get(opcode) as Int, it.name, argCount))
                } else {
                    append(value as Double)
                }
            }
        }
    }

    private fun formatArgument(value: Int, fieldName: String, argCount: Int) = fieldName.toLowerCase().let {
        // Many bugs were and are caused by this... so let's just throw here
        expect(value >= 0, "Expected register value to be >= 0")

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
