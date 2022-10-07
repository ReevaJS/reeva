package com.reevajs.reeva.transformer.opcodes

import com.reevajs.reeva.parsing.lexer.TokenType

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
            is TypeOfGlobal -> visitTypeOfGlobal(opcode)
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
            is DeclareGlobalVars -> visitDeclareGlobalVars(opcode)
            is DeclareGlobalFunc -> visitDeclareGlobalFunc(opcode)
            is PushDeclarativeEnvRecord -> visitPushDeclarativeEnvRecord(opcode)
            is PushModuleEnvRecord -> visitPushModuleEnvRecord()
            PopEnvRecord -> visitPopEnvRecord()
            is LoadGlobal -> visitLoadGlobal(opcode)
            is StoreGlobal -> visitStoreGlobal(opcode)
            is LoadCurrentEnvName -> visitLoadCurrentEnvName(opcode)
            is StoreCurrentEnvName -> visitStoreCurrentEnvName(opcode)
            is LoadEnvName -> visitLoadEnvName(opcode)
            is StoreEnvName -> visitStoreEnvName(opcode)
            is Jump -> visitJump(opcode)
            is JumpIfTrue -> visitJumpIfTrue(opcode)
            is JumpIfFalse -> visitJumpIfFalse(opcode)
            is JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode)
            is JumpIfToBooleanFalse -> visitJumpIfToBooleanFalse(opcode)
            is JumpIfUndefined -> visitJumpIfUndefined(opcode)
            is JumpIfNotUndefined -> visitJumpIfNotUndefined(opcode)
            is JumpIfNotNullish -> visitJumpIfNotNullish(opcode)
            is JumpIfNullish -> visitJumpIfNullish(opcode)
            is JumpIfNotEmpty -> visitJumpIfNotEmpty(opcode)
            is CreateRegExpObject -> visitCreateRegExpObject(opcode)
            is CreateTemplateLiteral -> visitCreateTemplateLiteral(opcode)
            ForInEnumerate -> visitForInEnumerate()
            is CreateClosure -> visitCreateClosure(opcode)
            is CreateGeneratorClosure -> visitCreateGeneratorClosure(opcode)
            is CreateAsyncClosure -> visitCreateAsyncClosure(opcode)
            is CreateAsyncGeneratorClosure -> visitCreateAsyncGeneratorClosure(opcode)
            GetSuperConstructor -> visitGetSuperConstructor()
            CreateUnmappedArgumentsObject -> visitCreateUnmappedArgumentsObject()
            CreateMappedArgumentsObject -> visitCreateMappedArgumentsObject()
            is ThrowConstantReassignmentError -> visitThrowConstantReassignmentError(opcode)
            is ThrowLexicalAccessError -> visitThrowLexicalAccessError(opcode)
            ThrowSuperNotInitializedIfEmpty -> visitThrowSuperNotInitializedIfEmpty()
            PushClosure -> visitPushClosure()
            Throw -> visitThrow()
            Return -> visitReturn()
            Yield -> visitYield()
            Await -> visitAwait()
            DefineGetterProperty -> visitDefineGetterProperty()
            DefineSetterProperty -> visitDefineSetterProperty()
            GetSuperBase -> visitGetSuperBase()
            is PushBigInt -> visitPushBigInt(opcode)
            PushEmpty -> visitPushEmpty()
            is CopyObjectExcludingProperties -> visitCopyObjectExcludingProperties(opcode)
            is LoadBoolean -> visitLoadBoolean(opcode)
            is StoreBoolean -> visitStoreBoolean(opcode)
            PushJVMFalse -> visitPushJVMFalse()
            PushJVMTrue -> visitPushJVMTrue()
            is PushJVMInt -> visitPushJVMInt(opcode)
            is CreateMethod -> visitCreateClassConstructor(opcode)
            CreateClass -> visitCreateClass()
            is AttachClassMethod -> visitAttachClassMethod(opcode)
            is AttachComputedClassMethod -> visitAttachComputedClassMethod(opcode)
            FinalizeClass -> visitFinalizeClass()
            is LoadModuleVar -> visitLoadModuleVar(opcode)
            is StoreModuleVar -> visitStoreModuleVar(opcode)
            CollectRestArgs -> visitCollectRestArgs()
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

    fun visitTypeOfGlobal(opcode: TypeOfGlobal)

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

    fun visitDeclareGlobalVars(opcode: DeclareGlobalVars)

    fun visitDeclareGlobalFunc(opcode: DeclareGlobalFunc)

    fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord)

    fun visitPushModuleEnvRecord()

    fun visitPopEnvRecord()

    fun visitLoadGlobal(opcode: LoadGlobal)

    fun visitStoreGlobal(opcode: StoreGlobal)

    fun visitLoadCurrentEnvName(opcode: LoadCurrentEnvName)

    fun visitStoreCurrentEnvName(opcode: StoreCurrentEnvName)

    fun visitLoadEnvName(opcode: LoadEnvName)

    fun visitStoreEnvName(opcode: StoreEnvName)

    fun visitJump(opcode: Jump)

    fun visitJumpIfTrue(opcode: JumpIfTrue)

    fun visitJumpIfFalse(opcode: JumpIfFalse)

    fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue)

    fun visitJumpIfToBooleanFalse(opcode: JumpIfToBooleanFalse)

    fun visitJumpIfUndefined(opcode: JumpIfUndefined)

    fun visitJumpIfNotUndefined(opcode: JumpIfNotUndefined)

    fun visitJumpIfNotNullish(opcode: JumpIfNotNullish)

    fun visitJumpIfNullish(opcode: JumpIfNullish)

    fun visitJumpIfNotEmpty(opcode: JumpIfNotEmpty)

    fun visitCreateRegExpObject(opcode: CreateRegExpObject)

    fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral)

    fun visitForInEnumerate()

    fun visitCreateClosure(opcode: CreateClosure)

    fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure)

    fun visitCreateAsyncClosure(opcode: CreateAsyncClosure)

    fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure)

    fun visitGetSuperConstructor()

    fun visitCreateUnmappedArgumentsObject()

    fun visitCreateMappedArgumentsObject()

    fun visitThrowSuperNotInitializedIfEmpty()

    fun visitThrowConstantReassignmentError(opcode: ThrowConstantReassignmentError)

    fun visitThrowLexicalAccessError(opcode: ThrowLexicalAccessError)

    fun visitPushClosure()

    fun visitThrow()

    fun visitReturn()

    fun visitYield()

    fun visitAwait()

    fun visitDefineGetterProperty()

    fun visitDefineSetterProperty()

    fun visitGetSuperBase()

    fun visitPushBigInt(opcode: PushBigInt)

    fun visitPushEmpty()

    fun visitCopyObjectExcludingProperties(opcode: CopyObjectExcludingProperties)

    fun visitLoadBoolean(opcode: LoadBoolean)

    fun visitStoreBoolean(opcode: StoreBoolean)

    fun visitPushJVMFalse()

    fun visitPushJVMTrue()

    fun visitPushJVMInt(opcode: PushJVMInt)

    fun visitCreateClassConstructor(opcode: CreateMethod)

    fun visitCreateClass()

    fun visitAttachClassMethod(opcode: AttachClassMethod)

    fun visitAttachComputedClassMethod(opcode: AttachComputedClassMethod)

    fun visitFinalizeClass()

    fun visitLoadModuleVar(opcode: LoadModuleVar)

    fun visitStoreModuleVar(opcode: StoreModuleVar)

    fun visitCollectRestArgs()
}
