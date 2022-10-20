package com.reevajs.reeva.transformer

import com.reevajs.reeva.transformer.opcodes.*

object IRPrinter {
    fun printInfo(info: FunctionInfo) {
        val header = buildString {
            append("=== ")
            append(info.name)
            append(" ===")
        }

        println(header)
        println("Parameter count: ${info.ir.argCount}")
        println("Local count: ${info.ir.locals.size}")
        println("Block count: ${info.ir.blocks.size}")

        printBlocks(info.ir.blocks)

        for (nestedFunction in info.ir.nestedFunctions) {
            println("\n")
            printInfo(nestedFunction)
        }
    }

    fun printBlocks(blocks: Map<BlockIndex, BasicBlock>) {
        println("Blocks:")
        blocks.forEach(::printBlock)
    }

    fun printBlock(blockIndex: BlockIndex, block: BasicBlock) {
        print("@$blockIndex ")

        if (block.identifier != null)
            print("${block.identifier} ")

        if (block.handlerBlock != null)
            print("handler=@${block.handlerBlock}")

        println()

        for ((index, opcode) in block.opcodes.withIndex()) {
            print("  ")
            print("%3d".format(index))
            print(".  ")

            print(opcode::class.simpleName)

            when (opcode) {
                is Call -> println(" ${opcode.argCount}")
                is CallWithDirectEvalCheck -> println(" argCount=${opcode.argCount} isStrict=${opcode.isStrict}")
                is Construct -> println(" ${opcode.argCount}")
                is CreateAsyncClosure -> println(" <FunctionInfo ${opcode.functionInfo.name}>")
                is CreateAsyncGeneratorClosure -> println(" <FunctionInfo ${opcode.functionInfo.name}>")
                is CreateConstructor -> println(" <FunctionInfo ${opcode.functionInfo.name}>")
                is CreateClosure -> println(" <FunctionInfo ${opcode.functionInfo.name}>")
                is CreateClassFieldDescriptor -> {
                    print(" static=${opcode.isStatic}")
                    if (opcode.functionInfo != null) {
                        println(" <FunctionInfo ${opcode.functionInfo.name}>")
                    } else {
                        println()
                    }
                }
                is CreateClassMethodDescriptor -> println(" static=${opcode.isStatic} <FunctionInfo ${opcode.functionInfo.name}>")
                is CreateClass -> println(" numFields=${opcode.numFields} numMethods=${opcode.numMethods}")
                is CreateGeneratorClosure -> println(" <FunctionInfo ${opcode.functionInfo.name}>")
                is DeclareGlobalVars -> {
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
                    println()
                }
                is DeclareGlobalFunc -> println(" ${opcode.name}")
                is LoadNamedProperty -> println(" \"${opcode.name}\"")
                is IncInt -> println(" [${opcode.local}]")
                is LoadCurrentEnvName -> println(" \"${opcode.name}\"")
                is LoadEnvName -> println(" \"${opcode.name}\" #${opcode.distance}")
                is LoadGlobal -> println(" \"${opcode.name}\"")
                is LoadBoolean -> println(" [${opcode.local}]")
                is LoadInt -> println(" [${opcode.local}]")
                is LoadValue -> println(" [${opcode.local}]")
                is PushConstant -> {
                    if (opcode.literal is String) {
                        println(" \"${opcode.literal}\"")
                    } else println(" ${opcode.literal}")
                }
                is PushBigInt -> println(" #${opcode.bigint}")
                is StoreKeyedProperty -> println(if (opcode.isStrict) "strict" else "")
                is StoreNamedProperty -> println(" \"${opcode.name}\" ${if (opcode.isStrict) "strict" else ""}")
                is StoreCurrentEnvName -> println(" \"${opcode.name}\"")
                is StoreEnvName -> println(" \"${opcode.name}\" #${opcode.distance}")
                is StoreGlobal -> println(" \"${opcode.name}\"")
                is StoreInt -> println(" [${opcode.local}]")
                is StoreValue -> println(" [${opcode.local}]")
                is ThrowConstantReassignmentError -> println(" \"${opcode.name}\"")
                is ThrowLexicalAccessErrorIfEmpty -> println(" \"${opcode.name}\"")
                is Yield -> println(" @${opcode.target}")
                is Await -> println(" @${opcode.target}")
                is StoreArray -> println(" [${opcode.arrayLocal.value}] [${opcode.indexLocal.value}]")
                is StoreArrayIndexed -> println(" [${opcode.arrayLocal}] #${opcode.index}")
                is CopyObjectExcludingProperties -> println(" #${opcode.propertiesLocal}")
                is PushJVMInt -> println(" #${opcode.int}")
                is LoadModuleVar -> println(" \"${opcode.name}\"")
                is StoreModuleVar -> println(" \"${opcode.name}\"")
                is Jump -> println(" @${opcode.target}")
                is JumpIfTrue -> println(" true=@${opcode.trueTarget} false=@${opcode.falseTarget}")
                is JumpIfToBooleanTrue -> println(" true=@${opcode.trueTarget} false=@${opcode.falseTarget}")
                is JumpIfUndefined -> println(" undefined=@${opcode.undefinedTarget} else=@${opcode.elseTarget}")
                is JumpIfNullish -> println(" nullish=@${opcode.nullishTarget} else=@${opcode.elseTarget}")
                is CreateRegExpObject -> println(" /${opcode.source}/${opcode.flags}")
                is CreateTemplateLiteral -> println(" #${opcode.numberOfParts}")
                else -> println()
            }
        }
    }
}
