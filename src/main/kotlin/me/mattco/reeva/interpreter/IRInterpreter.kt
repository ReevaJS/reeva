package me.mattco.reeva.interpreter

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.ir.*
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
) : OpcodeVisitor() {
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

    override fun visitLdaConstant(opcode: Opcode) {
        accumulator = getMappedConstant(opcode.cpAt(0))
    }

    override fun visitLdaInt(opcode: Opcode) {
        accumulator = JSNumber(opcode.literalAt(0))
    }

    override fun visitLdar(opcode: Opcode) {
        accumulator = registers[opcode.regAt(0)]
    }

    override fun visitStar(opcode: Opcode) {
        registers[opcode.regAt(0)] = accumulator
    }

    override fun visitMov(opcode: Opcode) {
        registers[opcode.regAt(1)] = registers[opcode.regAt(0)]
    }

    override fun visitLdaNamedProperty(opcode: Opcode) {
        val name = loadConstant<String>(opcode.cpAt(1))
        val obj = registers[opcode.regAt(0)] as JSObject
        accumulator = obj.get(name)
    }

    override fun visitLdaKeyedProperty(opcode: Opcode) {
        val obj = registers[opcode.regAt(0)] as JSObject
        accumulator = obj.get(Operations.toPropertyKey(accumulator))
    }

    override fun visitStaNamedProperty(opcode: Opcode) {
        val name = loadConstant<String>(opcode.cpAt(1))
        val obj = registers[opcode.regAt(0)] as JSObject
        obj.set(name, accumulator)
    }

    override fun visitStaKeyedProperty(opcode: Opcode) {
        val obj = registers[opcode.regAt(0)] as JSObject
        val key = registers[opcode.regAt(1)]
        obj.set(Operations.toPropertyKey(key), accumulator)
    }

    override fun visitCreateArrayLiteral() {
        accumulator = JSArrayObject.create(function.realm)
    }

    override fun visitStaArrayLiteral(opcode: Opcode) {
        val array = registers[opcode.regAt(0)] as JSObject
        val index = (registers[opcode.regAt(1)] as JSNumber).asInt
        array.indexedProperties.set(array, index, accumulator)
    }

    override fun visitStaArrayLiteralIndex(opcode: Opcode) {
        val array = registers[opcode.regAt(0)] as JSObject
        array.indexedProperties.set(array, opcode.literalAt(1), accumulator)
    }

    override fun visitCreateObjectLiteral() {
        accumulator = JSObject.create(function.realm)
    }

    override fun visitAdd(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "+")
    }

    override fun visitSub(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "-")
    }

    override fun visitMul(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "*")
    }

    override fun visitDiv(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "/")
    }

    override fun visitMod(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "%")
    }

    override fun visitExp(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "**")
    }

    override fun visitBitwiseOr(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "|")
    }

    override fun visitBitwiseXor(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "^")
    }

    override fun visitBitwiseAnd(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "&")
    }

    override fun visitShiftLeft(opcode: Opcode) {
        binaryOp(opcode.regAt(0), "<<")
    }

    override fun visitShiftRight(opcode: Opcode) {
        binaryOp(opcode.regAt(0), ">>")
    }

    override fun visitShiftRightUnsigned(opcode: Opcode) {
        binaryOp(opcode.regAt(0), ">>>")
    }

    override fun visitInc() {
        accumulator = JSNumber(accumulator.asInt + 1)
    }

    override fun visitDec() {
        accumulator = JSNumber(accumulator.asInt - 1)
    }

    override fun visitNegate() {
        val expr = accumulator.toNumeric()
        accumulator = if (expr is JSBigInt) {
            Operations.bigintUnaryMinus(expr)
        } else Operations.numericUnaryMinus(accumulator)
    }

    override fun visitBitwiseNot() {
        val expr = accumulator.toNumeric()
        accumulator = if (expr is JSBigInt) {
            Operations.bigintBitwiseNOT(expr)
        } else Operations.numericBitwiseNOT(accumulator)
    }

    override fun visitStringAppend(opcode: Opcode) {
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

    override fun visitDeletePropertySloppy(opcode: Opcode) {
        val target = registers[opcode.regAt(0)]
        accumulator = if (target is JSObject) {
            target.delete(accumulator.toPropertyKey()).toValue()
        } else JSTrue
    }

    override fun visitDeletePropertyStrict(opcode: Opcode) {
        val target = registers[opcode.regAt(0)]
        if (target is JSObject) {
            val key = accumulator.toPropertyKey()
            if (!target.delete(key))
                Errors.StrictModeFailedDelete(key, target.toJSString().string)
        }

        accumulator = JSTrue
    }

    override fun visitLdaGlobal(opcode: Opcode) {
        val name = loadConstant<String>(opcode.cpAt(0))
        val desc = globalEnv.extension().getPropertyDescriptor(name.key())
            ?: Errors.UnknownReference(name.key()).throwReferenceError()
        accumulator = desc.getActualValue(globalEnv.extension())
    }

    override fun visitStaGlobal(opcode: Opcode) {
        val name = loadConstant<String>(opcode.cpAt(0))
        globalEnv.extension().set(name.key(), accumulator)
    }

    override fun visitLdaCurrentEnv(opcode: Opcode) {
        expect(currentEnv !is GlobalEnvRecord)
        accumulator = currentEnv.getBinding(opcode.literalAt(0))
    }

    override fun visitStaCurrentEnv(opcode: Opcode) {
        expect(currentEnv !is GlobalEnvRecord)
        currentEnv.setBinding(opcode.literalAt(0), accumulator)
    }

    override fun visitLdaEnv(opcode: Opcode) {
        expect(currentEnv !is GlobalEnvRecord)
        val envIndex = envStack.lastIndex - opcode.literalAt(1)
        accumulator = envStack[envIndex].getBinding(opcode.literalAt(0))
    }

    override fun visitStaEnv(opcode: Opcode) {
        expect(currentEnv !is GlobalEnvRecord)
        val envIndex = envStack.lastIndex - opcode.literalAt(1)
        envStack[envIndex].setBinding(opcode.literalAt(0), accumulator)
    }

    override fun visitPushEnv(opcode: Opcode) {
        val newEnv = EnvRecord(currentEnv, currentEnv.isStrict, opcode.literalAt(0))
        envStack.add(newEnv)
        currentEnv = newEnv
    }

    override fun visitPopCurrentEnv() {
        currentEnv = envStack.removeLast()
    }

    override fun visitPopEnvs(opcode: Opcode) {
        repeat(opcode.literalAt(0) - 1) {
            envStack.removeLast()
        }
        currentEnv = envStack.removeLast()
    }

    override fun visitCall(opcode: Opcode) {
        call(opcode.regAt(0), opcode.rangeAt(1), CallMode.Normal)
    }

    override fun visitCall0(opcode: Opcode) {
        call(opcode.regAt(0), RegisterRange(opcode.regAt(1), 0), CallMode.Normal)
    }

    override fun visitCall1(opcode: Opcode) {
        call(opcode.regAt(0), opcode.rangeAt(1), CallMode.OneArg)
    }

    override fun visitCallLastSpread(opcode: Opcode) {
        call(opcode.regAt(0), opcode.rangeAt(1), CallMode.LastSpread)
    }

    override fun visitCallFromArray(opcode: Opcode) {
        call(opcode.regAt(0), RegisterRange(opcode.regAt(1), 1), CallMode.Spread)
    }

    override fun visitCallRuntime(opcode: Opcode) {
        val args = getRegisterBlock(opcode.rangeAt(1))
        accumulator = InterpRuntime.values()[opcode.literalAt(0)].function(args)
    }

    override fun visitConstruct(opcode: Opcode) {
        val target = registers[opcode.regAt(0)]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString().string).throwTypeError()

        accumulator = Operations.construct(
            target,
            getRegisterBlock(opcode.rangeAt(1)),
            accumulator
        )
    }

    override fun visitConstruct0(opcode: Opcode) {
        val target = registers[opcode.regAt(0)]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString().string).throwTypeError()

        accumulator = Operations.construct(
            target,
            emptyList(),
            accumulator
        )
    }

    override fun visitConstructLastSpread(opcode: Opcode) {
        TODO()
    }

    override fun visitConstructFromArray(opcode: Opcode) {
        TODO()
    }

    override fun visitTestEqual(opcode: Opcode) {
        accumulator = Operations.abstractEqualityComparison(registers[opcode.regAt(0)], accumulator)
    }

    override fun visitTestNotEqual(opcode: Opcode) {
        accumulator = Operations.abstractEqualityComparison(registers[opcode.regAt(0)], accumulator).inv()
    }

    override fun visitTestEqualStrict(opcode: Opcode) {
        accumulator = Operations.strictEqualityComparison(registers[opcode.regAt(0)], accumulator)
    }

    override fun visitTestNotEqualStrict(opcode: Opcode) {
        accumulator = Operations.strictEqualityComparison(registers[opcode.regAt(0)], accumulator).inv()
    }

    override fun visitTestLessThan(opcode: Opcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(lhs, rhs, true)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestGreaterThan(opcode: Opcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(rhs, lhs, false)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestLessThanOrEqual(opcode: Opcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(rhs, lhs, false)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestGreaterThanOrEqual(opcode: Opcode) {
        val lhs = registers[opcode.regAt(0)]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(lhs, rhs, true)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestReferenceEqual(opcode: Opcode) {
        accumulator = (accumulator == registers[opcode.regAt(0)]).toValue()
    }

    override fun visitTestInstanceOf(opcode: Opcode) {
        accumulator = Operations.instanceofOperator(registers[opcode.regAt(0)], accumulator)
    }

    override fun visitTestIn(opcode: Opcode) {
        val rval = registers[opcode.regAt(0)].toPropertyKey()
        accumulator = Operations.hasProperty(accumulator, rval).toValue()
    }

    override fun visitTestNullish(opcode: Opcode) {
        accumulator = accumulator.let { it == JSNull || it == JSUndefined }.toValue()
    }

    override fun visitTestNull(opcode: Opcode) {
        accumulator = (accumulator == JSNull).toValue()
    }

    override fun visitTestUndefined(opcode: Opcode) {
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

    override fun visitJump(opcode: Opcode) {
        ip = opcode.instrAt(0)
    }

    override fun visitJumpIfTrue(opcode: Opcode) {
        if (accumulator == JSTrue)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfFalse(opcode: Opcode) {
        if (accumulator == JSFalse)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfToBooleanTrue(opcode: Opcode) {
        if (Operations.toBoolean(accumulator))
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfToBooleanFalse(opcode: Opcode) {
        if (!Operations.toBoolean(accumulator))
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNull(opcode: Opcode) {
        if (accumulator == JSNull)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNotNull(opcode: Opcode) {
        if (accumulator != JSNull)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfUndefined(opcode: Opcode) {
        if (accumulator == JSUndefined)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNotUndefined(opcode: Opcode) {
        if (accumulator != JSUndefined)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNullish(opcode: Opcode) {
        if (accumulator is JSObject)
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfNotNullish(opcode: Opcode) {
        if (accumulator.let { it == JSNull || it == JSUndefined })
            ip = opcode.instrAt(0)
    }

    override fun visitJumpIfObject(opcode: Opcode) {
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

    override fun visitThrowConstReassignment(opcode: Opcode) {
        Errors.AssignmentToConstant(loadConstant(opcode.cpAt(0))).throwTypeError()
    }

    override fun visitThrowUseBeforeInitIfEmpty(opcode: Opcode) {
        if (accumulator == JSEmpty)
            Errors.AccessBeforeInitialization(loadConstant(opcode.cpAt(0))).throwTypeError()
    }

    override fun visitDefineGetterProperty(opcode: Opcode) {
        defineAccessor(
            registers[opcode.regAt(0)] as JSObject,
            registers[opcode.regAt(1)],
            registers[opcode.regAt(2)] as JSFunction,
            isGetter = true,
        )
    }

    override fun visitDefineSetterProperty(opcode: Opcode) {
        defineAccessor(
            registers[opcode.regAt(0)] as JSObject,
            registers[opcode.regAt(1)],
            registers[opcode.regAt(2)] as JSFunction,
            isGetter = false,
        )
    }

    override fun visitDeclareGlobals(opcode: Opcode) {
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

    override fun visitCreateClosure(opcode: Opcode) {
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

    private fun binaryOp(lhs: Int, op: String) {
        accumulator = Operations.applyStringOrNumericBinaryOperator(
            registers[lhs], accumulator, op
        )
    }

    private fun call(callableReg: Int, range: RegisterRange, mode: CallMode) {
        val target = registers[callableReg]
        val receiver = registers[range.start]

        accumulator = when (mode) {
            CallMode.Normal -> {
                Operations.call(target, receiver, getRegisterBlock(range))
            }
            CallMode.OneArg -> {
                Operations.call(target, receiver, listOf(accumulator))
            }
            CallMode.LastSpread -> {
                val nonSpreadArgs = if (range.count > 0) {
                    getRegisterBlock(range.drop(1))
                } else emptyList()

                val spreadValues = Operations.iterableToList(registers[range.end])

                Operations.call(target, receiver, nonSpreadArgs + spreadValues)
            }
            CallMode.Spread -> {
                val argArray = registers[range.end] as JSArrayObject
                val args = (0 until argArray.indexedProperties.arrayLikeSize).map {
                    argArray.indexedProperties.get(argArray, it.toInt())
                }

                Operations.call(target, receiver, args)
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
