package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.DeclarationsArray
import me.mattco.reeva.interpreter.JumpTable
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.FunctionInfo
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable

object IrPrinter {
    fun printFunctionInfo(info: FunctionInfo) {
        val name = if (info.isTopLevelScript) {
            "<top-level script>"
        } else "function \"${info.name}\""
        println("FunctionInfo for $name")

        println("Parameter count: ${info.argCount}")
        println("Register count: ${info.code.registerCount}")

        for (block in info.code.blocks)
            println(stringifyBlock(info, block))

        if (info.code.constantPool.isNotEmpty()) {
            val childFunctions = mutableListOf<FunctionInfo>()

            println("\nConstant pool (size = ${info.code.constantPool.size})")
            for ((index, constant) in info.code.constantPool.withIndex()) {
                print("    $index: ")
                println(stringifyConstant(constant))
                if (constant is FunctionInfo)
                    childFunctions.add(constant)
            }

            for (childFunction in childFunctions) {
                println()
                printFunctionInfo(childFunction)
            }
        }
    }

    private fun stringifyBlock(info: FunctionInfo, block: Block) = buildString {
        append(block.index)
        if (block.handler != null) {
            append(" (handler=@")
            append(block.handler!!.index)
            append(")")
        }
        append(":\n")
        block.forEachIndexed { index, opcode ->
            append("    ")
            append(stringifyOpcode(info, opcode))
            if (index != block.lastIndex)
                append('\n')
        }
    }

    private fun stringifyOpcode(info: FunctionInfo, opcode: Opcode) = buildString {
        append(opcode::class.simpleName)

        when (opcode) {
            is LdaConstant -> append(stringifyIndex(info, opcode.index))
            is LdaInt -> append(stringifyLiteral(opcode.int))
            is Ldar -> append(stringifyRegister(opcode.reg))
            is Star -> append(stringifyRegister(opcode.reg))
            is LdaNamedProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyIndex(info, opcode.nameIndex))
            }
            is LdaKeyedProperty -> append(" object:", stringifyRegister(opcode.objectReg))
            is StaNamedProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyIndex(info, opcode.nameIndex))
            }
            is StaKeyedProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyRegister(opcode.nameReg))
            }

            is StaArrayIndex -> {
                append(" array:", stringifyRegister(opcode.arrayReg))
                append(" index:", stringifyIndex(info, opcode.index))
            }
            is StaArray -> {
                append(" array:", stringifyRegister(opcode.arrayReg))
                append(" index:", stringifyRegister(opcode.indexReg))
            }

            is Add -> append(stringifyRegister(opcode.lhsReg))
            is Sub -> append(stringifyRegister(opcode.lhsReg))
            is Mul -> append(stringifyRegister(opcode.lhsReg))
            is Div -> append(stringifyRegister(opcode.lhsReg))
            is Mod -> append(stringifyRegister(opcode.lhsReg))
            is Exp -> append(stringifyRegister(opcode.lhsReg))
            is BitwiseOr -> append(stringifyRegister(opcode.lhsReg))
            is BitwiseXor -> append(stringifyRegister(opcode.lhsReg))
            is BitwiseAnd -> append(stringifyRegister(opcode.lhsReg))
            is ShiftLeft -> append(stringifyRegister(opcode.lhsReg))
            is ShiftRight -> append(stringifyRegister(opcode.lhsReg))
            is ShiftRightUnsigned -> append(stringifyRegister(opcode.lhsReg))

            is StringAppend -> append(stringifyRegister(opcode.lhsStringReg))
            is DeletePropertySloppy -> append(stringifyRegister(opcode.objectReg))
            is DeletePropertyStrict -> append(stringifyRegister(opcode.objectReg))

            is LdaGlobal -> append(stringifyIndex(info, opcode.name))
            is StaGlobal -> append(stringifyIndex(info, opcode.name))
            is LdaCurrentEnv -> append(stringifyIndex(info, opcode.name))
            is StaCurrentEnv -> append(stringifyIndex(info, opcode.name))
            is LdaEnv -> {
                append(" name:", stringifyIndex(info, opcode.name))
                append(" offset:", stringifyLiteral(opcode.offset))
            }
            is StaEnv -> {
                append(" name:", stringifyIndex(info, opcode.name))
                append(" offset:", stringifyLiteral(opcode.offset))
            }

            is Call -> {
                append(" target:", stringifyRegister(opcode.targetReg))
                append(" receiver:", stringifyRegister(opcode.receiverReg))
                if (opcode.argumentRegs.isNotEmpty()) {
                    append(" arguments: [")
                    for (reg in opcode.argumentRegs)
                        append(stringifyRegister(reg))
                    append(']')
                }
            }
            is CallWithArgArray -> {
                append(" target:", stringifyRegister(opcode.targetReg))
                append(" receiver:", stringifyRegister(opcode.receiverReg))
                append(" array:", stringifyRegister(opcode.argumentsReg))
            }

            is Construct -> {
                append(" target:", stringifyRegister(opcode.targetReg))
                append(" new.target:", stringifyRegister(opcode.newTargetReg))
                if (opcode.argumentRegs.isNotEmpty()) {
                    append(" arguments: [")
                    for (reg in opcode.argumentRegs)
                        append(stringifyRegister(reg))
                    append(']')
                }
            }
            is ConstructWithArgArray -> {
                append(" target:", stringifyRegister(opcode.targetReg))
                append(" new.target:", stringifyRegister(opcode.newTargetReg))
                append(" array:", stringifyRegister(opcode.argumentsReg))
            }

            is TestEqual -> append(stringifyRegister(opcode.lhsReg))
            is TestNotEqual -> append(stringifyRegister(opcode.lhsReg))
            is TestEqualStrict -> append(stringifyRegister(opcode.lhsReg))
            is TestNotEqualStrict -> append(stringifyRegister(opcode.lhsReg))
            is TestLessThan -> append(stringifyRegister(opcode.lhsReg))
            is TestGreaterThan -> append(stringifyRegister(opcode.lhsReg))
            is TestLessThanOrEqual -> append(stringifyRegister(opcode.lhsReg))
            is TestGreaterThanOrEqual -> append(stringifyRegister(opcode.lhsReg))
            is TestReferenceEqual -> append(stringifyRegister(opcode.lhsReg))
            is TestInstanceOf -> append(stringifyRegister(opcode.lhsReg))
            is TestIn -> append(stringifyRegister(opcode.lhsReg))

            is JumpIfTrue -> append(stringifyBlock(opcode.ifBlock), " else:", stringifyBlock(opcode.elseBlock!!))
            is JumpIfNull -> append(stringifyBlock(opcode.ifBlock), " else:", stringifyBlock(opcode.elseBlock!!))
            is JumpIfUndefined -> append(stringifyBlock(opcode.ifBlock), " else:", stringifyBlock(opcode.elseBlock!!))
            is JumpIfNullish -> append(stringifyBlock(opcode.ifBlock), " else:", stringifyBlock(opcode.elseBlock!!))
            is JumpIfObject -> append(stringifyBlock(opcode.ifBlock), " else:", stringifyBlock(opcode.elseBlock!!))
            is JumpFromTable -> append(stringifyIndex(info, opcode.table))
            is Jump -> append(stringifyBlock(opcode.ifBlock))

            is ThrowConstReassignment -> append(stringifyIndex(info, opcode.nameIndex))
            is ThrowUseBeforeInitIfEmpty -> append(stringifyIndex(info, opcode.nameIndex))

            is DefineGetterProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyRegister(opcode.nameReg))
                append(" method:", stringifyRegister(opcode.methodReg))
            }
            is DefineSetterProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyRegister(opcode.nameReg))
                append(" method:", stringifyRegister(opcode.methodReg))
            }
            is DeclareGlobals -> append(stringifyIndex(info, opcode.declarationsIndex))
            is CreateClosure -> append(stringifyIndex(info, opcode.functionInfoIndex))
            else -> {
            }
        }
    }

    private fun stringifyIndex(info: FunctionInfo, index: Index): String {
        val constantString = stringifyConstant(info.code.constantPool[index])
        return " [$index] ($constantString)"
    }

    private fun stringifyRegister(register: Register) = " r$register"

    private fun stringifyLiteral(literal: Literal) = " #$literal"

    private fun stringifyBlock(block: Block) = " @${block.index}"

    private fun stringifyConstant(constant: Any): String {
        return when (constant) {
            is String -> "\"$constant\""
            is Double -> constant.toString()
            is FunctionInfo -> "<FunctionInfo for ${constant.name ?: "<anonymous>"}>"
            is DeclarationsArray -> buildString {
                append("DeclarationsArray ")
                val vars = constant.varIterator().joinToString(", ")
                val lexs = constant.lexIterator().joinToString(", ")
                val funcs = constant.funcIterator().joinToString(", ")

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
            is JumpTable -> buildString {
                append("JumpTable { ")
                expect(constant.isNotEmpty())
                for ((index, block) in constant) {
                    append(index)
                    append(": @")
                    append(block.index)
                    append(' ')
                }
                append('}')
            }
            else -> unreachable()
        }
    }
}
