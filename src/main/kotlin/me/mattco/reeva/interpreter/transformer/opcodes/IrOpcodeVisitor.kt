package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.transformer.Block

abstract class IrOpcodeVisitor {
    open fun visit(opcode: Opcode) {
        when (opcode) {
            LdaEmpty -> visitLdaEmpty()
            LdaUndefined -> visitLdaUndefined()
            LdaNull -> visitLdaNull()
            LdaTrue -> visitLdaTrue()
            LdaFalse -> visitLdaFalse()
            LdaZero -> visitLdaZero()
            is LdaConstant -> visitLdaConstant(opcode.index)
            is LdaInt -> visitLdaInt(opcode.int)
            LdaClosure -> visitLdaClosure()

            is Ldar -> visitLdar(opcode.reg)
            is Star -> visitStar(opcode.reg)
            is LdaNamedProperty -> visitLdaNamedProperty(opcode.objectReg, opcode.nameIndex, opcode.typeIndex)
            is LdaKeyedProperty -> visitLdaKeyedProperty(opcode.objectReg, opcode.typeIndex)
            is StaNamedProperty -> visitStaNamedProperty(opcode.objectReg, opcode.nameIndex)
            is StaKeyedProperty -> visitStaKeyedProperty(opcode.objectReg, opcode.nameReg)

            CreateArray -> visitCreateArray()
            is StaArrayIndex -> visitStaArrayIndex(opcode.arrayReg, opcode.index)
            is StaArray -> visitStaArray(opcode.arrayReg, opcode.indexReg)
            CreateObject -> visitCreateObject()
            is CopyObjectExcludingProperties -> visitCopyObjectExcludingProperties(opcode.targetReg, opcode.excludedPropertyNames)

            is Add -> visitAdd(opcode.lhsReg, opcode.feedbackIndex)
            is Sub -> visitSub(opcode.lhsReg, opcode.feedbackIndex)
            is Mul -> visitMul(opcode.lhsReg, opcode.feedbackIndex)
            is Div -> visitDiv(opcode.lhsReg, opcode.feedbackIndex)
            is Mod -> visitMod(opcode.lhsReg, opcode.feedbackIndex)
            is Exp -> visitExp(opcode.lhsReg, opcode.feedbackIndex)
            is BitwiseOr -> visitBitwiseOr(opcode.lhsReg, opcode.feedbackIndex)
            is BitwiseXor -> visitBitwiseXor(opcode.lhsReg, opcode.feedbackIndex)
            is BitwiseAnd -> visitBitwiseAnd(opcode.lhsReg, opcode.feedbackIndex)
            is ShiftLeft -> visitShiftLeft(opcode.lhsReg, opcode.feedbackIndex)
            is ShiftRight -> visitShiftRight(opcode.lhsReg, opcode.feedbackIndex)
            is ShiftRightUnsigned -> visitShiftRightUnsigned(opcode.lhsReg, opcode.feedbackIndex)

            Inc -> visitInc()
            Dec -> visitDec()
            Negate -> visitNegate()
            BitwiseNot -> visitBitwiseNot()

            is StringAppend -> visitStringAppend(opcode.lhsStringReg)
            ToBooleanLogicalNot -> visitToBooleanLogicalNot()
            TypeOf -> visitTypeOf()
            is DeletePropertySloppy -> visitDeletePropertySloppy(opcode.objectReg)
            is DeletePropertyStrict -> visitDeletePropertyStrict(opcode.objectReg)

            is LdaGlobal -> visitLdaGlobal(opcode.name)
            is StaGlobal -> visitStaGlobal(opcode.name)
            is LdaCurrentRecordSlot -> visitLdaCurrentRecordSlot(opcode.slot)
            is StaCurrentRecordSlot -> visitStaCurrentRecordSlot(opcode.slot)
            is LdaRecordSlot -> visitLdaRecordSlot(opcode.slot, opcode.distance)
            is StaRecordSlot -> visitStaRecordSlot(opcode.slot, opcode.distance)
            is PushWithEnvRecord -> visitPushWithEnvRecord()
            is PushDeclarativeEnvRecord -> visitPushDeclarativeEnvRecord(opcode.numSlots)
            PopEnvRecord -> visitPopEnvRecord()

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
            is TestInstanceOf -> visitTestInstanceOf(opcode.lhsReg)
            is TestIn -> visitTestIn(opcode.lhsReg)

            ToNumber -> visitToNumber()
            ToNumeric -> visitToNumeric()
            ToString -> visitToString()

            is JumpAbsolute -> visitJumpAbsolute(opcode.ifBlock)
            is JumpIfTrue -> visitJumpIfTrue(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfEmpty -> visitJumpIfEmpty(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfUndefined -> visitJumpIfUndefined(opcode.ifBlock, opcode.elseBlock!!)
            is JumpIfNullish -> visitJumpIfNullish(opcode.ifBlock, opcode.elseBlock!!)
            is JumpFromTable -> visitJumpFromTable(opcode.table)

            Return -> visitReturn()
            is Yield -> visitYield(opcode.continuationBlock)
            is Await -> visitAwait(opcode.continuationBlock)
            Throw -> visitThrow()

            is CreateClass -> visitCreateClass(opcode.classDescriptorIndex, opcode.constructor, opcode.superClass, opcode.args)
            is CreateClassConstructor -> visitCreateClassConstructor(opcode.functionInfoIndex)
            GetSuperConstructor -> visitGetSuperConstructor()
            GetSuperBase -> visitGetSuperBase()
            is ThrowSuperNotInitializedIfEmpty -> visitThrowSuperNotInitializedIfEmpty()
            is ThrowSuperInitializedIfNotEmpty -> visitThrowSuperInitializedIfNotEmpty()

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
            is CreateGeneratorClosure -> visitCreateGeneratorClosure(opcode.functionInfoIndex)
            is CreateAsyncClosure -> visitCreateAsyncClosure(opcode.functionInfoIndex)
            CreateRestParam -> visitCreateRestParam()
            DebugBreakpoint -> visitDebugBreakpoint()
        }
    }

    abstract fun visitLdaEmpty()

    abstract fun visitLdaUndefined()

    abstract fun visitLdaNull()

    abstract fun visitLdaTrue()

    abstract fun visitLdaFalse()

    abstract fun visitLdaZero()

    abstract fun visitLdaConstant(index: ConstantIndex)

    abstract fun visitLdaInt(int: Literal)

    abstract fun visitLdaClosure()

    abstract fun visitLdar(reg: Register)

    abstract fun visitStar(reg: Register)

    abstract fun visitLdaNamedProperty(objectReg: Register, nameIndex: ConstantIndex, typeIndex: FeedbackIndex)

    abstract fun visitLdaKeyedProperty(objectReg: Register, typeIndex: FeedbackIndex)

    abstract fun visitStaNamedProperty(objectReg: Register, nameIndex: ConstantIndex)

    abstract fun visitStaKeyedProperty(objectReg: Register, nameReg: Register)

    abstract fun visitCreateArray()

    abstract fun visitStaArrayIndex(arrayReg: Register, index: Literal)

    abstract fun visitStaArray(arrayReg: Register, indexReg: Register)

    abstract fun visitCreateObject()

    abstract fun visitCopyObjectExcludingProperties(targetReg: Register, excludedPropertyNames: List<Register>)

    abstract fun visitAdd(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitSub(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitMul(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitDiv(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitMod(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitExp(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitBitwiseOr(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitBitwiseXor(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitBitwiseAnd(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitShiftLeft(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitShiftRight(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitShiftRightUnsigned(lhsReg: Register, feedbackIndex: FeedbackIndex)

    abstract fun visitInc()

    abstract fun visitDec()

    abstract fun visitNegate()

    abstract fun visitBitwiseNot()

    abstract fun visitStringAppend(lhsStringReg: Register)

    abstract fun visitToBooleanLogicalNot()

    abstract fun visitTypeOf()

    abstract fun visitDeletePropertySloppy(objectReg: Register)

    abstract fun visitDeletePropertyStrict(objectReg: Register)

    abstract fun visitLdaGlobal(name: ConstantIndex)

    abstract fun visitStaGlobal(name: ConstantIndex)

    abstract fun visitLdaCurrentRecordSlot(slot: Literal)

    abstract fun visitStaCurrentRecordSlot(slot: Literal)

    abstract fun visitLdaRecordSlot(slot: Literal, distance: Literal)

    abstract fun visitStaRecordSlot(slot: Literal, distance: Literal)

    abstract fun visitPushWithEnvRecord()

    abstract fun visitPushDeclarativeEnvRecord(numSlots: Literal)

    abstract fun visitPopEnvRecord()

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

    abstract fun visitTestInstanceOf(lhsReg: Register)

    abstract fun visitTestIn(lhsReg: Register)

    abstract fun visitToNumber()

    abstract fun visitToNumeric()

    abstract fun visitToString()

    abstract fun visitJumpIfTrue(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfToBooleanTrue(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfEmpty(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfUndefined(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpIfNullish(ifBlock: Block, elseBlock: Block)

    abstract fun visitJumpFromTable(table: ConstantIndex)

    abstract fun visitJumpAbsolute(block: Block)
    
    abstract fun visitForInEnumerate()

    abstract fun visitReturn()

    abstract fun visitYield(continuationBlock: Block)

    abstract fun visitAwait(continuationBlock: Block)

    abstract fun visitThrow()

    abstract fun visitCreateClass(classDescriptorIndex: ConstantIndex, constructor: Register, superClass: Register, args: List<Register>)

    abstract fun visitCreateClassConstructor(functionInfoIndex: Int)

    abstract fun visitGetSuperConstructor()

    abstract fun visitGetSuperBase()

    abstract fun visitThrowSuperNotInitializedIfEmpty()

    abstract fun visitThrowSuperInitializedIfNotEmpty()

    abstract fun visitDefineGetterProperty(objectReg: Register, nameReg: Register, methodReg: Register)

    abstract fun visitDefineSetterProperty(objectReg: Register, nameReg: Register, methodReg: Register)

    abstract fun visitDeclareGlobals(declarationsIndex: ConstantIndex)

    abstract fun visitCreateMappedArgumentsObject()

    abstract fun visitCreateUnmappedArgumentsObject()

    abstract fun visitGetIterator()

    abstract fun visitIteratorNext()

    abstract fun visitIteratorResultDone()

    abstract fun visitIteratorResultValue()

    abstract fun visitCreateClosure(functionInfoIndex: Int)

    abstract fun visitCreateGeneratorClosure(functionInfoIndex: Int)

    abstract fun visitCreateAsyncClosure(functionInfoIndex: Int)

    abstract fun visitCreateRestParam()

    abstract fun visitDebugBreakpoint()
}
