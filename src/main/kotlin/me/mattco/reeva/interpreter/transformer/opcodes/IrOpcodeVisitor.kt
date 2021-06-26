package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.transformer.Block

abstract class IrOpcodeVisitor {
    open fun visit(opcode: Opcode) {
        when (opcode) {
            LdaTrue -> visitLdaTrue()
            LdaFalse -> visitLdaFalse()
            LdaUndefined -> visitLdaUndefined()
            LdaNull -> visitLdaNull()
            LdaZero -> visitLdaZero()
            is LdaConstant -> visitLdaConstant(opcode.index)
            is LdaInt -> visitLdaInt(opcode.int)

            is Ldar -> visitLdar(opcode.reg)
            is Star -> visitStar(opcode.reg)
            is LdaNamedProperty -> visitLdaNamedProperty(opcode.objectReg, opcode.nameIndex)
            is LdaKeyedProperty -> visitLdaKeyedProperty(opcode.objectReg)
            is StaNamedProperty -> visitStaNamedProperty(opcode.objectReg, opcode.nameIndex)
            is StaKeyedProperty -> visitStaKeyedProperty(opcode.objectReg, opcode.nameReg)

            CreateArray -> visitCreateArray()
            is StaArrayIndex -> visitStaArrayIndex(opcode.arrayReg, opcode.index)
            is StaArray -> visitStaArray(opcode.arrayReg, opcode.indexReg)
            CreateObject -> visitCreateObject()

            is Add -> visitAdd(opcode.lhsReg)
            is Sub -> visitSub(opcode.lhsReg)
            is Mul -> visitMul(opcode.lhsReg)
            is Div -> visitDiv(opcode.lhsReg)
            is Mod -> visitMod(opcode.lhsReg)
            is Exp -> visitExp(opcode.lhsReg)
            is BitwiseOr -> visitBitwiseOr(opcode.lhsReg)
            is BitwiseXor -> visitBitwiseXor(opcode.lhsReg)
            is BitwiseAnd -> visitBitwiseAnd(opcode.lhsReg)
            is ShiftLeft -> visitShiftLeft(opcode.lhsReg)
            is ShiftRight -> visitShiftRight(opcode.lhsReg)
            is ShiftRightUnsigned -> visitShiftRightUnsigned(opcode.lhsReg)

            Inc -> visitInc()
            Dec -> visitDec()
            Negate -> visitNegate()
            BitwiseNot -> visitBitwiseNot()

            is StringAppend -> visitStringAppend(opcode.lhsStringReg)
            ToBooleanLogicalNot -> visitToBooleanLogicalNot()
            LogicalNot -> visitLogicalNot()
            TypeOf -> visitTypeOf()
            is DeletePropertySloppy -> visitDeletePropertySloppy(opcode.objectReg)
            is DeletePropertyStrict -> visitDeletePropertyStrict(opcode.objectReg)

            is LdaGlobal -> visitLdaGlobal(opcode.name)
            is StaGlobal -> visitStaGlobal(opcode.name)
            is LdaCurrentEnv -> visitLdaCurrentEnv(opcode.name)
            is StaCurrentEnv -> visitStaCurrentEnv(opcode.name)
            is LdaEnv -> visitLdaEnv(opcode.name, opcode.offset)
            is StaEnv -> visitStaEnv(opcode.name, opcode.offset)
            PushLexicalEnv -> visitPushLexicalEnv()
            PopLexicalEnv -> visitPopEnv()

            is Call -> visitCall(opcode.targetReg, opcode.receiverReg, opcode.argumentRegs)
            is CallWithArgArray -> visitCallWithArgArray(opcode.targetReg, opcode.receiverReg, opcode.argumentsReg)

            is Construct -> visitConstruct(opcode.targetReg, opcode.newTargetReg, opcode.argumentRegs)
            is ConstructWithArgArray -> visitConstructWithArgArray(opcode.targetReg, opcode.newTargetReg, opcode.argumentsReg)

            is TestEqual -> visitTestEqual(opcode.lhsReg)
            is TestNotEqual -> visitTestNotEqual(opcode.lhsReg)
            is TestEqualStrict -> visitTestEqualStrict(opcode.lhsReg)
            is TestNotEqualStrict -> visitTestNotEqualStrict(opcode.lhsReg)
            is TestLessThan -> visitTestLessThan(opcode.lhsReg)
            is TestGreaterThan -> visitTestGreaterThan(opcode.lhsReg)
            is TestLessThanOrEqual -> visitTestLessThanOrEqual(opcode.lhsReg)
            is TestGreaterThanOrEqual -> visitTestGreaterThanOrEqual(opcode.lhsReg)
            is TestReferenceEqual -> visitTestReferenceEqual(opcode.lhsReg)
            is TestInstanceOf -> visitTestInstanceOf(opcode.lhsReg)
            is TestIn -> visitTestIn(opcode.lhsReg)
            TestNullish -> visitTestNullish()
            TestNull -> visitTestNull()
            TestUndefined -> visitTestUndefined()

            ToBoolean -> visitToBoolean()
            ToNumber -> visitToNumber()
            ToNumeric -> visitToNumeric()
            ToObject -> visitToObject()
            ToString -> visitToString()

            is JumpIfTrue -> visitJumpIfTrue(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfNull -> visitJumpIfNull(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfUndefined -> visitJumpIfUndefined(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfNullish -> visitJumpIfNullish(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfObject -> visitJumpIfObject(opcode.ifBlock, opcode.elseBlock!!)
            is JumpFromTable -> visitJumpFromTable(opcode.table)
            is Jump -> visitJump(opcode.ifBlock)

            Return -> visitReturn()
            Throw -> visitThrow()
            is ThrowConstReassignment -> visitThrowConstReassignment(opcode.nameIndex)
            is ThrowUseBeforeInitIfEmpty -> visitThrowUseBeforeInitIfEmpty(opcode.nameIndex)

            is DefineGetterProperty -> visitDefineGetterProperty(opcode.objectReg, opcode.nameReg, opcode.methodReg)
            is DefineSetterProperty -> visitDefineSetterProperty(opcode.objectReg, opcode.nameReg, opcode.methodReg)
            is DeclareGlobals -> visitDeclareGlobals(opcode.declarationsIndex)
            CreateMappedArgumentsObject -> visitCreateMappedArgumentsObject()
            CreateUnmappedArgumentsObject -> visitCreateUnmappedArgumentsObject()
            GetIterator -> visitGetIterator()
            IteratorNext -> visitIteratorNext()
            IteratorResultDone -> visitIteratorResultDone()
            IteratorResultValue -> visitIteratorResultValue()
            ForInEnumerate -> visitForInEnumerate()
            is CreateClosure -> visitCreateClosure(opcode.functionInfoIndex)
            DebugBreakpoint -> visitDebugBreakpoint()
        }
    }

    abstract fun visitLdaTrue()

    abstract fun visitLdaFalse()

    abstract fun visitLdaUndefined()

    abstract fun visitLdaNull()

    abstract fun visitLdaZero()

    abstract fun visitLdaConstant(index: Index)

    abstract fun visitLdaInt(int: Literal)

    abstract fun visitLdar(reg: Register)

    abstract fun visitStar(reg: Register)

    abstract fun visitLdaNamedProperty(objectReg: Register, nameIndex: Index)

    abstract fun visitLdaKeyedProperty(objectReg: Register)

    abstract fun visitStaNamedProperty(objectReg: Register, nameIndex: Index)

    abstract fun visitStaKeyedProperty(objectReg: Register, nameReg: Register)

    abstract fun visitCreateArray()

    abstract fun visitStaArrayIndex(arrayReg: Register, index: Literal)

    abstract fun visitStaArray(arrayReg: Register, indexReg: Register)

    abstract fun visitCreateObject()

    abstract fun visitAdd(lhsReg: Register)

    abstract fun visitSub(lhsReg: Register)

    abstract fun visitMul(lhsReg: Register)

    abstract fun visitDiv(lhsReg: Register)

    abstract fun visitMod(lhsReg: Register)

    abstract fun visitExp(lhsReg: Register)

    abstract fun visitBitwiseOr(lhsReg: Register)

    abstract fun visitBitwiseXor(lhsReg: Register)

    abstract fun visitBitwiseAnd(lhsReg: Register)

    abstract fun visitShiftLeft(lhsReg: Register)

    abstract fun visitShiftRight(lhsReg: Register)

    abstract fun visitShiftRightUnsigned(lhsReg: Register)

    abstract fun visitInc()

    abstract fun visitDec()

    abstract fun visitNegate()

    abstract fun visitBitwiseNot()

    abstract fun visitStringAppend(lhsStringReg: Register)

    abstract fun visitToBooleanLogicalNot()

    abstract fun visitLogicalNot()

    abstract fun visitTypeOf()

    abstract fun visitDeletePropertySloppy(objectReg: Register)

    abstract fun visitDeletePropertyStrict(objectReg: Register)

    abstract fun visitLdaGlobal(name: Index)

    abstract fun visitStaGlobal(name: Index)

    abstract fun visitLdaCurrentEnv(name: Index)

    abstract fun visitStaCurrentEnv(name: Index)

    abstract fun visitLdaEnv(name: Index, offset: Literal)

    abstract fun visitStaEnv(name: Index, offset: Literal)

    abstract fun visitPushLexicalEnv()

    abstract fun visitPopEnv()

    abstract fun visitCall(targetReg: Register, receiverReg: Register, argumentRegs: List<Register>)

    abstract fun visitCallWithArgArray(targetReg: Register, receiverReg: Register, argumentReg: Register)

    abstract fun visitConstruct(targetReg: Register, newTargetReg: Register, argumentRegs: List<Register>)

    abstract fun visitConstructWithArgArray(targetReg: Register, newTargetReg: Register, argumentReg: Register)

    abstract fun visitTestEqual(lhsReg: Register)

    abstract fun visitTestNotEqual(lhsReg: Register)

    abstract fun visitTestEqualStrict(lhsReg: Register)

    abstract fun visitTestNotEqualStrict(lhsReg: Register)

    abstract fun visitTestLessThan(lhsReg: Register)

    abstract fun visitTestGreaterThan(lhsReg: Register)

    abstract fun visitTestLessThanOrEqual(lhsReg: Register)

    abstract fun visitTestGreaterThanOrEqual(lhsReg: Register)

    abstract fun visitTestReferenceEqual(lhsReg: Register)

    abstract fun visitTestInstanceOf(lhsReg: Register)

    abstract fun visitTestIn(lhsReg: Register)

    abstract fun visitTestNullish()

    abstract fun visitTestNull()

    abstract fun visitTestUndefined()

    abstract fun visitToBoolean()

    abstract fun visitToNumber()

    abstract fun visitToNumeric()

    abstract fun visitToObject()

    abstract fun visitToString()

    abstract fun visitJumpIfTrue(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfToBooleanTrue(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfNull(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfUndefined(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfNullish(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfObject(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpFromTable(table: Index)

    abstract fun visitJump(ifBlock: Block)
    
    abstract fun visitForInEnumerate()

    abstract fun visitReturn()

    abstract fun visitThrow()

    abstract fun visitThrowConstReassignment(nameIndex: Index)

    abstract fun visitThrowUseBeforeInitIfEmpty(nameIndex: Index)

    abstract fun visitDefineGetterProperty(objectReg: Register, nameReg: Register, methodReg: Register)

    abstract fun visitDefineSetterProperty(objectReg: Register, nameReg: Register, methodReg: Register)

    abstract fun visitDeclareGlobals(declarationsIndex: Index)

    abstract fun visitCreateMappedArgumentsObject()

    abstract fun visitCreateUnmappedArgumentsObject()

    abstract fun visitGetIterator()

    abstract fun visitIteratorNext()

    abstract fun visitIteratorResultDone()

    abstract fun visitIteratorResultValue()

    abstract fun visitCreateClosure(functionInfoIndex: Int)

    abstract fun visitDebugBreakpoint()
}
