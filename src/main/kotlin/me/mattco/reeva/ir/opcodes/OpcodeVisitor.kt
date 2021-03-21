package me.mattco.reeva.ir.opcodes

import me.mattco.reeva.ir.Opcode
import me.mattco.reeva.ir.OpcodeType

abstract class OpcodeVisitor {
    open fun visit(opcode: Opcode) {
        when (opcode.type) {
            OpcodeType.LdaTrue -> visitLdaTrue()
            OpcodeType.LdaFalse -> visitLdaFalse()
            OpcodeType.LdaUndefined -> visitLdaUndefined()
            OpcodeType.LdaNull -> visitLdaNull()
            OpcodeType.LdaZero -> visitLdaZero()
            OpcodeType.LdaConstant -> visitLdaConstant(opcode)
            OpcodeType.LdaInt -> visitLdaInt(opcode)

            OpcodeType.Ldar -> visitLdar(opcode)
            OpcodeType.Star -> visitStar(opcode)
            OpcodeType.Mov -> visitMov(opcode)
            OpcodeType.LdaNamedProperty -> visitLdaNamedProperty(opcode)
            OpcodeType.LdaKeyedProperty -> visitLdaKeyedProperty(opcode)
            OpcodeType.StaNamedProperty -> visitStaNamedProperty(opcode)
            OpcodeType.StaKeyedProperty -> visitStaKeyedProperty(opcode)

            OpcodeType.CreateArrayLiteral -> visitCreateArrayLiteral()
            OpcodeType.StaArrayLiteral -> visitStaArrayLiteral(opcode)
            OpcodeType.StaArrayLiteralIndex -> visitStaArrayLiteralIndex(opcode)
            OpcodeType.CreateObjectLiteral -> visitCreateObjectLiteral()

            OpcodeType.Add -> visitAdd(opcode)
            OpcodeType.Sub -> visitSub(opcode)
            OpcodeType.Mul -> visitMul(opcode)
            OpcodeType.Div -> visitDiv(opcode)
            OpcodeType.Mod -> visitMod(opcode)
            OpcodeType.Exp -> visitExp(opcode)
            OpcodeType.BitwiseOr -> visitBitwiseOr(opcode)
            OpcodeType.BitwiseXor -> visitBitwiseXor(opcode)
            OpcodeType.BitwiseAnd -> visitBitwiseAnd(opcode)
            OpcodeType.ShiftLeft -> visitShiftLeft(opcode)
            OpcodeType.ShiftRight -> visitShiftRight(opcode)
            OpcodeType.ShiftRightUnsigned -> visitShiftRightUnsigned(opcode)

            OpcodeType.Inc -> visitInc()
            OpcodeType.Dec -> visitDec()
            OpcodeType.Negate -> visitNegate()
            OpcodeType.BitwiseNot -> visitBitwiseNot()

            OpcodeType.StringAppend -> visitStringAppend(opcode)
            OpcodeType.ToBooleanLogicalNot -> visitToBooleanLogicalNot()
            OpcodeType.LogicalNot -> visitLogicalNot()
            OpcodeType.TypeOf -> visitTypeOf()
            OpcodeType.DeletePropertySloppy -> visitDeletePropertySloppy(opcode)
            OpcodeType.DeletePropertyStrict -> visitDeletePropertyStrict(opcode)

            OpcodeType.LdaGlobal -> visitLdaGlobal(opcode)
            OpcodeType.StaGlobal -> visitStaGlobal(opcode)
            OpcodeType.LdaCurrentEnv -> visitLdaCurrentEnv(opcode)
            OpcodeType.StaCurrentEnv -> visitStaCurrentEnv(opcode)
            OpcodeType.LdaEnv -> visitLdaEnv(opcode)
            OpcodeType.StaEnv -> visitStaEnv(opcode)
            OpcodeType.PushEnv -> visitPushEnv(opcode)
            OpcodeType.PopCurrentEnv -> visitPopCurrentEnv()
            OpcodeType.PopEnvs -> visitPopEnvs(opcode)

            OpcodeType.Call -> visitCall(opcode)
            OpcodeType.Call0 -> visitCall0(opcode)
            OpcodeType.Call1 -> visitCall1(opcode)
            OpcodeType.CallLastSpread -> visitCallLastSpread(opcode)
            OpcodeType.CallFromArray -> visitCallFromArray(opcode)
            OpcodeType.CallRuntime -> visitCallRuntime(opcode)

            OpcodeType.Construct -> visitConstruct(opcode)
            OpcodeType.Construct0 -> visitConstruct0(opcode)
            OpcodeType.ConstructLastSpread -> visitConstructLastSpread(opcode)
            OpcodeType.ConstructFromArray -> visitConstructFromArray(opcode)

            OpcodeType.TestEqual -> visitTestEqual(opcode)
            OpcodeType.TestNotEqual -> visitTestNotEqual(opcode)
            OpcodeType.TestEqualStrict -> visitTestEqualStrict(opcode)
            OpcodeType.TestNotEqualStrict -> visitTestNotEqualStrict(opcode)
            OpcodeType.TestLessThan -> visitTestLessThan(opcode)
            OpcodeType.TestGreaterThan -> visitTestGreaterThan(opcode)
            OpcodeType.TestLessThanOrEqual -> visitTestLessThanOrEqual(opcode)
            OpcodeType.TestGreaterThanOrEqual -> visitTestGreaterThanOrEqual(opcode)
            OpcodeType.TestReferenceEqual -> visitTestReferenceEqual(opcode)
            OpcodeType.TestInstanceOf -> visitTestInstanceOf(opcode)
            OpcodeType.TestIn -> visitTestIn(opcode)
            OpcodeType.TestNullish -> visitTestNullish(opcode)
            OpcodeType.TestNull -> visitTestNull(opcode)
            OpcodeType.TestUndefined -> visitTestUndefined(opcode)

            OpcodeType.ToBoolean -> visitToBoolean()
            OpcodeType.ToNumber -> visitToNumber()
            OpcodeType.ToNumeric -> visitToNumeric()
            OpcodeType.ToObject -> visitToObject()
            OpcodeType.ToString -> visitToString()

            OpcodeType.Jump -> visitJump(opcode)
            OpcodeType.JumpIfTrue -> visitJumpIfTrue(opcode)
            OpcodeType.JumpIfFalse -> visitJumpIfFalse(opcode)
            OpcodeType.JumpIfToBooleanTrue -> visitJumpIfToBooleanTrue(opcode)
            OpcodeType.JumpIfToBooleanFalse -> visitJumpIfToBooleanFalse(opcode)
            OpcodeType.JumpIfNull -> visitJumpIfNull(opcode)
            OpcodeType.JumpIfNotNull -> visitJumpIfNotNull(opcode)
            OpcodeType.JumpIfUndefined -> visitJumpIfUndefined(opcode)
            OpcodeType.JumpIfNotUndefined -> visitJumpIfNotUndefined(opcode)
            OpcodeType.JumpIfNullish -> visitJumpIfNullish(opcode)
            OpcodeType.JumpIfNotNullish -> visitJumpIfNotNullish(opcode)
            OpcodeType.JumpIfObject -> visitJumpIfObject(opcode)
            OpcodeType.JumpPlaceholder -> throw IllegalArgumentException()

            OpcodeType.Return -> visitReturn()
            OpcodeType.Throw -> visitThrow()
            OpcodeType.ThrowConstReassignment -> visitThrowConstReassignment(opcode)
            OpcodeType.ThrowUseBeforeInitIfEmpty -> visitThrowUseBeforeInitIfEmpty(opcode)

            OpcodeType.DefineGetterProperty -> visitDefineGetterProperty(opcode)
            OpcodeType.DefineSetterProperty -> visitDefineSetterProperty(opcode)
            OpcodeType.DeclareGlobals -> visitDeclareGlobals(opcode)
            OpcodeType.CreateMappedArgumentsObject -> visitCreateMappedArgumentsObject()
            OpcodeType.CreateUnmappedArgumentsObject -> visitCreateUnmappedArgumentsObject()
            OpcodeType.GetIterator -> visitGetIterator()
            OpcodeType.CreateClosure -> visitCreateClosure(opcode)
            OpcodeType.DebugBreakpoint -> visitDebugBreakpoint()
        }
    }

    open fun visitLdaTrue() { }

    open fun visitLdaFalse() { }

    open fun visitLdaUndefined() { }

    open fun visitLdaNull() { }

    open fun visitLdaZero() { }

    open fun visitLdaConstant(opcode: Opcode) { }

    open fun visitLdaInt(opcode: Opcode) { }

    open fun visitLdar(opcode: Opcode) { }

    open fun visitStar(opcode: Opcode) { }

    open fun visitMov(opcode: Opcode) { }

    open fun visitLdaNamedProperty(opcode: Opcode) { }

    open fun visitLdaKeyedProperty(opcode: Opcode) { }

    open fun visitStaNamedProperty(opcode: Opcode) { }

    open fun visitStaKeyedProperty(opcode: Opcode) { }

    open fun visitCreateArrayLiteral() { }

    open fun visitStaArrayLiteral(opcode: Opcode) { }

    open fun visitStaArrayLiteralIndex(opcode: Opcode) { }

    open fun visitCreateObjectLiteral() { }

    open fun visitAdd(opcode: Opcode) { }

    open fun visitSub(opcode: Opcode) { }

    open fun visitMul(opcode: Opcode) { }

    open fun visitDiv(opcode: Opcode) { }

    open fun visitMod(opcode: Opcode) { }

    open fun visitExp(opcode: Opcode) { }

    open fun visitBitwiseOr(opcode: Opcode) { }

    open fun visitBitwiseXor(opcode: Opcode) { }

    open fun visitBitwiseAnd(opcode: Opcode) { }

    open fun visitShiftLeft(opcode: Opcode) { }

    open fun visitShiftRight(opcode: Opcode) { }

    open fun visitShiftRightUnsigned(opcode: Opcode) { }

    open fun visitInc() { }

    open fun visitDec() { }

    open fun visitNegate() { }

    open fun visitBitwiseNot() { }

    open fun visitStringAppend(opcode: Opcode) { }

    open fun visitToBooleanLogicalNot() { }

    open fun visitLogicalNot() { }

    open fun visitTypeOf() { }

    open fun visitDeletePropertySloppy(opcode: Opcode) { }

    open fun visitDeletePropertyStrict(opcode: Opcode) { }

    open fun visitLdaGlobal(opcode: Opcode) { }

    open fun visitStaGlobal(opcode: Opcode) { }

    open fun visitLdaCurrentEnv(opcode: Opcode) { }

    open fun visitStaCurrentEnv(opcode: Opcode) { }

    open fun visitLdaEnv(opcode: Opcode) { }

    open fun visitStaEnv(opcode: Opcode) { }

    open fun visitPushEnv(opcode: Opcode) { }

    open fun visitPopCurrentEnv() { }

    open fun visitPopEnvs(opcode: Opcode) { }

    open fun visitCall(opcode: Opcode) { }

    open fun visitCall0(opcode: Opcode) { }

    open fun visitCall1(opcode: Opcode) { }

    open fun visitCallLastSpread(opcode: Opcode) { }

    open fun visitCallFromArray(opcode: Opcode) { }

    open fun visitCallRuntime(opcode: Opcode) { }

    open fun visitConstruct(opcode: Opcode) { }

    open fun visitConstruct0(opcode: Opcode) { }

    open fun visitConstructLastSpread(opcode: Opcode) { }

    open fun visitConstructFromArray(opcode: Opcode) { }

    open fun visitTestEqual(opcode: Opcode) { }

    open fun visitTestNotEqual(opcode: Opcode) { }

    open fun visitTestEqualStrict(opcode: Opcode) { }

    open fun visitTestNotEqualStrict(opcode: Opcode) { }

    open fun visitTestLessThan(opcode: Opcode) { }

    open fun visitTestGreaterThan(opcode: Opcode) { }

    open fun visitTestLessThanOrEqual(opcode: Opcode) { }

    open fun visitTestGreaterThanOrEqual(opcode: Opcode) { }

    open fun visitTestReferenceEqual(opcode: Opcode) { }

    open fun visitTestInstanceOf(opcode: Opcode) { }

    open fun visitTestIn(opcode: Opcode) { }

    open fun visitTestNullish(opcode: Opcode) { }

    open fun visitTestNull(opcode: Opcode) { }

    open fun visitTestUndefined(opcode: Opcode) { }

    open fun visitToBoolean() { }

    open fun visitToNumber() { }

    open fun visitToNumeric() { }

    open fun visitToObject() { }

    open fun visitToString() { }

    open fun visitJump(opcode: Opcode) { }

    open fun visitJumpIfTrue(opcode: Opcode) { }

    open fun visitJumpIfFalse(opcode: Opcode) { }

    open fun visitJumpIfToBooleanTrue(opcode: Opcode) { }

    open fun visitJumpIfToBooleanFalse(opcode: Opcode) { }

    open fun visitJumpIfNull(opcode: Opcode) { }

    open fun visitJumpIfNotNull(opcode: Opcode) { }

    open fun visitJumpIfUndefined(opcode: Opcode) { }

    open fun visitJumpIfNotUndefined(opcode: Opcode) { }

    open fun visitJumpIfNullish(opcode: Opcode) { }

    open fun visitJumpIfNotNullish(opcode: Opcode) { }

    open fun visitJumpIfObject(opcode: Opcode) { }

    open fun visitReturn() { }

    open fun visitThrow() { }

    open fun visitThrowConstReassignment(opcode: Opcode) { }

    open fun visitThrowUseBeforeInitIfEmpty(opcode: Opcode) { }

    open fun visitDefineGetterProperty(opcode: Opcode) { }

    open fun visitDefineSetterProperty(opcode: Opcode) { }

    open fun visitDeclareGlobals(opcode: Opcode) { }

    open fun visitCreateMappedArgumentsObject() { }

    open fun visitCreateUnmappedArgumentsObject() { }

    open fun visitGetIterator() { }

    open fun visitCreateClosure(opcode: Opcode) { }

    open fun visitDebugBreakpoint() { }
}
