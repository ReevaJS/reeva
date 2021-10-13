package com.reevajs.reeva.interpreter

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.ThrowException
import com.reevajs.reeva.core.environment.DeclarativeEnvRecord
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.core.lifecycle.ExecutionResult
import com.reevajs.reeva.interpreter.transformer.FunctionInfo
import com.reevajs.reeva.interpreter.transformer.LocalKind
import com.reevajs.reeva.interpreter.transformer.opcodes.*
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.iterators.JSObjectPropertyIterator
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObject.Companion.initialize
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.*

class Interpreter(
    private val realm: Realm,
    private val executable: Executable,
    private val arguments: List<JSValue>,
    initialEnvRecord: EnvRecord,
) : OpcodeVisitor {
    private val info: FunctionInfo
        get() = executable.ir!!

    private val stack = ArrayDeque<Any>()
    private val locals = Array<Any?>(info.localSlots.size) { null }

    private var lexicalEnv = initialEnvRecord
    private var ip = 0
    private var isDone = false

    fun interpret(): ExecutionResult {
        for ((index, arg) in arguments.withIndex()) {
            locals[index] = arg
        }

        try {
            while (!isDone) {
                visit(info.opcodes[ip++])
            }
        } catch (e: ThrowException) {
            return ExecutionResult.RuntimeError(executable, e.value)
        } catch (e: Throwable) {
            println("Exception in FunctionInfo ${info.name}, opcode ${ip - 1}")
            throw e
        }

        expect(stack.size == 1)
        expect(stack[0] is JSValue)
        return ExecutionResult.Success(executable, stack[0] as JSValue)
    }

    override fun visitPushNull() {
        push(JSNull)
    }

    override fun visitPushUndefined() {
        push(JSUndefined)
    }

    override fun visitPushConstant(opcode: PushConstant) {
        when (val l = opcode.literal) {
            is String -> push(JSString(l))
            is Int -> push(JSNumber(l))
            is Double -> push(JSNumber(l))
            is Boolean -> push(JSBoolean.valueOf(l))
            else -> unreachable()
        }
    }

    override fun visitPop() {
        pop()
    }

    override fun visitDup() {
        expect(stack.isNotEmpty())
        push(stack.last())
    }

    override fun visitDupX1() {
        expect(stack.size >= 2)

        // Inserting into a deque isn't great, but this is a very rare opcode
        stack.add(stack.size - 2, stack.last())
    }

    override fun visitSwap() {
        val temp = stack.last()
        stack[stack.lastIndex] = stack[stack.lastIndex - 1]
        stack[stack.lastIndex - 1] = temp
    }

    override fun visitLoadInt(opcode: LoadInt) {
        expect(info.localSlots[opcode.local.value] == LocalKind.Int)
        push(locals[opcode.local.value]!!)
    }

    override fun visitStoreInt(opcode: StoreInt) {
        expect(info.localSlots[opcode.local.value] == LocalKind.Int)
        locals[opcode.local.value] = pop()
    }

    override fun visitIncInt(opcode: IncInt) {
        expect(info.localSlots[opcode.local.value] == LocalKind.Int)
        locals[opcode.local.value] = (locals[opcode.local.value] as Int) + 1
    }

    override fun visitLoadValue(opcode: LoadValue) {
        expect(info.localSlots[opcode.local.value] == LocalKind.Value)
        push(locals[opcode.local.value]!!)
    }

    override fun visitStoreValue(opcode: StoreValue) {
        expect(info.localSlots[opcode.local.value] == LocalKind.Value)
        locals[opcode.local.value] = pop()
    }

    private fun visitBinaryOperator(operator: String) {
        val rhs = popValue()
        val lhs = popValue()
        push(Operations.applyStringOrNumericBinaryOperator(realm, lhs, rhs, operator))
    }

    override fun visitAdd() {
        visitBinaryOperator("+")
    }

    override fun visitSub() {
        visitBinaryOperator("-")
    }

    override fun visitMul() {
        visitBinaryOperator("*")
    }

    override fun visitDiv() {
        visitBinaryOperator("/")
    }

    override fun visitExp() {
        visitBinaryOperator("**")
    }

    override fun visitMod() {
        visitBinaryOperator("%")
    }

    override fun visitBitwiseAnd() {
        visitBinaryOperator("&")
    }

    override fun visitBitwiseOr() {
        visitBinaryOperator("|")
    }

    override fun visitBitwiseXor() {
        visitBinaryOperator("^")
    }

    override fun visitShiftLeft() {
        visitBinaryOperator("<<")
    }

    override fun visitShiftRight() {
        visitBinaryOperator(">>")
    }

    override fun visitShiftRightUnsigned() {
        visitBinaryOperator(">>>")
    }

    override fun visitTestEqualStrict() {
        val rhs = popValue()
        val lhs = popValue()
        push(Operations.strictEqualityComparison(lhs, rhs))
    }

    override fun visitTestNotEqualStrict() {
        val rhs = popValue()
        val lhs = popValue()
        push(Operations.strictEqualityComparison(lhs, rhs).inv())
    }

    override fun visitTestEqual() {
        val rhs = popValue()
        val lhs = popValue()
        push(Operations.abstractEqualityComparison(realm, lhs, rhs))
    }

    override fun visitTestNotEqual() {
        val rhs = popValue()
        val lhs = popValue()
        push(Operations.abstractEqualityComparison(realm, lhs, rhs).inv())
    }

    override fun visitTestLessThan() {
        val rhs = popValue()
        val lhs = popValue()
        val result = Operations.abstractRelationalComparison(realm, lhs, rhs, true)
        push(result.ifUndefined(JSFalse))
    }

    override fun visitTestLessThanOrEqual() {
        val rhs = popValue()
        val lhs = popValue()
        val result = Operations.abstractRelationalComparison(realm, rhs, lhs, false)
        push(if (result == JSFalse) JSTrue else JSFalse)
    }

    override fun visitTestGreaterThan() {
        val rhs = popValue()
        val lhs = popValue()
        val result = Operations.abstractRelationalComparison(realm, rhs, lhs, false)
        push(result.ifUndefined(JSFalse))
    }

    override fun visitTestGreaterThanOrEqual() {
        val rhs = popValue()
        val lhs = popValue()
        val result = Operations.abstractRelationalComparison(realm, lhs, rhs, true)
        push(if (result == JSFalse) JSTrue else JSFalse)
    }

    override fun visitTestInstanceOf() {
        val ctor = popValue()
        push(Operations.instanceofOperator(realm, popValue(), ctor))
    }

    override fun visitTestIn() {
        val rhs = popValue()
        val lhs = popValue().toPropertyKey(realm)
        push(Operations.hasProperty(rhs, lhs))
    }

    override fun visitTypeOf() {
        push(Operations.typeofOperator(popValue()))
    }

    override fun visitToNumber() {
        push(Operations.toNumber(realm, popValue()))
    }

    override fun visitToNumeric() {
        push(Operations.toNumeric(realm, popValue()))
    }

    override fun visitNegate() {
        val value = popValue().let {
            if (it is JSBigInt) {
                Operations.bigintUnaryMinus(it)
            } else Operations.numericUnaryMinus(it)
        }
        push(value)
    }

    override fun visitBitwiseNot() {
        val value = popValue().let {
            if (it is JSBigInt) {
                Operations.bigintBitwiseNOT(it)
            } else Operations.numericBitwiseNOT(realm, it)
        }
        push(value)
    }

    override fun visitToBooleanLogicalNot() {
        push((!Operations.toBoolean(popValue())).toValue())
    }

    override fun visitInc() {
        push(JSNumber(popValue().asInt + 1))
    }

    override fun visitDec() {
        push(JSNumber(popValue().asInt - 1))
    }

    override fun visitLoadKeyedProperty() {
        val key = popValue().toPropertyKey(realm)
        val obj = popValue().toObject(realm)
        push(obj.get(key))
    }

    override fun visitStoreKeyedProperty() {
        val value = popValue()
        val key = popValue().toPropertyKey(realm)
        val obj = popValue().toObject(realm)
        obj.set(key, value)
    }

    override fun visitLoadNamedProperty(opcode: LoadNamedProperty) {
        val obj = popValue().toObject(realm)
        when (val name = opcode.name) {
            is String -> push(obj.get(name))
            is JSSymbol -> push(obj.get(name))
            else -> unreachable()
        }
    }

    override fun visitStoreNamedProperty(opcode: StoreNamedProperty) {
        val value = popValue()
        val obj = popValue().toObject(realm)
        when (val name = opcode.name) {
            is String -> obj.set(name, value)
            is JSSymbol -> obj.set(name, value)
            else -> unreachable()
        }
    }

    override fun visitCreateObject() {
        push(JSObject.create(realm))
    }

    override fun visitCreateArray() {
        push(JSArrayObject.create(realm))
    }

    override fun visitStoreArray(opcode: StoreArray) {
        val value = popValue()
        val index = locals[opcode.index.value] as Int
        val array = popValue() as JSObject
        array.indexedProperties.set(array, index, value)
        locals[opcode.index.value] = locals[opcode.index.value] as Int + 1
    }

    override fun visitDeletePropertyStrict() {
        val property = popValue()
        val target = popValue()
        if (target is JSObject) {
            val key = property.toPropertyKey(realm)
            if (!target.delete(key))
                Errors.StrictModeFailedDelete(key, target.toJSString(realm).string)
        }
        push(JSTrue)
    }

    override fun visitDeletePropertySloppy() {
        val property = popValue()
        val target = popValue()
        if (target is JSObject) {
            push(target.delete(property.toPropertyKey(realm)).toValue())
        } else {
            push(JSTrue)
        }
    }

    override fun visitGetIterator() {
        push(Operations.getIterator(realm, popValue().toObject(realm)))
    }

    override fun visitIteratorNext() {
        push(Operations.iteratorNext(pop() as Operations.IteratorRecord))
    }

    override fun visitIteratorResultDone() {
        push(Operations.iteratorComplete(popValue()))
    }

    override fun visitIteratorResultValue() {
        push(Operations.iteratorValue(popValue()))
    }

    override fun visitCall(opcode: Call) {
        val args = mutableListOf<JSValue>()

        repeat(opcode.argCount) {
            args.add(popValue())
        }

        val receiver = popValue()
        val target = popValue()

        push(Operations.call(
            realm,
            target,
            receiver,
            args.asReversed(),
        ))
    }

    override fun visitCallArray() {
        val argsArray = popValue() as JSObject
        val receiver = popValue()
        val target = popValue()
        push(Operations.call(
            realm,
            target,
            receiver,
            (0 until argsArray.indexedProperties.arrayLikeSize).map(argsArray::get),
        ))
    }

    override fun visitConstruct(opcode: Construct) {
        val args = mutableListOf<JSValue>()

        repeat(opcode.argCount) {
            args.add(popValue())
        }

        val newTarget = popValue()
        val target = popValue()

        push(Operations.construct(
            target,
            args.asReversed(),
            newTarget,
        ))
    }

    override fun visitConstructArray() {
        val argsArray = popValue() as JSObject
        val newTarget = popValue()
        val target = popValue()
        push(Operations.construct(
            target,
            (0 until argsArray.indexedProperties.arrayLikeSize).map(argsArray::get),
            newTarget,
        ))
    }

    override fun visitDeclareGlobals(opcode: DeclareGlobals) {
        // TODO: This is not spec compliant
        for (name in opcode.lexs) {
            if (realm.globalEnv.hasBinding(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError(realm)
        }

        for (name in opcode.funcs) {
            if (realm.globalEnv.hasBinding(name))
                Errors.InvalidGlobalFunction(name).throwSyntaxError(realm)
        }

        for (name in opcode.vars) {
            if (realm.globalEnv.hasBinding(name))
                Errors.InvalidGlobalVar(name).throwSyntaxError(realm)
        }

        for (name in opcode.vars)
            realm.globalEnv.setBinding(name, JSUndefined)
        for (name in opcode.funcs)
            realm.globalEnv.setBinding(name, JSUndefined)
    }

    override fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord) {
        lexicalEnv = DeclarativeEnvRecord(lexicalEnv, opcode.slotCount)
    }

    override fun visitPopEnvRecord() {
        lexicalEnv = lexicalEnv.outer!!
    }

    override fun visitLoadGlobal(opcode: LoadGlobal) {
        if (!realm.globalEnv.hasBinding(opcode.name))
            Errors.NotDefined(opcode.name).throwReferenceError(realm)
        push(realm.globalEnv.getBinding(opcode.name))
    }

    override fun visitStoreGlobal(opcode: StoreGlobal) {
        realm.globalEnv.setBinding(opcode.name, popValue())
    }

    override fun visitLoadCurrentEnvSlot(opcode: LoadCurrentEnvSlot) {
        push(lexicalEnv.getBinding(opcode.slot))
    }

    override fun visitStoreCurrentEnvSlot(opcode: StoreCurrentEnvSlot) {
        lexicalEnv.setBinding(opcode.slot, popValue())
    }

    override fun visitLoadEnvSlot(opcode: LoadEnvSlot) {
        var env = lexicalEnv
        repeat(opcode.distance) { env = env.outer!! }
        push(env.getBinding(opcode.slot))
    }

    override fun visitStoreEnvSlot(opcode: StoreEnvSlot) {
        var env = lexicalEnv
        repeat(opcode.distance) { env = env.outer!! }
        env.setBinding(opcode.slot, popValue())
    }

    override fun visitJump(opcode: Jump) {
        ip = opcode.to
    }

    override fun visitJumpIfTrue(opcode: JumpIfTrue) {
        if (popValue() == JSTrue)
            ip = opcode.to
    }

    override fun visitJumpIfFalse(opcode: JumpIfFalse) {
        if (popValue() == JSFalse)
            ip = opcode.to
    }

    override fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue) {
        if (popValue().toBoolean())
            ip = opcode.to
    }

    override fun visitJumpIfToBooleanFalse(opcode: JumpIfToBooleanFalse) {
        if (!popValue().toBoolean())
            ip = opcode.to
    }

    override fun visitJumpIfUndefined(opcode: JumpIfUndefined) {
        if (popValue() == JSUndefined)
            ip = opcode.to
    }

    override fun visitJumpIfNotUndefined(opcode: JumpIfNotUndefined) {
        if (popValue() != JSUndefined)
            ip = opcode.to
    }

    override fun visitJumpIfNotNullish(opcode: JumpIfNotNullish) {
        if (!popValue().isNullish)
            ip = opcode.to
    }

    override fun visitJumpIfNotEmpty(opcode: JumpIfNotEmpty) {
        if (popValue() != JSEmpty)
            ip = opcode.to
    }

    override fun visitForInEnumerate() {
        val target = popValue().toObject(realm)
        val iterator = JSObjectPropertyIterator.create(realm, target)
        val nextMethod = Operations.getV(realm, iterator, "next".key())
        val iteratorRecord = Operations.IteratorRecord(iterator, nextMethod, false)
        push(iteratorRecord)
    }

    override fun visitCreateClosure(opcode: CreateClosure) {
        val function = NormalIRFunction(realm, executable.forInfo(opcode.ir), lexicalEnv).initialize()
        Operations.setFunctionName(realm, function, opcode.ir.name.key())
        Operations.makeConstructor(realm, function)
        push(function)
    }

    override fun visitCreateClassConstructor(opcode: CreateClassConstructor) {
        TODO("Not yet implemented")
    }

    override fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateAsyncClosure(opcode: CreateAsyncClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateRestParam() {
        TODO("Not yet implemented")
    }

    override fun visitGetSuperConstructor() {
        TODO("Not yet implemented")
    }

    override fun visitCreateUnmappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitCreateMappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitThrowConstantError(opcode: ThrowConstantError) {
        TODO("Not yet implemented")
    }

    override fun visitThrowSuperNotInitializedIfEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitPushClosure() {
        push(Reeva.activeAgent.callStack.last())
    }

    override fun visitReturn() {
        isDone = true
    }

    private fun pop(): Any = stack.removeLast()

    private fun popInt(): Int = pop() as Int

    private fun popValue(): JSValue = pop() as JSValue

    private fun push(value: Any) {
        stack.add(value)
    }

    abstract class IRFunction(
        realm: Realm,
        val executable: Executable,
        val outerEnvRecord: EnvRecord,
        prototype: JSValue = realm.functionProto,
    ) : JSFunction(realm, executable.ir!!.isStrict, prototype)

    class NormalIRFunction(
        realm: Realm,
        executable: Executable,
        outerEnvRecord: EnvRecord,
    ) : IRFunction(realm, executable, outerEnvRecord) {
        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
            return when (val result = Interpreter(realm, executable, args, outerEnvRecord).interpret()) {
                is ExecutionResult.InternalError -> throw result.cause
                is ExecutionResult.ParseError -> unreachable()
                is ExecutionResult.RuntimeError -> throw ThrowException(result.value)
                is ExecutionResult.Success -> result.value
            }
        }
    }

    companion object {
        fun wrap(
            realm: Realm,
            executable: Executable,
            outerEnvRecord: EnvRecord,
            kind: Operations.FunctionKind = Operations.FunctionKind.Normal,
        ) = when (kind) {
            Operations.FunctionKind.Normal -> NormalIRFunction(realm, executable, outerEnvRecord)
            else -> TODO()
        }.initialize()
    }
}