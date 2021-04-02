package me.mattco.reeva.interpreter

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.ir.DeclarationsArray
import me.mattco.reeva.ir.FunctionInfo
import me.mattco.reeva.ir.opcodes.IrOpcode
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
    private val globalEnv: GlobalEnvRecord

    private val info = function.info

    private val registers = Registers(info.registerCount)
    private var accumulator by registers::accumulator
    private var ip = 0
    private var isDone = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.constantPool.size) { null }

    private val envStack = mutableListOf<EnvRecord>()
    private var currentEnv = function.envRecord

    init {
        var env: EnvRecord? = currentEnv
        while (env != null) {
            envStack.add(env)
            env = env.outer
        }
        envStack.reverse()
        val topEnv = envStack.first()
        expect(topEnv is GlobalEnvRecord)
        globalEnv = topEnv
    }

    fun interpret(): EvaluationResult {
        try {
            while (!isDone) {
                try {
                    visit(info.code[ip++])
                } catch (e: ThrowException) {
                    val handler = info.handlers.firstOrNull { ip - 1 in it.start..it.end }

                    if (handler == null) {
                        exception = e
                        isDone = true
                    } else {
                        repeat(envStack.size - handler.contextDepth) {
                            currentEnv = envStack.removeLast()
                        }
                        ip = handler.handler
                    }
                }
            }
        } catch (e: Throwable) {
            println("Exception in FunctionInfo ${info.name} (length=${info.code.size}), opcode ${ip - 1}")
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

    override fun visitLdaConstant(opcode: IrOpcode) {
        accumulator = getMappedConstant(opcode.cpAt(0))
    }

    override fun visitLdaInt(opcode: IrOpcode) {
        accumulator = JSNumber(opcode.literalAt(0))
    }

    override fun visitLdar(opcode: IrOpcode) {
        accumulator = registers[opcode.regAt(0)]
    }

    override fun visitStar(opcode: IrOpcode) {
        registers[opcode.regAt(0)] = accumulator
    }

    override fun visitMov(opcode: IrOpcode) {
        registers[opcode.regAt(1)] = registers[opcode.regAt(0)]
    }

    override fun visitLdaNamedProperty(opcode: IrOpcode) {
        val key = loadConstant<String>(opcode.cpAt(1)).key()
        val obj = registers[opcode.regAt(0)] as JSObject
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(opcode.slotAt(2))

        accumulator = if (obj.shape !in slot.shapes) {
            val location = obj.getPropertyLocation(key)
            if (location != null) {
                slot.shapes[obj.shape] = location
                obj.getDescriptorAt(location).getActualValue(obj)
            } else JSUndefined
        } else obj.get(key)
    }

    override fun visitLdaKeyedProperty(opcode: IrOpcode) {
        val key = accumulator.toPropertyKey()
        val obj = registers[opcode.regAt(0)] as JSObject
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(opcode.slotAt(1))

        accumulator = if (obj.shape !in slot.shapes) {
            val location = obj.getPropertyLocation(key)
            if (location != null) {
                slot.shapes[obj.shape] = location
                obj.getDescriptorAt(location).getActualValue(obj)
            } else JSUndefined
        } else obj.get(key)
    }

    override fun visitStaNamedProperty(opcode: IrOpcode) {
        val key = loadConstant<String>(opcode.cpAt(1)).key()
        val obj = registers[opcode.regAt(0)] as JSObject
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(opcode.slotAt(2))

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

    override fun visitStaKeyedProperty(opcode: IrOpcode) {
        val obj = registers[opcode.regAt(0)] as JSObject
        val key = registers[opcode.regAt(1)].toPropertyKey()
        val slot = info.feedbackVector.slotAs<ObjectFeedback>(opcode.slotAt(1))
        if (obj.shape !in slot.shapes)
            slot.shapes[obj.shape] = obj.getPropertyLocation(key)!!
        obj.set(key, accumulator)
    }

    override fun visitCreateArrayLiteral() {
        accumulator = JSArrayObject.create(function.realm)
    }

    override fun visitStaArrayLiteral(opcode: IrOpcode) {
        val array = registers[opcode.regAt(0)] as JSObject
        val index = (registers[opcode.regAt(1)] as JSNumber).asInt
        array.indexedProperties.set(array, index, accumulator)
    }

    override fun visitStaArrayLiteralIndex(opcode: IrOpcode) {
        val array = registers[opcode.regAt(0)] as JSObject
        array.indexedProperties.set(array, opcode.literalAt(1), accumulator)
    }

    override fun visitCreateObjectLiteral() {
        accumulator = JSObject.create(function.realm)
    }

    override fun visitAdd(opcode: IrOpcode) {
        binaryOp(opcode, "+")
    }

    override fun visitSub(opcode: IrOpcode) {
        binaryOp(opcode, "-")
    }

    override fun visitMul(opcode: IrOpcode) {
        binaryOp(opcode, "*")
    }

    override fun visitDiv(opcode: IrOpcode) {
        binaryOp(opcode, "/")
    }

    override fun visitMod(opcode: IrOpcode) {
        binaryOp(opcode, "%")
    }

    override fun visitExp(opcode: IrOpcode) {
        binaryOp(opcode, "**")
    }

    override fun visitBitwiseOr(opcode: IrOpcode) {
        binaryOp(opcode, "|")
    }

    override fun visitBitwiseXor(opcode: IrOpcode) {
        binaryOp(opcode, "^")
    }

    override fun visitBitwiseAnd(opcode: IrOpcode) {
        binaryOp(opcode, "&")
    }

    override fun visitShiftLeft(opcode: IrOpcode) {
        binaryOp(opcode, "<<")
    }

    override fun visitShiftRight(opcode: IrOpcode) {
        binaryOp(opcode, ">>")
    }

    override fun visitShiftRightUnsigned(opcode: IrOpcode) {
        binaryOp(opcode, ">>>")
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

    override fun visitStringAppend(opcode: IrOpcode) {
        val template = registers[opcode.regAt(0)] as JSString
        registers[opcode.regAt(0)] = JSString(
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

    override fun visitDeletePropertySloppy(opcode: IrOpcode) {
        val target = registers[opcode.regAt(0)]
        accumulator = if (target is JSObject) {
            target.delete(accumulator.toPropertyKey()).toValue()
        } else JSTrue
    }

    override fun visitDeletePropertyStrict(opcode: IrOpcode) {
        val target = registers[opcode.regAt(0)]
        if (target is JSObject) {
            val key = accumulator.toPropertyKey()
            if (!target.delete(key))
                Errors.StrictModeFailedDelete(key, target.toJSString().string)
        }

        accumulator = JSTrue
    }

    override fun visitLdaGlobal(opcode: IrOpcode) {
        val name = loadConstant<String>(opcode.cpAt(0))
        val desc = globalEnv.extension().getPropertyDescriptor(name.key())
            ?: Errors.UnknownReference(name.key()).throwReferenceError()
        accumulator = desc.getActualValue(globalEnv.extension())
    }

    override fun visitStaGlobal(opcode: IrOpcode) {
        val name = loadConstant<String>(opcode.cpAt(0))
        globalEnv.extension().set(name.key(), accumulator)
    }

    override fun visitLdaCurrentEnv(opcode: IrOpcode) {
        expect(currentEnv !is GlobalEnvRecord)
        accumulator = currentEnv.getBinding(opcode.literalAt(0))
    }

    override fun visitStaCurrentEnv(opcode: IrOpcode) {
        expect(currentEnv !is GlobalEnvRecord)
        currentEnv.setBinding(opcode.literalAt(0), accumulator)
    }

    override fun visitLdaEnv(opcode: IrOpcode) {
        expect(currentEnv !is GlobalEnvRecord)
        val envIndex = envStack.lastIndex - opcode.literalAt(1)
        accumulator = envStack[envIndex].getBinding(opcode.literalAt(0))
    }

    override fun visitStaEnv(opcode: IrOpcode) {
        expect(currentEnv !is GlobalEnvRecord)
        val envIndex = envStack.lastIndex - opcode.literalAt(1)
        envStack[envIndex].setBinding(opcode.literalAt(0), accumulator)
    }

    override fun visitPushEnv(opcode: IrOpcode) {
        val newEnv = EnvRecord(currentEnv, currentEnv.isStrict, opcode.literalAt(0))
        envStack.add(newEnv)
        currentEnv = newEnv
    }

    override fun visitPopCurrentEnv() {
        currentEnv = envStack.removeLast()
    }

    override fun visitPopEnvs(opcode: IrOpcode) {
        repeat(opcode.literalAt(0) - 1) {
            envStack.removeLast()
        }
        currentEnv = envStack.removeLast()
    }

    override fun visitCall(opcode: IrOpcode) {
        call(opcode.regAt(0), opcode.rangeAt(1), CallMode.Normal)
    }

    override fun visitCall0(opcode: IrOpcode) {
        call(opcode.regAt(0), RegisterRange(opcode.regAt(1), 1), CallMode.Normal)
    }

    override fun visitCall1(opcode: IrOpcode) {
        call(opcode.regAt(0), opcode.rangeAt(1), CallMode.OneArg)
    }

    override fun visitCallLastSpread(opcode: IrOpcode) {
        call(opcode.regAt(0), opcode.rangeAt(1), CallMode.LastSpread)
    }

    override fun visitCallFromArray(opcode: IrOpcode) {
        call(opcode.regAt(0), RegisterRange(opcode.regAt(1), 1), CallMode.Spread)
    }

    override fun visitCallRuntime(opcode: IrOpcode) {
        val args = getRegisterBlock(opcode.rangeAt(1))
        accumulator = InterpRuntime.values()[opcode.literalAt(0)].function(args)
    }

    override fun visitConstruct(opcode: IrOpcode) {
        val target = registers[opcode.regAt(0)]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString().string).throwTypeError()

        accumulator = Operations.construct(
            target,
            getRegisterBlock(opcode.rangeAt(1)),
            accumulator
        )
    }

    override fun visitConstruct0(opcode: IrOpcode) {
        val target = registers[opcode.regAt(0)]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString().string).throwTypeError()

        accumulator = Operations.construct(
            target,
            emptyList(),
            accumulator
        )
    }

    override fun visitConstructLastSpread(opcode: IrOpcode) {
        TODO()
    }

    override fun visitConstructFromArray(opcode: IrOpcode) {
        TODO()
    }

    override fun visitTestEqual(opcode: IrOpcode) {
        accumulator = Operations.abstractEqualityComparison(registers[opcode.regAt(0)], accumulator)
    }

    override fun visitTestNotEqual(opcode: IrOpcode) {
        accumulator = Operations.abstractEqualityComparison(registers[opcode.regAt(0)], accumulator).inv()
    }

    override fun visitTestEqualStrict(opcode: IrOpcode) {
        accumulator = Operations.strictEqualityComparison(registers[opcode.regAt(0)], accumulator)
    }

    override fun visitTestNotEqualStrict(opcode: IrOpcode) {
        accumulator = Operations.strictEqualityComparison(registers[opcode.regAt(0)], accumulator).inv()
    }

    override fun visitTestLessThan(opcode: IrOpcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(lhs, rhs, true)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestGreaterThan(opcode: IrOpcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(rhs, lhs, false)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestLessThanOrEqual(opcode: IrOpcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(rhs, lhs, false)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestGreaterThanOrEqual(opcode: IrOpcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(lhs, rhs, true)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestReferenceEqual(opcode: IrOpcode) {
        accumulator = (accumulator == registers[opcode.regAt(0)]).toValue()
    }

    override fun visitTestInstanceOf(opcode: IrOpcode) {
        accumulator = Operations.instanceofOperator(registers[opcode.regAt(0)], accumulator)
    }

    override fun visitTestIn(opcode: IrOpcode) {
        val rval = registers[opcode.regAt(0)].toPropertyKey()
        accumulator = Operations.hasProperty(accumulator, rval).toValue()
    }

    override fun visitTestNullish(opcode: IrOpcode) {
        accumulator = accumulator.let { it == JSNull || it == JSUndefined }.toValue()
    }

    override fun visitTestNull(opcode: IrOpcode) {
        accumulator = (accumulator == JSNull).toValue()
    }

    override fun visitTestUndefined(opcode: IrOpcode) {
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

    override fun visitJump(opcode: IrOpcode) {
        ip = opcode.instrAt(0)
    }

    override fun visitJumpIfTrue(opcode: IrOpcode) {
        if (accumulator == JSTrue)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfFalse(opcode: IrOpcode) {
        if (accumulator == JSFalse)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfToBooleanTrue(opcode: IrOpcode) {
        if (Operations.toBoolean(accumulator))
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfToBooleanFalse(opcode: IrOpcode) {
        if (!Operations.toBoolean(accumulator))
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNull(opcode: IrOpcode) {
        if (accumulator == JSNull)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNotNull(opcode: IrOpcode) {
        if (accumulator != JSNull)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfUndefined(opcode: IrOpcode) {
        if (accumulator == JSUndefined)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNotUndefined(opcode: IrOpcode) {
        if (accumulator != JSUndefined)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNullish(opcode: IrOpcode) {
        if (accumulator is JSObject)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNotNullish(opcode: IrOpcode) {
        if (accumulator.let { it == JSNull || it == JSUndefined })
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfObject(opcode: IrOpcode) {
        if (!accumulator.let { it == JSNull || it == JSUndefined })
            ip = opcode.instrAt(0)
    }

    override fun visitReturn() {
        isDone = true
    }

    override fun visitThrow() {
        val handler = info.handlers.firstOrNull { ip - 1 in it.start..it.end }
            ?: throw ThrowException(accumulator)
        repeat(envStack.size - handler.contextDepth) {
            currentEnv = envStack.removeLast()
        }
        ip = handler.handler
    }

    override fun visitThrowConstReassignment(opcode: IrOpcode) {
        Errors.AssignmentToConstant(loadConstant(opcode.cpAt(0))).throwTypeError()
    }

    override fun visitThrowUseBeforeInitIfEmpty(opcode: IrOpcode) {
        if (accumulator == JSEmpty)
            Errors.AccessBeforeInitialization(loadConstant(opcode.cpAt(0))).throwTypeError()
    }

    override fun visitDefineGetterProperty(opcode: IrOpcode) {
        defineAccessor(
            registers[opcode.regAt(0)] as JSObject,
            registers[opcode.regAt(1)],
            registers[opcode.regAt(2)] as JSFunction,
            isGetter = true,
        )
    }

    override fun visitDefineSetterProperty(opcode: IrOpcode) {
        defineAccessor(
            registers[opcode.regAt(0)] as JSObject,
            registers[opcode.regAt(1)],
            registers[opcode.regAt(2)] as JSFunction,
            isGetter = false,
        )
    }

    override fun visitDeclareGlobals(opcode: IrOpcode) {
        val array = loadConstant<DeclarationsArray>(opcode.cpAt(0))
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

    override fun visitCreateClosure(opcode: IrOpcode) {
        val newInfo = loadConstant<FunctionInfo>(opcode.cpAt(0))
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

    private fun binaryOp(opcode: IrOpcode, op: String) {
        val lhs = registers[opcode.regAt(0)]
        val slot = info.feedbackVector.slotAs<OpFeedback>(opcode.slotAt(1))
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
            if (globalEnv.hasRestrictedGlobalProperty(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError()
        }

        for (name in funcNames) {
            if (!globalEnv.canDeclareGlobalFunction(name))
                Errors.InvalidGlobalFunction(name).throwTypeError()
        }

        for (name in varNames) {
            if (!globalEnv.canDeclareGlobalVar(name))
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
        init {
            isConstructable = true
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
