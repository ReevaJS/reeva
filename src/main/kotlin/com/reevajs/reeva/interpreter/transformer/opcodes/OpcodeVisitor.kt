package com.reevajs.reeva.interpreter.transformer.opcodes

interface OpcodeVisitor {
    fun visit(opcode: Opcode) {
        when (opcode) {
            PushNull -> visitPushNull()
            PushUndefined -> visitPushUndefined()
            is PushConstant -> visitPushConstant(opcode)
            Pop -> visitPop()
            Dup -> visitDup()
            DupX1 -> visitDupX1()
            DupX2 -> visitDupX2()
            Swap -> visitSwap()
            is LoadInt -> visitLoadInt(opcode)
            is StoreInt -> visitStoreInt(opcode)
            is IncInt -> visitIncInt(opcode)
            is LoadValue -> visitLoadValue(opcode)
            is StoreValue -> visitStoreValue(opcode)
            Add -> visitAdd()
            Sub -> visitSub()
            Mul -> visitMul()
            Div -> visitDiv()
            Exp -> visitExp()
            Mod -> visitMod()
            BitwiseAnd -> visitBitwiseAnd()
            BitwiseOr -> visitBitwiseOr()
            BitwiseXor -> visitBitwiseXor()
            ShiftLeft -> visitShiftLeft()
            ShiftRight -> visitShiftRight()
            ShiftRightUnsigned -> visitShiftRightUnsigned()
            TestEqualStrict -> visitTestEqualStrict()
            TestNotEqualStrict -> visitTestNotEqualStrict()
            TestEqual -> visitTestEqual()
            TestNotEqual -> visitTestNotEqual()
            TestLessThan -> visitTestLessThan()
            TestLessThanOrEqual -> visitTestLessThanOrEqual()
            TestGreaterThan -> visitTestGreaterThan()
            TestGreaterThanOrEqual -> visitTestGreaterThanOrEqual()
            TestInstanceOf -> visitTestInstanceOf()
            TestIn -> visitTestIn()
            TypeOf -> visitTypeOf()
            ToNumber -> visitToNumber()
            ToNumeric -> visitToNumeric()
            ToString -> visitToString()
            Negate -> visitNegate()
            BitwiseNot -> visitBitwiseNot()
            ToBooleanLogicalNot -> visitToBooleanLogicalNot()
            Inc -> visitInc()
            Dec -> visitDec()
            LoadKeyedProperty -> visitLoadKeyedProperty()
            StoreKeyedProperty -> visitStoreKeyedProperty()
            is LoadNamedProperty -> visitLoadNamedProperty(opcode)
            is StoreNamedProperty -> visitStoreNamedProperty(opcode)
            CreateObject -> visitCreateObject()
            CreateArray -> visitCreateArray()
            is StoreArray -> visitStoreArray(opcode)
            is StoreArrayIndexed -> visitStoreArrayIndexed(opcode)
            DeletePropertyStrict -> visitDeletePropertyStrict()
            DeletePropertySloppy -> visitDeletePropertySloppy()
            GetIterator -> visitGetIterator()
            IteratorNext -> visitIteratorNext()
            IteratorResultDone -> visitIteratorResultDone()
            IteratorResultValue -> visitIteratorResultValue()
            is Call -> visitCall(opcode)
            CallArray -> visitCallArray()
            is Construct -> visitConstruct(opcode)
            ConstructArray -> visitConstructArray()
            is DeclareGlobals -> visitDeclareGlobals(opcode)
            is PushDeclarativeEnvRecord -> visitPushDeclarativeEnvRecord(opcode)
            PopEnvRecord -> visitPopEnvRecord()
            is LoadGlobal -> visitLoadGlobal(opcode)
            is StoreGlobal -> visitStoreGlobal(opcode)
            is LoadCurrentEnvSlot -> visitLoadCurrentEnvSlot(opcode)
            is StoreCurrentEnvSlot -> visitStoreCurrentEnvSlot(opcode)
            is LoadEnvSlot -> visitLoadEnvSlot(opcode)
            is StoreEnvSlot -> visitStoreEnvSlot(opcode)
            is Jump -> visitJump(opcode)
            is JumpIfTrue -> visitJumpIfTrue(opcode)
            is JumpIfFalse -> visitJumpIfFalse(opcode)
            is JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode)
            is JumpIfToBooleanFalse -> visitJumpIfToBooleanFalse(opcode)
            is JumpIfUndefined -> visitJumpIfUndefined(opcode)
            is JumpIfNotUndefined -> visitJumpIfNotUndefined(opcode)
            is JumpIfNotNullish -> visitJumpIfNotNullish(opcode)
            is JumpIfNotEmpty -> visitJumpIfNotEmpty(opcode)
            is CreateRegExpObject -> visitCreateRegExpObject(opcode)
            is CreateTemplateLiteral -> visitCreateTemplateLiteral(opcode)
            ForInEnumerate -> visitForInEnumerate()
            is CreateClosure -> visitCreateClosure(opcode)
            is CreateClassConstructor -> visitCreateClassConstructor(opcode)
            is CreateGeneratorClosure -> visitCreateGeneratorClosure(opcode)
            is CreateAsyncClosure -> visitCreateAsyncClosure(opcode)
            is CreateAsyncGeneratorClosure -> visitCreateAsyncGeneratorClosure(opcode)
            CreateRestParam -> visitCreateRestParam()
            GetSuperConstructor -> visitGetSuperConstructor()
            CreateUnmappedArgumentsObject -> visitCreateUnmappedArgumentsObject()
            CreateMappedArgumentsObject -> visitCreateMappedArgumentsObject()
            is ThrowConstantError -> visitThrowConstantError(opcode)
            ThrowSuperNotInitializedIfEmpty -> visitThrowSuperNotInitializedIfEmpty()
            PushClosure -> visitPushClosure()
            Throw -> visitThrow()
            Return -> visitReturn()
        }
    }

    fun visitPushNull()

    fun visitPushUndefined()

    fun visitPushConstant(opcode: PushConstant)

    fun visitPop()

    fun visitDup()

    fun visitDupX1()

    fun visitDupX2()

    fun visitSwap()

    fun visitLoadInt(opcode: LoadInt)

    fun visitStoreInt(opcode: StoreInt)

    fun visitIncInt(opcode: IncInt)

    fun visitLoadValue(opcode: LoadValue)

    fun visitStoreValue(opcode: StoreValue)

    fun visitAdd()

    fun visitSub()

    fun visitMul()

    fun visitDiv()

    fun visitExp()

    fun visitMod()

    fun visitBitwiseAnd()

    fun visitBitwiseOr()

    fun visitBitwiseXor()

    fun visitShiftLeft()

    fun visitShiftRight()

    fun visitShiftRightUnsigned()

    fun visitTestEqualStrict()

    fun visitTestNotEqualStrict()

    fun visitTestEqual()

    fun visitTestNotEqual()

    fun visitTestLessThan()

    fun visitTestLessThanOrEqual()

    fun visitTestGreaterThan()

    fun visitTestGreaterThanOrEqual()

    fun visitTestInstanceOf()

    fun visitTestIn()

    fun visitTypeOf()

    fun visitToNumber()

    fun visitToNumeric()

    fun visitToString()

    fun visitNegate()

    fun visitBitwiseNot()

    fun visitToBooleanLogicalNot()

    fun visitInc()

    fun visitDec()

    fun visitLoadKeyedProperty()

    fun visitStoreKeyedProperty()

    fun visitLoadNamedProperty(opcode: LoadNamedProperty)

    fun visitStoreNamedProperty(opcode: StoreNamedProperty)

    fun visitCreateObject()

    fun visitCreateArray()

    fun visitStoreArray(opcode: StoreArray)

    fun visitStoreArrayIndexed(opcode: StoreArrayIndexed)

    fun visitDeletePropertyStrict()

    fun visitDeletePropertySloppy()

    fun visitGetIterator()

    fun visitIteratorNext()

    fun visitIteratorResultDone()

    fun visitIteratorResultValue()

    fun visitCall(opcode: Call)

    fun visitCallArray()

    fun visitConstruct(opcode: Construct)

    fun visitConstructArray()

    fun visitDeclareGlobals(opcode: DeclareGlobals)

    fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord)

    fun visitPopEnvRecord()

    fun visitLoadGlobal(opcode: LoadGlobal)

    fun visitStoreGlobal(opcode: StoreGlobal)

    fun visitLoadCurrentEnvSlot(opcode: LoadCurrentEnvSlot)

    fun visitStoreCurrentEnvSlot(opcode: StoreCurrentEnvSlot)

    fun visitLoadEnvSlot(opcode: LoadEnvSlot)

    fun visitStoreEnvSlot(opcode: StoreEnvSlot)

    fun visitJump(opcode: Jump)

    fun visitJumpIfTrue(opcode: JumpIfTrue)

    fun visitJumpIfFalse(opcode: JumpIfFalse)

    fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue)

    fun visitJumpIfToBooleanFalse(opcode: JumpIfToBooleanFalse)

    fun visitJumpIfUndefined(opcode: JumpIfUndefined)

    fun visitJumpIfNotUndefined(opcode: JumpIfNotUndefined)

    fun visitJumpIfNotNullish(opcode: JumpIfNotNullish)

    fun visitJumpIfNotEmpty(opcode: JumpIfNotEmpty)

    fun visitCreateRegExpObject(opcode: CreateRegExpObject)

    fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral)

    fun visitForInEnumerate()

    fun visitCreateClosure(opcode: CreateClosure)

    fun visitCreateClassConstructor(opcode: CreateClassConstructor)

    fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure)

    fun visitCreateAsyncClosure(opcode: CreateAsyncClosure)

    fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure)

    fun visitCreateRestParam()

    fun visitGetSuperConstructor()

    fun visitCreateUnmappedArgumentsObject()

    fun visitCreateMappedArgumentsObject()

    fun visitThrowConstantError(opcode: ThrowConstantError)

    fun visitThrowSuperNotInitializedIfEmpty()

    fun visitPushClosure()

    fun visitThrow()

    fun visitReturn()
}
