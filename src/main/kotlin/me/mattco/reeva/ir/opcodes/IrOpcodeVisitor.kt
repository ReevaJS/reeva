package me.mattco.reeva.ir.opcodes

abstract class IrOpcodeVisitor {
    open fun visit(opcode: IrOpcode) {
        when (opcode.type) {
            IrOpcodeType.LdaTrue -> visitLdaTrue()
            IrOpcodeType.LdaFalse -> visitLdaFalse()
            IrOpcodeType.LdaUndefined -> visitLdaUndefined()
            IrOpcodeType.LdaNull -> visitLdaNull()
            IrOpcodeType.LdaZero -> visitLdaZero()
            IrOpcodeType.LdaConstant -> visitLdaConstant(opcode.intAt(0))
            IrOpcodeType.LdaInt -> visitLdaInt(opcode.intAt(0))

            IrOpcodeType.Ldar -> visitLdar(opcode.intAt(0))
            IrOpcodeType.Star -> visitStar(opcode.intAt(0))
            IrOpcodeType.Mov -> visitMov(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.LdaNamedProperty -> visitLdaNamedProperty(opcode.intAt(0), opcode.intAt(1), opcode.intAt(2))
            IrOpcodeType.LdaKeyedProperty -> visitLdaKeyedProperty(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.StaNamedProperty -> visitStaNamedProperty(opcode.intAt(0), opcode.intAt(1), opcode.intAt(2))
            IrOpcodeType.StaKeyedProperty -> visitStaKeyedProperty(opcode.intAt(0), opcode.intAt(1), opcode.intAt(2))

            IrOpcodeType.CreateArrayLiteral -> visitCreateArrayLiteral()
            IrOpcodeType.StaArrayLiteral -> visitStaArrayLiteral(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.StaArrayLiteralIndex -> visitStaArrayLiteralIndex(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.CreateObjectLiteral -> visitCreateObjectLiteral()

            IrOpcodeType.Add -> visitAdd(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.Sub -> visitSub(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.Mul -> visitMul(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.Div -> visitDiv(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.Mod -> visitMod(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.Exp -> visitExp(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.BitwiseOr -> visitBitwiseOr(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.BitwiseXor -> visitBitwiseXor(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.BitwiseAnd -> visitBitwiseAnd(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.ShiftLeft -> visitShiftLeft(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.ShiftRight -> visitShiftRight(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.ShiftRightUnsigned -> visitShiftRightUnsigned(opcode.intAt(0), opcode.intAt(1))

            IrOpcodeType.Inc -> visitInc()
            IrOpcodeType.Dec -> visitDec()
            IrOpcodeType.Negate -> visitNegate()
            IrOpcodeType.BitwiseNot -> visitBitwiseNot()

            IrOpcodeType.StringAppend -> visitStringAppend(opcode.intAt(0))
            IrOpcodeType.ToBooleanLogicalNot -> visitToBooleanLogicalNot()
            IrOpcodeType.LogicalNot -> visitLogicalNot()
            IrOpcodeType.TypeOf -> visitTypeOf()
            IrOpcodeType.DeletePropertySloppy -> visitDeletePropertySloppy(opcode.intAt(0))
            IrOpcodeType.DeletePropertyStrict -> visitDeletePropertyStrict(opcode.intAt(0))

            IrOpcodeType.LdaGlobal -> visitLdaGlobal(opcode.intAt(0))
            IrOpcodeType.StaGlobal -> visitStaGlobal(opcode.intAt(0))
            IrOpcodeType.LdaCurrentEnv -> visitLdaCurrentEnv(opcode.intAt(0))
            IrOpcodeType.StaCurrentEnv -> visitStaCurrentEnv(opcode.intAt(0))
            IrOpcodeType.LdaEnv -> visitLdaEnv(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.StaEnv -> visitStaEnv(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.CreateBlockScope -> visitCreateBlockScope(opcode.intAt(0))
            IrOpcodeType.PushEnv -> visitPushEnv(opcode.intAt(0))
            IrOpcodeType.PopCurrentEnv -> visitPopCurrentEnv(opcode.intAt(0))

            IrOpcodeType.Call -> visitCall(opcode.intAt(0), opcode.rangeAt(1))
            IrOpcodeType.Call0 -> visitCall0(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.CallLastSpread -> visitCallLastSpread(opcode.intAt(0), opcode.rangeAt(1))
            IrOpcodeType.CallFromArray -> visitCallFromArray(opcode.intAt(0), opcode.intAt(1))
            IrOpcodeType.CallRuntime -> visitCallRuntime(opcode.intAt(0), opcode.rangeAt(1))

            IrOpcodeType.Construct -> visitConstruct(opcode.intAt(0), opcode.rangeAt(1))
            IrOpcodeType.Construct0 -> visitConstruct0(opcode.intAt(0))
            IrOpcodeType.ConstructLastSpread -> visitConstructLastSpread(opcode.intAt(0), opcode.rangeAt(1))
            IrOpcodeType.ConstructFromArray -> visitConstructFromArray(opcode.intAt(0), opcode.intAt(1))

            IrOpcodeType.TestEqual -> visitTestEqual(opcode.intAt(0))
            IrOpcodeType.TestNotEqual -> visitTestNotEqual(opcode.intAt(0))
            IrOpcodeType.TestEqualStrict -> visitTestEqualStrict(opcode.intAt(0))
            IrOpcodeType.TestNotEqualStrict -> visitTestNotEqualStrict(opcode.intAt(0))
            IrOpcodeType.TestLessThan -> visitTestLessThan(opcode.intAt(0))
            IrOpcodeType.TestGreaterThan -> visitTestGreaterThan(opcode.intAt(0))
            IrOpcodeType.TestLessThanOrEqual -> visitTestLessThanOrEqual(opcode.intAt(0))
            IrOpcodeType.TestGreaterThanOrEqual -> visitTestGreaterThanOrEqual(opcode.intAt(0))
            IrOpcodeType.TestReferenceEqual -> visitTestReferenceEqual(opcode.intAt(0))
            IrOpcodeType.TestInstanceOf -> visitTestInstanceOf(opcode.intAt(0))
            IrOpcodeType.TestIn -> visitTestIn(opcode.intAt(0))
            IrOpcodeType.TestNullish -> visitTestNullish(opcode.intAt(0))
            IrOpcodeType.TestNull -> visitTestNull(opcode.intAt(0))
            IrOpcodeType.TestUndefined -> visitTestUndefined(opcode.intAt(0))

            IrOpcodeType.ToBoolean -> visitToBoolean()
            IrOpcodeType.ToNumber -> visitToNumber()
            IrOpcodeType.ToNumeric -> visitToNumeric()
            IrOpcodeType.ToObject -> visitToObject()
            IrOpcodeType.ToString -> visitToString()

            IrOpcodeType.Jump -> visitJump(opcode.intAt(0))
            IrOpcodeType.JumpIfTrue -> visitJumpIfTrue(opcode.intAt(0))
            IrOpcodeType.JumpIfFalse -> visitJumpIfFalse(opcode.intAt(0))
            IrOpcodeType.JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode.intAt(0))
            IrOpcodeType.JumpIfToBooleanFalse -> visitJumpIfToBooleanFalse(opcode.intAt(0))
            IrOpcodeType.JumpIfNull -> visitJumpIfNull(opcode.intAt(0))
            IrOpcodeType.JumpIfNotNull -> visitJumpIfNotNull(opcode.intAt(0))
            IrOpcodeType.JumpIfUndefined -> visitJumpIfUndefined(opcode.intAt(0))
            IrOpcodeType.JumpIfNotUndefined -> visitJumpIfNotUndefined(opcode.intAt(0))
            IrOpcodeType.JumpIfNullish -> visitJumpIfNullish(opcode.intAt(0))
            IrOpcodeType.JumpIfNotNullish -> visitJumpIfNotNullish(opcode.intAt(0))
            IrOpcodeType.JumpIfObject -> visitJumpIfObject(opcode.intAt(0))
            IrOpcodeType.JumpPlaceholder -> throw IllegalArgumentException()

            IrOpcodeType.Return -> visitReturn()
            IrOpcodeType.Throw -> visitThrow()
            IrOpcodeType.ThrowConstReassignment -> visitThrowConstReassignment(opcode.intAt(0))
            IrOpcodeType.ThrowUseBeforeInitIfEmpty -> visitThrowUseBeforeInitIfEmpty(opcode.intAt(0))

            IrOpcodeType.DefineGetterProperty -> visitDefineGetterProperty(opcode.intAt(0), opcode.intAt(1), opcode.intAt(2))
            IrOpcodeType.DefineSetterProperty -> visitDefineSetterProperty(opcode.intAt(0), opcode.intAt(1), opcode.intAt(2))
            IrOpcodeType.DeclareGlobals -> visitDeclareGlobals(opcode.intAt(0))
            IrOpcodeType.CreateMappedArgumentsObject -> visitCreateMappedArgumentsObject()
            IrOpcodeType.CreateUnmappedArgumentsObject -> visitCreateUnmappedArgumentsObject()
            IrOpcodeType.GetIterator -> visitGetIterator()
            IrOpcodeType.CreateClosure -> visitCreateClosure(opcode.intAt(0))
            IrOpcodeType.DebugBreakpoint -> visitDebugBreakpoint()
        }
    }

    open fun visitLdaTrue() { }

    open fun visitLdaFalse() { }

    open fun visitLdaUndefined() { }

    open fun visitLdaNull() { }

    open fun visitLdaZero() { }

    open fun visitLdaConstant(cpIndex: Int) { }

    open fun visitLdaInt(int: Int) { }

    open fun visitLdar(reg: Int) { }

    open fun visitStar(reg: Int) { }

    open fun visitMov(fromReg: Int, toReg: Int) { }

    open fun visitLdaNamedProperty(objReg: Int, nameCpIndex: Int, slot: Int) { }

    open fun visitLdaKeyedProperty(objReg: Int, slot: Int) { }

    open fun visitStaNamedProperty(objReg: Int, nameCpIndex: Int, slot: Int) { }

    open fun visitStaKeyedProperty(objReg: Int, propertyReg: Int, slot: Int) { }

    open fun visitCreateArrayLiteral() { }

    open fun visitStaArrayLiteral(arrayReg: Int, indexReg: Int) { }

    open fun visitStaArrayLiteralIndex(arrayReg: Int, index: Int) { }

    open fun visitCreateObjectLiteral() { }

    open fun visitAdd(lhsReg: Int, slot: Int) { }

    open fun visitSub(lhsReg: Int, slot: Int) { }

    open fun visitMul(lhsReg: Int, slot: Int) { }

    open fun visitDiv(lhsReg: Int, slot: Int) { }

    open fun visitMod(lhsReg: Int, slot: Int) { }

    open fun visitExp(lhsReg: Int, slot: Int) { }

    open fun visitBitwiseOr(lhsReg: Int, slot: Int) { }

    open fun visitBitwiseXor(lhsReg: Int, slot: Int) { }

    open fun visitBitwiseAnd(lhsReg: Int, slot: Int) { }

    open fun visitShiftLeft(lhsReg: Int, slot: Int) { }

    open fun visitShiftRight(lhsReg: Int, slot: Int) { }

    open fun visitShiftRightUnsigned(lhsReg: Int, slot: Int) { }

    open fun visitInc() { }

    open fun visitDec() { }

    open fun visitNegate() { }

    open fun visitBitwiseNot() { }

    open fun visitStringAppend(lhsReg: Int) { }

    open fun visitToBooleanLogicalNot() { }

    open fun visitLogicalNot() { }

    open fun visitTypeOf() { }

    open fun visitDeletePropertySloppy(objReg: Int) { }

    open fun visitDeletePropertyStrict(objReg: Int) { }

    open fun visitLdaGlobal(nameCpIndex: Int) { }

    open fun visitStaGlobal(nameCpIndex: Int) { }

    open fun visitLdaCurrentEnv(envSlot: Int) { }

    open fun visitStaCurrentEnv(envSlot: Int) { }

    open fun visitLdaEnv(contextReg: Int, envSlot: Int) { }

    open fun visitStaEnv(contextReg: Int, envSlot: Int) { }

    open fun visitCreateBlockScope(numSlots: Int) { }

    open fun visitPushEnv(envReg: Int) { }

    open fun visitPopCurrentEnv(envReg: Int) { }

    open fun visitCall(targetReg: Int, args: RegisterRange) { }

    open fun visitCall0(targetReg: Int, receiverReg: Int) { }

    open fun visitCallLastSpread(targetReg: Int, args: RegisterRange) { }

    open fun visitCallFromArray(targetReg: Int, arrayReg: Int) { }

    open fun visitCallRuntime(functionId: Int, args: RegisterRange) { }

    open fun visitConstruct(targetReg: Int, args: RegisterRange) { }

    open fun visitConstruct0(targetReg: Int) { }

    open fun visitConstructLastSpread(targetReg: Int, args: RegisterRange) { }

    open fun visitConstructFromArray(targetReg: Int, arrayReg: Int) { }

    open fun visitTestEqual(lhsReg: Int) { }

    open fun visitTestNotEqual(lhsReg: Int) { }

    open fun visitTestEqualStrict(lhsReg: Int) { }

    open fun visitTestNotEqualStrict(lhsReg: Int) { }

    open fun visitTestLessThan(lhsReg: Int) { }

    open fun visitTestGreaterThan(lhsReg: Int) { }

    open fun visitTestLessThanOrEqual(lhsReg: Int) { }

    open fun visitTestGreaterThanOrEqual(lhsReg: Int) { }

    open fun visitTestReferenceEqual(lhsReg: Int) { }

    open fun visitTestInstanceOf(lhsReg: Int) { }

    open fun visitTestIn(lhsReg: Int) { }

    open fun visitTestNullish(lhsReg: Int) { }

    open fun visitTestNull(lhsReg: Int) { }

    open fun visitTestUndefined(lhsReg: Int) { }

    open fun visitToBoolean() { }

    open fun visitToNumber() { }

    open fun visitToNumeric() { }

    open fun visitToObject() { }

    open fun visitToString() { }

    open fun visitJump(targetInstr: Int) { }

    open fun visitJumpIfTrue(targetInstr: Int) { }

    open fun visitJumpIfFalse(targetInstr: Int) { }

    open fun visitJumpIfToBooleanTrue(targetInstr: Int) { }

    open fun visitJumpIfToBooleanFalse(targetInstr: Int) { }

    open fun visitJumpIfNull(targetInstr: Int) { }

    open fun visitJumpIfNotNull(targetInstr: Int) { }

    open fun visitJumpIfUndefined(targetInstr: Int) { }

    open fun visitJumpIfNotUndefined(targetInstr: Int) { }

    open fun visitJumpIfNullish(targetInstr: Int) { }

    open fun visitJumpIfNotNullish(targetInstr: Int) { }

    open fun visitJumpIfObject(targetInstr: Int) { }

    open fun visitReturn() { }

    open fun visitThrow() { }

    open fun visitThrowConstReassignment(nameCpIndex: Int) { }

    open fun visitThrowUseBeforeInitIfEmpty(nameCpIndex: Int) { }

    open fun visitDefineGetterProperty(targetReg: Int, propertyReg: Int, methodReg: Int) { }

    open fun visitDefineSetterProperty(targetReg: Int, propertyReg: Int, methodReg: Int) { }

    open fun visitDeclareGlobals(globalsCpIndex: Int) { }

    open fun visitCreateMappedArgumentsObject() { }

    open fun visitCreateUnmappedArgumentsObject() { }

    open fun visitGetIterator() { }

    open fun visitCreateClosure(infoCpIndex: Int) { }

    open fun visitDebugBreakpoint() { }
}
