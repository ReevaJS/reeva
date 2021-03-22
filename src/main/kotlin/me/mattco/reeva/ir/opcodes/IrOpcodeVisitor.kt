package me.mattco.reeva.ir.opcodes

abstract class IrOpcodeVisitor {
    open fun visit(opcode: IrOpcode) {
        when (opcode.type) {
            IrOpcodeType.LdaTrue -> visitLdaTrue()
            IrOpcodeType.LdaFalse -> visitLdaFalse()
            IrOpcodeType.LdaUndefined -> visitLdaUndefined()
            IrOpcodeType.LdaNull -> visitLdaNull()
            IrOpcodeType.LdaZero -> visitLdaZero()
            IrOpcodeType.LdaConstant -> visitLdaConstant(opcode)
            IrOpcodeType.LdaInt -> visitLdaInt(opcode)

            IrOpcodeType.Ldar -> visitLdar(opcode)
            IrOpcodeType.Star -> visitStar(opcode)
            IrOpcodeType.Mov -> visitMov(opcode)
            IrOpcodeType.LdaNamedProperty -> visitLdaNamedProperty(opcode)
            IrOpcodeType.LdaKeyedProperty -> visitLdaKeyedProperty(opcode)
            IrOpcodeType.StaNamedProperty -> visitStaNamedProperty(opcode)
            IrOpcodeType.StaKeyedProperty -> visitStaKeyedProperty(opcode)

            IrOpcodeType.CreateArrayLiteral -> visitCreateArrayLiteral()
            IrOpcodeType.StaArrayLiteral -> visitStaArrayLiteral(opcode)
            IrOpcodeType.StaArrayLiteralIndex -> visitStaArrayLiteralIndex(opcode)
            IrOpcodeType.CreateObjectLiteral -> visitCreateObjectLiteral()

            IrOpcodeType.Add -> visitAdd(opcode)
            IrOpcodeType.Sub -> visitSub(opcode)
            IrOpcodeType.Mul -> visitMul(opcode)
            IrOpcodeType.Div -> visitDiv(opcode)
            IrOpcodeType.Mod -> visitMod(opcode)
            IrOpcodeType.Exp -> visitExp(opcode)
            IrOpcodeType.BitwiseOr -> visitBitwiseOr(opcode)
            IrOpcodeType.BitwiseXor -> visitBitwiseXor(opcode)
            IrOpcodeType.BitwiseAnd -> visitBitwiseAnd(opcode)
            IrOpcodeType.ShiftLeft -> visitShiftLeft(opcode)
            IrOpcodeType.ShiftRight -> visitShiftRight(opcode)
            IrOpcodeType.ShiftRightUnsigned -> visitShiftRightUnsigned(opcode)

            IrOpcodeType.Inc -> visitInc()
            IrOpcodeType.Dec -> visitDec()
            IrOpcodeType.Negate -> visitNegate()
            IrOpcodeType.BitwiseNot -> visitBitwiseNot()

            IrOpcodeType.StringAppend -> visitStringAppend(opcode)
            IrOpcodeType.ToBooleanLogicalNot -> visitToBooleanLogicalNot()
            IrOpcodeType.LogicalNot -> visitLogicalNot()
            IrOpcodeType.TypeOf -> visitTypeOf()
            IrOpcodeType.DeletePropertySloppy -> visitDeletePropertySloppy(opcode)
            IrOpcodeType.DeletePropertyStrict -> visitDeletePropertyStrict(opcode)

            IrOpcodeType.LdaGlobal -> visitLdaGlobal(opcode)
            IrOpcodeType.StaGlobal -> visitStaGlobal(opcode)
            IrOpcodeType.LdaCurrentEnv -> visitLdaCurrentEnv(opcode)
            IrOpcodeType.StaCurrentEnv -> visitStaCurrentEnv(opcode)
            IrOpcodeType.LdaEnv -> visitLdaEnv(opcode)
            IrOpcodeType.StaEnv -> visitStaEnv(opcode)
            IrOpcodeType.PushEnv -> visitPushEnv(opcode)
            IrOpcodeType.PopCurrentEnv -> visitPopCurrentEnv()
            IrOpcodeType.PopEnvs -> visitPopEnvs(opcode)

            IrOpcodeType.Call -> visitCall(opcode)
            IrOpcodeType.Call0 -> visitCall0(opcode)
            IrOpcodeType.Call1 -> visitCall1(opcode)
            IrOpcodeType.CallLastSpread -> visitCallLastSpread(opcode)
            IrOpcodeType.CallFromArray -> visitCallFromArray(opcode)
            IrOpcodeType.CallRuntime -> visitCallRuntime(opcode)

            IrOpcodeType.Construct -> visitConstruct(opcode)
            IrOpcodeType.Construct0 -> visitConstruct0(opcode)
            IrOpcodeType.ConstructLastSpread -> visitConstructLastSpread(opcode)
            IrOpcodeType.ConstructFromArray -> visitConstructFromArray(opcode)

            IrOpcodeType.TestEqual -> visitTestEqual(opcode)
            IrOpcodeType.TestNotEqual -> visitTestNotEqual(opcode)
            IrOpcodeType.TestEqualStrict -> visitTestEqualStrict(opcode)
            IrOpcodeType.TestNotEqualStrict -> visitTestNotEqualStrict(opcode)
            IrOpcodeType.TestLessThan -> visitTestLessThan(opcode)
            IrOpcodeType.TestGreaterThan -> visitTestGreaterThan(opcode)
            IrOpcodeType.TestLessThanOrEqual -> visitTestLessThanOrEqual(opcode)
            IrOpcodeType.TestGreaterThanOrEqual -> visitTestGreaterThanOrEqual(opcode)
            IrOpcodeType.TestReferenceEqual -> visitTestReferenceEqual(opcode)
            IrOpcodeType.TestInstanceOf -> visitTestInstanceOf(opcode)
            IrOpcodeType.TestIn -> visitTestIn(opcode)
            IrOpcodeType.TestNullish -> visitTestNullish(opcode)
            IrOpcodeType.TestNull -> visitTestNull(opcode)
            IrOpcodeType.TestUndefined -> visitTestUndefined(opcode)

            IrOpcodeType.ToBoolean -> visitToBoolean()
            IrOpcodeType.ToNumber -> visitToNumber()
            IrOpcodeType.ToNumeric -> visitToNumeric()
            IrOpcodeType.ToObject -> visitToObject()
            IrOpcodeType.ToString -> visitToString()

            IrOpcodeType.Jump -> visitJump(opcode)
            IrOpcodeType.JumpIfTrue -> visitJumpIfTrue(opcode)
            IrOpcodeType.JumpIfFalse -> visitJumpIfFalse(opcode)
            IrOpcodeType.JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode)
            IrOpcodeType.JumpIfToBooleanFalse -> visitJumpIfToBooleanFalse(opcode)
            IrOpcodeType.JumpIfNull -> visitJumpIfNull(opcode)
            IrOpcodeType.JumpIfNotNull -> visitJumpIfNotNull(opcode)
            IrOpcodeType.JumpIfUndefined -> visitJumpIfUndefined(opcode)
            IrOpcodeType.JumpIfNotUndefined -> visitJumpIfNotUndefined(opcode)
            IrOpcodeType.JumpIfNullish -> visitJumpIfNullish(opcode)
            IrOpcodeType.JumpIfNotNullish -> visitJumpIfNotNullish(opcode)
            IrOpcodeType.JumpIfObject -> visitJumpIfObject(opcode)
            IrOpcodeType.JumpPlaceholder -> throw IllegalArgumentException()

            IrOpcodeType.Return -> visitReturn()
            IrOpcodeType.Throw -> visitThrow()
            IrOpcodeType.ThrowConstReassignment -> visitThrowConstReassignment(opcode)
            IrOpcodeType.ThrowUseBeforeInitIfEmpty -> visitThrowUseBeforeInitIfEmpty(opcode)

            IrOpcodeType.DefineGetterProperty -> visitDefineGetterProperty(opcode)
            IrOpcodeType.DefineSetterProperty -> visitDefineSetterProperty(opcode)
            IrOpcodeType.DeclareGlobals -> visitDeclareGlobals(opcode)
            IrOpcodeType.CreateMappedArgumentsObject -> visitCreateMappedArgumentsObject()
            IrOpcodeType.CreateUnmappedArgumentsObject -> visitCreateUnmappedArgumentsObject()
            IrOpcodeType.GetIterator -> visitGetIterator()
            IrOpcodeType.CreateClosure -> visitCreateClosure(opcode)
            IrOpcodeType.DebugBreakpoint -> visitDebugBreakpoint()
        }
    }

    open fun visitLdaTrue() { }

    open fun visitLdaFalse() { }

    open fun visitLdaUndefined() { }

    open fun visitLdaNull() { }

    open fun visitLdaZero() { }

    open fun visitLdaConstant(opcode: IrOpcode) { }

    open fun visitLdaInt(opcode: IrOpcode) { }

    open fun visitLdar(opcode: IrOpcode) { }

    open fun visitStar(opcode: IrOpcode) { }

    open fun visitMov(opcode: IrOpcode) { }

    open fun visitLdaNamedProperty(opcode: IrOpcode) { }

    open fun visitLdaKeyedProperty(opcode: IrOpcode) { }

    open fun visitStaNamedProperty(opcode: IrOpcode) { }

    open fun visitStaKeyedProperty(opcode: IrOpcode) { }

    open fun visitCreateArrayLiteral() { }

    open fun visitStaArrayLiteral(opcode: IrOpcode) { }

    open fun visitStaArrayLiteralIndex(opcode: IrOpcode) { }

    open fun visitCreateObjectLiteral() { }

    open fun visitAdd(opcode: IrOpcode) { }

    open fun visitSub(opcode: IrOpcode) { }

    open fun visitMul(opcode: IrOpcode) { }

    open fun visitDiv(opcode: IrOpcode) { }

    open fun visitMod(opcode: IrOpcode) { }

    open fun visitExp(opcode: IrOpcode) { }

    open fun visitBitwiseOr(opcode: IrOpcode) { }

    open fun visitBitwiseXor(opcode: IrOpcode) { }

    open fun visitBitwiseAnd(opcode: IrOpcode) { }

    open fun visitShiftLeft(opcode: IrOpcode) { }

    open fun visitShiftRight(opcode: IrOpcode) { }

    open fun visitShiftRightUnsigned(opcode: IrOpcode) { }

    open fun visitInc() { }

    open fun visitDec() { }

    open fun visitNegate() { }

    open fun visitBitwiseNot() { }

    open fun visitStringAppend(opcode: IrOpcode) { }

    open fun visitToBooleanLogicalNot() { }

    open fun visitLogicalNot() { }

    open fun visitTypeOf() { }

    open fun visitDeletePropertySloppy(opcode: IrOpcode) { }

    open fun visitDeletePropertyStrict(opcode: IrOpcode) { }

    open fun visitLdaGlobal(opcode: IrOpcode) { }

    open fun visitStaGlobal(opcode: IrOpcode) { }

    open fun visitLdaCurrentEnv(opcode: IrOpcode) { }

    open fun visitStaCurrentEnv(opcode: IrOpcode) { }

    open fun visitLdaEnv(opcode: IrOpcode) { }

    open fun visitStaEnv(opcode: IrOpcode) { }

    open fun visitPushEnv(opcode: IrOpcode) { }

    open fun visitPopCurrentEnv() { }

    open fun visitPopEnvs(opcode: IrOpcode) { }

    open fun visitCall(opcode: IrOpcode) { }

    open fun visitCall0(opcode: IrOpcode) { }

    open fun visitCall1(opcode: IrOpcode) { }

    open fun visitCallLastSpread(opcode: IrOpcode) { }

    open fun visitCallFromArray(opcode: IrOpcode) { }

    open fun visitCallRuntime(opcode: IrOpcode) { }

    open fun visitConstruct(opcode: IrOpcode) { }

    open fun visitConstruct0(opcode: IrOpcode) { }

    open fun visitConstructLastSpread(opcode: IrOpcode) { }

    open fun visitConstructFromArray(opcode: IrOpcode) { }

    open fun visitTestEqual(opcode: IrOpcode) { }

    open fun visitTestNotEqual(opcode: IrOpcode) { }

    open fun visitTestEqualStrict(opcode: IrOpcode) { }

    open fun visitTestNotEqualStrict(opcode: IrOpcode) { }

    open fun visitTestLessThan(opcode: IrOpcode) { }

    open fun visitTestGreaterThan(opcode: IrOpcode) { }

    open fun visitTestLessThanOrEqual(opcode: IrOpcode) { }

    open fun visitTestGreaterThanOrEqual(opcode: IrOpcode) { }

    open fun visitTestReferenceEqual(opcode: IrOpcode) { }

    open fun visitTestInstanceOf(opcode: IrOpcode) { }

    open fun visitTestIn(opcode: IrOpcode) { }

    open fun visitTestNullish(opcode: IrOpcode) { }

    open fun visitTestNull(opcode: IrOpcode) { }

    open fun visitTestUndefined(opcode: IrOpcode) { }

    open fun visitToBoolean() { }

    open fun visitToNumber() { }

    open fun visitToNumeric() { }

    open fun visitToObject() { }

    open fun visitToString() { }

    open fun visitJump(opcode: IrOpcode) { }

    open fun visitJumpIfTrue(opcode: IrOpcode) { }

    open fun visitJumpIfFalse(opcode: IrOpcode) { }

    open fun visitJumpIfToBooleanTrue(opcode: IrOpcode) { }

    open fun visitJumpIfToBooleanFalse(opcode: IrOpcode) { }

    open fun visitJumpIfNull(opcode: IrOpcode) { }

    open fun visitJumpIfNotNull(opcode: IrOpcode) { }

    open fun visitJumpIfUndefined(opcode: IrOpcode) { }

    open fun visitJumpIfNotUndefined(opcode: IrOpcode) { }

    open fun visitJumpIfNullish(opcode: IrOpcode) { }

    open fun visitJumpIfNotNullish(opcode: IrOpcode) { }

    open fun visitJumpIfObject(opcode: IrOpcode) { }

    open fun visitReturn() { }

    open fun visitThrow() { }

    open fun visitThrowConstReassignment(opcode: IrOpcode) { }

    open fun visitThrowUseBeforeInitIfEmpty(opcode: IrOpcode) { }

    open fun visitDefineGetterProperty(opcode: IrOpcode) { }

    open fun visitDefineSetterProperty(opcode: IrOpcode) { }

    open fun visitDeclareGlobals(opcode: IrOpcode) { }

    open fun visitCreateMappedArgumentsObject() { }

    open fun visitCreateUnmappedArgumentsObject() { }

    open fun visitGetIterator() { }

    open fun visitCreateClosure(opcode: IrOpcode) { }

    open fun visitDebugBreakpoint() { }
}
