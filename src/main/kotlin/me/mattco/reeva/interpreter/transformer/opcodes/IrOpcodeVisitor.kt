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
            PopEnv -> visitPopEnv()

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
            is JumpIfNull -> visitJumpIfNull(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfUndefined -> visitJumpIfUndefined(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfNullish -> visitJumpIfNullish(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfObject -> visitJumpIfObject(opcode.ifBlock, opcode.elseBlock!!)
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
            is CreateClosure -> visitCreateClosure(opcode.functionInfoIndex)
            DebugBreakpoint -> visitDebugBreakpoint()
        }
    }

    open fun visitLdaTrue() { }

    open fun visitLdaFalse() { }

    open fun visitLdaUndefined() { }

    open fun visitLdaNull() { }

    open fun visitLdaZero() { }

    open fun visitLdaConstant(index: Index) { }

    open fun visitLdaInt(int: Literal) { }

    open fun visitLdar(reg: Register) { }

    open fun visitStar(reg: Register) { }

    open fun visitLdaNamedProperty(objectReg: Register, nameIndex: Index) { }

    open fun visitLdaKeyedProperty(objectReg: Register) { }

    open fun visitStaNamedProperty(objectReg: Register, nameIndex: Index) { }

    open fun visitStaKeyedProperty(objectReg: Register, nameReg: Register) { }

    open fun visitCreateArray() { }

    open fun visitStaArrayIndex(arrayReg: Register, index: Literal) { }

    open fun visitStaArray(arrayReg: Register, indexReg: Register) { }

    open fun visitCreateObject() { }

    open fun visitAdd(lhsReg: Register) { }

    open fun visitSub(lhsReg: Register) { }

    open fun visitMul(lhsReg: Register) { }

    open fun visitDiv(lhsReg: Register) { }

    open fun visitMod(lhsReg: Register) { }

    open fun visitExp(lhsReg: Register) { }

    open fun visitBitwiseOr(lhsReg: Register) { }

    open fun visitBitwiseXor(lhsReg: Register) { }

    open fun visitBitwiseAnd(lhsReg: Register) { }

    open fun visitShiftLeft(lhsReg: Register) { }

    open fun visitShiftRight(lhsReg: Register) { }

    open fun visitShiftRightUnsigned(lhsReg: Register) { }

    open fun visitInc() { }

    open fun visitDec() { }

    open fun visitNegate() { }

    open fun visitBitwiseNot() { }

    open fun visitStringAppend(lhsStringReg: Register) { }

    open fun visitToBooleanLogicalNot() { }

    open fun visitLogicalNot() { }

    open fun visitTypeOf() { }

    open fun visitDeletePropertySloppy(objectReg: Register) { }

    open fun visitDeletePropertyStrict(objectReg: Register) { }

    open fun visitLdaGlobal(name: Index) { }

    open fun visitStaGlobal(name: Index) { }

    open fun visitLdaCurrentEnv(name: Index) { }

    open fun visitStaCurrentEnv(name: Index) { }

    open fun visitLdaEnv(name: Index, offset: Literal) { }

    open fun visitStaEnv(name: Index, offset: Literal) { }

    open fun visitPushLexicalEnv() { }

    open fun visitPopEnv() { }

    open fun visitCall(targetReg: Register, receiverReg: Register, argumentRegs: List<Register>) { }

    open fun visitCallWithArgArray(targetReg: Register, receiverReg: Register, argumentReg: Register) { }

    open fun visitConstruct(targetReg: Register, newTargetReg: Register, argumentRegs: List<Register>) { }

    open fun visitConstructWithArgArray(targetReg: Register, newTargetReg: Register, argumentReg: Register) { }

    open fun visitTestEqual(lhsReg: Register) { }

    open fun visitTestNotEqual(lhsReg: Register) { }

    open fun visitTestEqualStrict(lhsReg: Register) { }

    open fun visitTestNotEqualStrict(lhsReg: Register) { }

    open fun visitTestLessThan(lhsReg: Register) { }

    open fun visitTestGreaterThan(lhsReg: Register) { }

    open fun visitTestLessThanOrEqual(lhsReg: Register) { }

    open fun visitTestGreaterThanOrEqual(lhsReg: Register) { }

    open fun visitTestReferenceEqual(lhsReg: Register) { }

    open fun visitTestInstanceOf(lhsReg: Register) { }

    open fun visitTestIn(lhsReg: Register) { }

    open fun visitTestNullish() { }

    open fun visitTestNull() { }

    open fun visitTestUndefined() { }

    open fun visitToBoolean() { }

    open fun visitToNumber() { }

    open fun visitToNumeric() { }

    open fun visitToObject() { }

    open fun visitToString() { }

    open fun visitJumpIfTrue(ifBlock: Block, elseBlock: Block) { }

    open fun visitJumpIfNull(ifBlock: Block, elseBlock: Block) { }

    open fun visitJumpIfUndefined(ifBlock: Block, elseBlock: Block) { }

    open fun visitJumpIfNullish(ifBlock: Block, elseBlock: Block) { }

    open fun visitJumpIfObject(ifBlock: Block, elseBlock: Block) { }

    open fun visitJump(ifBlock: Block) { }

    open fun visitReturn() { }

    open fun visitThrow() { }

    open fun visitThrowConstReassignment(nameIndex: Index) { }

    open fun visitThrowUseBeforeInitIfEmpty(nameIndex: Index) { }

    open fun visitDefineGetterProperty(objectReg: Register, nameReg: Register, methodReg: Register) { }

    open fun visitDefineSetterProperty(objectReg: Register, nameReg: Register, methodReg: Register) { }

    open fun visitDeclareGlobals(declarationsIndex: Index) { }

    open fun visitCreateMappedArgumentsObject() { }

    open fun visitCreateUnmappedArgumentsObject() { }

    open fun visitGetIterator() { }

    open fun visitIteratorNext() { }

    open fun visitIteratorResultDone() { }

    open fun visitIteratorResultValue() { }

    open fun visitCreateClosure(functionInfoIndex: Int) { }

    open fun visitDebugBreakpoint() { }
}
