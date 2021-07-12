package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.*
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.FunctionInfo
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.unreachable

class IrPrinter(private val info: FunctionInfo) {
    fun print() {
        val name = if (info.isTopLevelScript) {
            "<top-level script>"
        } else "function \"${info.name}\""
        println("FunctionInfo for $name")

        println("Parameter count: ${info.code.argCount}")
        println("Register count: ${info.code.registerCount}")
        println("Opcode count: ${info.code.blocks.sumOf { it.size }}")

        for (block in info.code.blocks)
            println(stringifyBlock(block))

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
                IrPrinter(childFunction).print()
            }
        }
    }

    private fun stringifyBlock(block: Block) = buildString {
        append(block.name)
        if (block.handler != null) {
            append(" (handler=@")
            append(block.handler!!.name)
            append(")")
        }
        append(":\n")
        block.forEachIndexed { index, opcode ->
            append("    ")
            append(stringifyOpcode(opcode))
            if (index != block.lastIndex)
                append('\n')
        }
    }

    private fun stringifyOpcode(opcode: Opcode) = buildString {
        append(opcode::class.simpleName)

        when (opcode) {
            is LdaConstant -> append(stringifyIndex(opcode.index))
            is LdaInt -> append(stringifyLiteral(opcode.int))
            is Ldar -> append(stringifyRegister(opcode.reg))
            is Star -> append(stringifyRegister(opcode.reg))
            is LdaNamedProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyIndex(opcode.nameIndex))
            }
            is LdaKeyedProperty -> append(" object:", stringifyRegister(opcode.objectReg))
            is StaNamedProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyIndex(opcode.nameIndex))
            }
            is StaKeyedProperty -> {
                append(" object:", stringifyRegister(opcode.objectReg))
                append(" name:", stringifyRegister(opcode.nameReg))
            }

            is StaArrayIndex -> {
                append(" array:", stringifyRegister(opcode.arrayReg))
                append(" index:", stringifyLiteral(opcode.index))
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

            is LdaGlobal -> append(stringifyIndex(opcode.name))
            is StaGlobal -> append(stringifyIndex(opcode.name))
            is LdaCurrentRecordSlot -> append(stringifyLiteral(opcode.slot))
            is StaCurrentRecordSlot -> append(stringifyLiteral(opcode.slot))
            is LdaRecordSlot -> {
                append(" slot:", stringifyLiteral(opcode.slot))
                append(" distance:", stringifyLiteral(opcode.distance))
            }
            is StaRecordSlot -> {
                append(" slot:", stringifyLiteral(opcode.slot))
                append(" distance:", stringifyLiteral(opcode.distance))
            }
            is PushDeclarativeEnvRecord -> append(stringifyLiteral(opcode.numSlots))

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

            is JumpIfTrue -> append(stringifyBlockIndex(opcode.ifBlock), " else:", stringifyBlockIndex(opcode.elseBlock!!))
            is JumpIfToBooleanTrue -> append(stringifyBlockIndex(opcode.ifBlock), " else:", stringifyBlockIndex(opcode.elseBlock!!))
            is JumpIfEmpty -> append(stringifyBlockIndex(opcode.ifBlock), " else:", stringifyBlockIndex(opcode.elseBlock!!))
            is JumpIfUndefined -> append(stringifyBlockIndex(opcode.ifBlock), " else:", stringifyBlockIndex(opcode.elseBlock!!))
            is JumpIfNullish -> append(stringifyBlockIndex(opcode.ifBlock), " else:", stringifyBlockIndex(opcode.elseBlock!!))
            is JumpFromTable -> append(stringifyIndex(opcode.table))
            is Jump -> append(stringifyBlockIndex(opcode.ifBlock))

            is Yield -> append(stringifyBlockIndex(opcode.continuationBlock))
            is Await -> append(stringifyBlockIndex(opcode.continuationBlock))

            is CreateClass -> {
                append(" descriptor:", stringifyIndex(opcode.classDescriptorIndex))
                append(" constructor:", stringifyRegister(opcode.constructor))
                append(" superClass:", stringifyRegister(opcode.superClass))
                if (opcode.args.isNotEmpty()) {
                    append(" arguments: [")
                    for (reg in opcode.args)
                        append(stringifyRegister(reg))
                    append(']')
                }
            }
            is CreateClassConstructor -> append(stringifyIndex(opcode.functionInfoIndex))

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
            is DeclareGlobals -> append(stringifyIndex(opcode.declarationsIndex))
            is CreateClosure -> append(stringifyIndex(opcode.functionInfoIndex))
            is CreateGeneratorClosure -> append(stringifyIndex(opcode.functionInfoIndex))
            is CreateAsyncClosure -> append(stringifyIndex(opcode.functionInfoIndex))
            else -> {
            }
        }
    }

    private fun stringifyIndex(index: Index): String {
        val constantString = stringifyConstant(info.code.constantPool[index])
        return " [$index] ($constantString)"
    }

    private fun stringifyRegister(register: Register): String {
        return when {
            register == Interpreter.RECEIVER_REGISTER -> " <receiver>"
            register == Interpreter.NEW_TARGET_REGISTER -> " <new.target>"
            register < info.code.argCount -> " a${register - Interpreter.RESERVED_REGISTERS}"
            else -> " r${register - info.code.argCount}"
        }
    }

    private fun stringifyLiteral(literal: Literal) = " #$literal"

    private fun stringifyBlockIndex(block: Block) = " @${block.name}"

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

                if (vars.isNotBlank())
                    append("var={", vars, '}')
                if (lexs.isNotBlank())
                    append("lex={", lexs, '}')
                if (funcs.isNotBlank())
                    append("func={", funcs, '}')
            }
            is JumpTable -> buildString {
                append("JumpTable { ")
                expect(constant.isNotEmpty())
                for ((index, block) in constant)
                    append(index, ": @", block.name, ' ')
                append('}')
            }
            is ClassDescriptor -> buildString {
                append("ClassDescriptor {")
                for (descriptor in constant.methodDescriptors) {
                    append('[', descriptor, ']')
                    if (descriptor != constant.methodDescriptors.last())
                        append(' ')
                }
                append('}')
            }
            is MethodDescriptor -> buildString {
                append("MethodDescriptor {")
                if (constant.name != null)
                    append(" name=", constant.name)
                append(" static=", constant.isStatic)
                when {
                    constant.isGetter -> append(" getter")
                    constant.isSetter -> append(" setter")
                    else -> when (constant.kind) {
                        Operations.FunctionKind.Async -> append(" async")
                        Operations.FunctionKind.Generator -> append(" generator")
                        Operations.FunctionKind.AsyncGenerator -> append(" async-generator")
                        Operations.FunctionKind.Normal -> {}
                    }
                }

                append(" info=", constant.methodInfo, " }")
            }
            is JSSymbol -> "Symbol (${constant.description})"
            else -> unreachable()
        }
    }
}
