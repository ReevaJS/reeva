package me.mattco.reeva.interpreter

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.ir.DeclarationsArray
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.ir.opcodes.IrOpcodeVisitor
import me.mattco.reeva.ir.opcodes.RegisterRange
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.JSObject.Companion.initialize
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.*

class IRInterpreter(
    private val function: IRFunction,
    private val arguments: List<JSValue>,
) : IrOpcodeVisitor() {
    private val info = function.info

    private val registers = Registers(info.registerCount)
    private var accumulator by registers::accumulator
    private var ip = 0
    private var isDone = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.constantPool.size) { null }

    private var currentEnv = function.envRecord

    fun interpret(): EvaluationResult {
        try {
            while (!isDone) {
                try {
                    visit(info.opcodes[ip++])
                } catch (e: ThrowException) {
                    val handler = info.handlers.firstOrNull { ip - 1 in it.start..it.end }

                    if (handler == null) {
                        exception = e
                        isDone = true
                    } else {
                        ip = handler.handler
                    }
                }
            }
        } catch (e: Throwable) {
            println("Exception in FunctionInfo ${info.name} (length=${info.opcodes.size}), opcode ${ip - 1}")
            throw e
        }

        return if (exception != null) {
            EvaluationResult.RuntimeError(exception!!.value)
        } else EvaluationResult.Success(accumulator)
    }

    override fun visitLdaTrue() {
        accumulator = JSTrue
    }

    override fun visitLdaFalse() {
        accumulator = JSFalse
    }

    override fun visitLdaUndefined() {
        accumulator = JSUndefined
    }

    override fun visitLdaNull() {
        accumulator = JSNull
    }

    override fun visitLdaZero() {
        accumulator = JSNumber.ZERO
    }

    override fun visitLdaConstant(cpIndex: Int) {
        accumulator = getMappedConstant(cpIndex)
    }

    override fun visitLdaInt(int: Int) {
        accumulator = JSNumber(int)
    }

    override fun visitLdar(reg: Int) {
        accumulator = registers[reg]
    }

    override fun visitStar(reg: Int) {
        registers[reg] = accumulator
    }

    override fun visitMov(fromReg: Int, toReg: Int) {
        registers[fromReg] = registers[toReg]
    }

    override fun visitLdaNamedProperty(objReg: Int, nameCpIndex: Int, slot: Int) {
        val obj = registers[objReg] as JSObject
        val key = loadConstant<String>(nameCpIndex).key()
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(slot)

        accumulator = if (obj.shape !in slot.shapes) {
            val location = obj.getPropertyLocation(key)
            if (location != null) {
                slot.shapes[obj.shape] = location
                obj.getDescriptorAt(location).getActualValue(obj)
            } else JSUndefined
        } else obj.get(key)
    }

    override fun visitLdaKeyedProperty(objReg: Int, slot: Int) {
        val obj = registers[objReg] as JSObject
        val key = accumulator.toPropertyKey()
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(slot)

        accumulator = if (obj.shape !in slot.shapes) {
            val location = obj.getPropertyLocation(key)
            if (location != null) {
                slot.shapes[obj.shape] = location
                obj.getDescriptorAt(location).getActualValue(obj)
            } else JSUndefined
        } else obj.get(key)
    }

    override fun visitStaNamedProperty(objReg: Int, nameCpIndex: Int, slot: Int) {
        val obj = registers[objReg] as JSObject
        val key = loadConstant<String>(nameCpIndex).key()
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(slot)

        if (obj.shape !in slot.shapes) {
            val location = obj.getPropertyLocation(key)
            if (location != null) {
                slot.shapes[obj.shape] = location
                obj.setPropertyAt(location, Descriptor(accumulator, Descriptor.DEFAULT_ATTRIBUTES))
            } else JSUndefined
        } else obj.get(key)

        if (obj.shape !in slot.shapes)
            slot.shapes[obj.shape] = obj.getPropertyLocation(key)!!
        obj.set(key, accumulator)
    }

    override fun visitStaKeyedProperty(objReg: Int, propertyReg: Int, slot: Int) {
        val obj = registers[objReg] as JSObject
        val key = registers[propertyReg].toPropertyKey()
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(slot)
        if (obj.shape !in slot.shapes)
            slot.shapes[obj.shape] = obj.getPropertyLocation(key)!!
        obj.set(key, accumulator)
    }

    override fun visitCreateArrayLiteral() {
        accumulator = JSArrayObject.create(function.realm)
    }

    override fun visitStaArrayLiteral(arrayReg: Int, indexReg: Int) {
        val array = registers[arrayReg] as JSObject
        val index = (registers[indexReg] as JSNumber).asInt
        array.indexedProperties.set(array, index, accumulator)
    }

    override fun visitStaArrayLiteralIndex(arrayReg: Int, index: Int) {
        val array = registers[arrayReg] as JSObject
        array.indexedProperties.set(array, index, accumulator)
    }

    override fun visitCreateObjectLiteral() {
        accumulator = JSObject.create(function.realm)
    }

    override fun visitAdd(lhsReg: Int, slot: Int) {
        binaryOp("+", lhsReg, slot)
    }

    override fun visitSub(lhsReg: Int, slot: Int) {
        binaryOp("-", lhsReg, slot)
    }

    override fun visitMul(lhsReg: Int, slot: Int) {
        binaryOp("*", lhsReg, slot)
    }

    override fun visitDiv(lhsReg: Int, slot: Int) {
        binaryOp("/", lhsReg, slot)
    }

    override fun visitMod(lhsReg: Int, slot: Int) {
        binaryOp("%", lhsReg, slot)
    }

    override fun visitExp(lhsReg: Int, slot: Int) {
        binaryOp("**", lhsReg, slot)
    }

    override fun visitBitwiseOr(lhsReg: Int, slot: Int) {
        binaryOp("|", lhsReg, slot)
    }

    override fun visitBitwiseXor(lhsReg: Int, slot: Int) {
        binaryOp("^", lhsReg, slot)
    }

    override fun visitBitwiseAnd(lhsReg: Int, slot: Int) {
        binaryOp("&", lhsReg, slot)
    }

    override fun visitShiftLeft(lhsReg: Int, slot: Int) {
        binaryOp("<<", lhsReg, slot)
    }

    override fun visitShiftRight(lhsReg: Int, slot: Int) {
        binaryOp(">>", lhsReg, slot)
    }

    override fun visitShiftRightUnsigned(lhsReg: Int, slot: Int) {
        binaryOp(">>>", lhsReg, slot)
    }

    override fun visitInc() {
        accumulator = JSNumber(accumulator.asInt + 1)
    }

    override fun visitDec() {
        accumulator = JSNumber(accumulator.asInt - 1)
    }

    override fun visitNegate() {
        accumulator = if (accumulator is JSBigInt) {
            Operations.bigintUnaryMinus(accumulator)
        } else Operations.numericUnaryMinus(accumulator)
    }

    override fun visitBitwiseNot() {
        accumulator = if (accumulator is JSBigInt) {
            Operations.bigintBitwiseNOT(accumulator)
        } else Operations.numericBitwiseNOT(accumulator)
    }

    override fun visitStringAppend(lhsReg: Int) {
        val template = registers[lhsReg] as JSString
        registers[lhsReg] = JSString(
            template.string + (accumulator as JSString).string
        )
    }

    override fun visitToBooleanLogicalNot() {
        accumulator = (!Operations.toBoolean(accumulator)).toValue()
    }

    override fun visitLogicalNot() {
        accumulator = (!accumulator.asBoolean).toValue()
    }

    override fun visitTypeOf() {
        accumulator = Operations.typeofOperator(accumulator)
    }

    override fun visitDeletePropertySloppy(objReg: Int) {
        val target = registers[objReg]
        accumulator = if (target is JSObject) {
            target.delete(accumulator.toPropertyKey()).toValue()
        } else JSTrue
    }

    override fun visitDeletePropertyStrict(objReg: Int) {
        val target = registers[objReg]
        if (target is JSObject) {
            val key = accumulator.toPropertyKey()
            if (!target.delete(key))
                Errors.StrictModeFailedDelete(key, target.toJSString().string)
        }

        accumulator = JSTrue
    }

    override fun visitLdaGlobal(nameCpIndex: Int) {
        val name = loadConstant<String>(nameCpIndex)
        val desc = function.globalEnv.extension().getPropertyDescriptor(name.key())
            ?: Errors.UnknownReference(name.key()).throwReferenceError()
        accumulator = desc.getActualValue(function.globalEnv.extension())
    }

    override fun visitStaGlobal(nameCpIndex: Int) {
        val name = loadConstant<String>(nameCpIndex)
        function.globalEnv.extension().set(name.key(), accumulator)
    }

    override fun visitLdaCurrentEnv(envSlot: Int) {
        expect(currentEnv !is GlobalEnvRecord)
        accumulator = currentEnv.getBinding(envSlot)
    }

    override fun visitStaCurrentEnv(envSlot: Int) {
        expect(currentEnv !is GlobalEnvRecord)
        currentEnv.setBinding(envSlot, accumulator)
    }

    override fun visitLdaEnv(contextReg: Int, envSlot: Int) {
        expect(currentEnv !is GlobalEnvRecord)
        val targetContext = registers[contextReg] as EnvRecord
        accumulator = targetContext.getBinding(envSlot)
    }

    override fun visitStaEnv(contextReg: Int, envSlot: Int) {
        expect(currentEnv !is GlobalEnvRecord)
        val targetContext = registers[contextReg] as EnvRecord
        targetContext.setBinding(envSlot, accumulator)
    }

    override fun visitCreateBlockScope(numSlots: Int) {
        accumulator = EnvRecord(currentEnv, currentEnv.isStrict, numSlots)
    }

    override fun visitPushEnv(envReg: Int) {
        registers[envReg] = currentEnv
        currentEnv = accumulator as EnvRecord
    }

    override fun visitPopCurrentEnv(envReg: Int) {
        currentEnv = registers[envReg] as EnvRecord
    }

    override fun visitCall(targetReg: Int, args: RegisterRange) {
        call(targetReg, args, CallMode.Normal)
    }

    override fun visitCall0(targetReg: Int, receiverReg: Int) {
        call(targetReg, RegisterRange(receiverReg, 1), CallMode.Normal)
    }

    override fun visitCallLastSpread(targetReg: Int, args: RegisterRange) {
        call(targetReg, args, CallMode.LastSpread)
    }

    override fun visitCallFromArray(targetReg: Int, arrayReg: Int) {
        call(targetReg, RegisterRange(arrayReg, 1), CallMode.Spread)
    }

    override fun visitCallRuntime(functionId: Int, args: RegisterRange) {
        val args = getRegisterBlock(args)
        accumulator = InterpRuntime.values()[functionId].function(args)
    }

    override fun visitConstruct(targetReg: Int, args: RegisterRange) {
        val target = registers[targetReg]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString().string).throwTypeError()

        accumulator = Operations.construct(
            target,
            getRegisterBlock(args),
            accumulator
        )
    }

    override fun visitConstruct0(targetReg: Int) {
        val target = registers[targetReg]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString().string).throwTypeError()

        accumulator = Operations.construct(
            target,
            emptyList(),
            accumulator
        )
    }

    override fun visitConstructLastSpread(targetReg: Int, args: RegisterRange) {
        TODO()
    }

    override fun visitConstructFromArray(targetReg: Int, arrayReg: Int) {
        TODO()
    }

    override fun visitTestEqual(lhsReg: Int) {
        accumulator = Operations.abstractEqualityComparison(registers[lhsReg], accumulator)
    }

    override fun visitTestNotEqual(lhsReg: Int) {
        accumulator = Operations.abstractEqualityComparison(registers[lhsReg], accumulator).inv()
    }

    override fun visitTestEqualStrict(lhsReg: Int) {
        accumulator = Operations.strictEqualityComparison(registers[lhsReg], accumulator)
    }

    override fun visitTestNotEqualStrict(lhsReg: Int) {
        accumulator = Operations.strictEqualityComparison(registers[lhsReg], accumulator).inv()
    }

    override fun visitTestLessThan(lhsReg: Int) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(lhs, rhs, true)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestGreaterThan(lhsReg: Int) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(rhs, lhs, false)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestLessThanOrEqual(lhsReg: Int) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(rhs, lhs, false)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestGreaterThanOrEqual(lhsReg: Int) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(lhs, rhs, true)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestReferenceEqual(lhsReg: Int) {
        accumulator = (accumulator == registers[lhsReg]).toValue()
    }

    override fun visitTestInstanceOf(lhsReg: Int) {
        accumulator = Operations.instanceofOperator(registers[lhsReg], accumulator)
    }

    override fun visitTestIn(lhsReg: Int) {
        val rval = registers[lhsReg].toPropertyKey()
        accumulator = Operations.hasProperty(accumulator, rval).toValue()
    }

    override fun visitTestNullish(lhsReg: Int) {
        accumulator = accumulator.let { it == JSNull || it == JSUndefined }.toValue()
    }

    override fun visitTestNull(lhsReg: Int) {
        accumulator = (accumulator == JSNull).toValue()
    }

    override fun visitTestUndefined(lhsReg: Int) {
        accumulator = (accumulator == JSUndefined).toValue()
    }

    override fun visitToBoolean() {
        accumulator = Operations.toBoolean(accumulator).toValue()
    }

    override fun visitToNumber() {
        accumulator = Operations.toNumber(accumulator)
    }

    override fun visitToNumeric() {
        accumulator = Operations.toNumeric(accumulator)
    }

    override fun visitToObject() {
        accumulator = Operations.toObject(accumulator)
    }

    override fun visitToString() {
        accumulator = Operations.toString(accumulator)
    }

    override fun visitJump(targetInstr: Int) {
        ip = targetInstr
    }

    override fun visitJumpIfTrue(targetInstr: Int) {
        if (accumulator == JSTrue)
            ip = targetInstr
    }

    override fun visitJumpIfFalse(targetInstr: Int) {
        if (accumulator == JSFalse)
            ip = targetInstr
    }

    override fun visitJumpIfToBooleanTrue(targetInstr: Int) {
        if (Operations.toBoolean(accumulator))
            ip = targetInstr
    }

    override fun visitJumpIfToBooleanFalse(targetInstr: Int) {
        if (!Operations.toBoolean(accumulator))
            ip = targetInstr
    }

    override fun visitJumpIfNull(targetInstr: Int) {
        if (accumulator == JSNull)
            ip = targetInstr
    }

    override fun visitJumpIfNotNull(targetInstr: Int) {
        if (accumulator != JSNull)
            ip = targetInstr
    }

    override fun visitJumpIfUndefined(targetInstr: Int) {
        if (accumulator == JSUndefined)
            ip = targetInstr
    }

    override fun visitJumpIfNotUndefined(targetInstr: Int) {
        if (accumulator != JSUndefined)
            ip = targetInstr
    }

    override fun visitJumpIfNullish(targetInstr: Int) {
        if (accumulator is JSObject)
            ip = targetInstr
    }

    override fun visitJumpIfNotNullish(targetInstr: Int) {
        if (accumulator.let { it == JSNull || it == JSUndefined })
            ip = targetInstr
    }

    override fun visitJumpIfObject(targetInstr: Int) {
        if (!accumulator.let { it == JSNull || it == JSUndefined })
            ip = targetInstr
    }

    override fun visitReturn() {
        isDone = true
    }

    override fun visitThrow() {
        val handler = info.handlers.firstOrNull { ip - 1 in it.start..it.end }
            ?: throw ThrowException(accumulator)
        ip = handler.handler
    }

    override fun visitThrowConstReassignment(nameCpIndex: Int) {
        Errors.AssignmentToConstant(loadConstant(nameCpIndex)).throwTypeError()
    }

    override fun visitThrowUseBeforeInitIfEmpty(nameCpIndex: Int) {
        if (accumulator == JSEmpty)
            Errors.AccessBeforeInitialization(loadConstant(nameCpIndex)).throwTypeError()
    }

    override fun visitDefineGetterProperty(targetReg: Int, propertyReg: Int, methodReg: Int) {
        defineAccessor(
            registers[targetReg] as JSObject,
            registers[propertyReg],
            registers[methodReg] as JSFunction,
            isGetter = true,
        )
    }

    override fun visitDefineSetterProperty(targetReg: Int, propertyReg: Int, methodReg: Int) {
        defineAccessor(
            registers[targetReg] as JSObject,
            registers[propertyReg],
            registers[methodReg] as JSFunction,
            isGetter = false,
        )
    }

    override fun visitDeclareGlobals(globalsCpIndex: Int) {
        val array = loadConstant<DeclarationsArray>(globalsCpIndex)
        declareGlobals(array)
    }

    override fun visitCreateMappedArgumentsObject() {
        // TODO
    }

    override fun visitCreateUnmappedArgumentsObject() {
        // TODO
    }

    override fun visitGetIterator() {
        accumulator = Operations.getIterator(Operations.toObject(accumulator)).iterator
        if (accumulator !is JSObject)
            Errors.NonObjectIterator.throwTypeError()
    }

    override fun visitCreateClosure(infoCpIndex: Int) {
        val newInfo = loadConstant<FunctionInfo>(infoCpIndex)
        val newEnv = EnvRecord(currentEnv, currentEnv.isStrict || newInfo.isStrict, newInfo.topLevelSlots)
        accumulator = IRFunction(function.realm, newInfo, newEnv).initialize()
    }

    override fun visitDebugBreakpoint() {
        TODO()
    }

    private fun defineAccessor(obj: JSObject, property: JSValue, method: JSFunction, isGetter: Boolean) {
        val key = property.toPropertyKey()
        Operations.setFunctionName(method, key, if (isGetter) "get" else "set")
        val accessor = if (isGetter) JSAccessor(method, null) else JSAccessor(null, method)

        Operations.definePropertyOrThrow(
            obj,
            key,
            Descriptor(accessor, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE)
        )
    }

    private fun binaryOp(op: String, lhsReg: Int, slot: Int) {
        val lhs = registers[lhsReg]
        val slot = info.feedbackVector.slotAs<OpFeedback>(slot)
        slot.typeTree.recordType(Type.fromValue(lhs))
        slot.typeTree.recordType(Type.fromValue(accumulator))
        accumulator = Operations.applyStringOrNumericBinaryOperator(lhs, accumulator, op)
    }

    private fun call(callableReg: Int, range: RegisterRange, mode: CallMode) {
        val target = registers[callableReg]
        val args = getRegisterBlock(range)

        accumulator = when (mode) {
            CallMode.Normal -> {
                Operations.call(target, args[0], args.drop(1))
            }
            CallMode.OneArg -> {
                Operations.call(target, args[0], listOf(args[1]))
            }
            CallMode.LastSpread -> {
                val nonSpreadArgs = args.drop(1).dropLast(1)
                val spreadValues = Operations.iterableToList(args.last())
                Operations.call(target, args[0], nonSpreadArgs + spreadValues)
            }
            CallMode.Spread -> {
                expect(args.size == 2)
                val argArray = args[1] as JSArrayObject
                val callArgs = (0 until argArray.indexedProperties.arrayLikeSize).map {
                    argArray.indexedProperties.get(argArray, it.toInt())
                }

                Operations.call(target, args[0], callArgs)
            }
        }
    }

    private fun declareGlobals(array: DeclarationsArray) {
        val varNames = array.varIterator().toList()
        val lexNames = array.lexIterator().toList()
        val funcNames = array.funcIterator().toList()

        for (name in lexNames) {
            if (function.globalEnv.hasRestrictedGlobalProperty(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError()
        }

        for (name in funcNames) {
            if (!function.globalEnv.canDeclareGlobalFunction(name))
                Errors.InvalidGlobalFunction(name).throwTypeError()
        }

        for (name in varNames) {
            if (!function.globalEnv.canDeclareGlobalVar(name))
                Errors.InvalidGlobalVar(name).throwTypeError()
        }
    }

    enum class CallMode {
        Normal,
        OneArg,
        LastSpread,
        Spread,
    }

    private fun getRegisterBlock(range: RegisterRange): List<JSValue> {
        val args = mutableListOf<JSValue>()
        for (i in range.start until (range.start + range.count))
            args.add(registers[i])
        return args
    }

    private fun getMappedConstant(index: Int): JSValue {
        return mappedCPool[index] ?: when (val value = info.constantPool[index]) {
            is Int -> JSNumber(value)
            is Double -> JSNumber(value)
            is String -> JSString(value)
            is FunctionInfo -> TODO()
            else -> unreachable()
        }.also { mappedCPool[index] = it }
    }

    private inline fun <reified T> loadConstant(index: Int): T {
        return info.constantPool[index] as T
    }

    inner class Registers(size: Int) {
        var accumulator: JSValue = JSEmpty
        private val registers = Array(size) {
            if (it < info.argCount) JSUndefined else JSEmpty
        }

        init {
            for ((index, argument) in arguments.withIndex()) {
                if (index >= info.argCount)
                    break
                registers[index] = argument
            }
        }

        operator fun get(index: Int) = registers[index]

        operator fun set(index: Int, value: JSValue) {
            registers[index] = value
        }
    }

    class IRFunction(
        realm: Realm,
        val info: FunctionInfo,
        val envRecord: EnvRecord,
    ) : JSFunction(realm, info.isStrict) {
        val globalEnv: GlobalEnvRecord

        init {
            isConstructable = true

            if (envRecord is GlobalEnvRecord) {
                globalEnv = envRecord
            } else {
                var record = envRecord
                while (record !is GlobalEnvRecord)
                    record = record.outer!!
                globalEnv = record
            }
        }

        override fun init() {
            super.init()

            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue) + arguments
            val result = IRInterpreter(this, args).interpret()
            if (result is EvaluationResult.RuntimeError)
                throw ThrowException(result.value)
            return result.value
        }
    }

    companion object {
        fun wrap(info: FunctionInfo, realm: Realm): JSFunction {
            return IRFunction(
                realm,
                info,
                GlobalEnvRecord(realm, info.isStrict, info.topLevelSlots)
            ).initialize()
        }
    }
}
