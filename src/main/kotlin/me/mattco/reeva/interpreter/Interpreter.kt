package me.mattco.reeva.interpreter

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.DeclarationsArray
import me.mattco.reeva.interpreter.transformer.FunctionInfo
import me.mattco.reeva.interpreter.transformer.opcodes.Index
import me.mattco.reeva.interpreter.transformer.opcodes.IrOpcodeVisitor
import me.mattco.reeva.interpreter.transformer.opcodes.Literal
import me.mattco.reeva.interpreter.transformer.opcodes.Register
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.JSObject.Companion.initialize
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue
import me.mattco.reeva.utils.unreachable

class Interpreter(
    private val function: IRFunction,
    private val arguments: List<JSValue>,
) : IrOpcodeVisitor() {
    private val info = function.info
    private val realm: Realm
        get() = function.realm

    private val registers = Registers(info.code.registerCount)
    private var accumulator by registers::accumulator
    private var isDone = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.code.constantPool.size) { null }

    private var currentBlock = info.code.blocks[0]
    private var ip = 0

    fun interpret(): EvaluationResult {
        realm.pushEnv(function.envRecord)

        try {
            while (!isDone) {
                try {
                    visit(currentBlock[ip++])
                } catch (e: ThrowException) {
                    exception = e
                    isDone = true
                }
            }
        } catch (e: Throwable) {
            println("Exception in FunctionInfo ${info.name}, block=${currentBlock.index} opcode ${ip - 1}")
            throw e
        }

        return if (exception != null) {
            EvaluationResult.RuntimeError(exception!!.value)
        } else EvaluationResult.Success(accumulator)
    }

    private fun jumpTo(block: Block) {
        ip = 0
        currentBlock = block
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

    override fun visitLdaConstant(index: Index) {
        accumulator = getMappedConstant(index)
    }

    override fun visitLdaInt(int: Literal) {
        accumulator = JSNumber(int)
    }

    override fun visitLdar(reg: Register) {
        accumulator = registers[reg]
    }

    override fun visitStar(reg: Register) {
        registers[reg] = accumulator
    }

    override fun visitLdaNamedProperty(objectReg: Register, nameIndex: Index) {
        val obj = registers[objectReg] as JSObject
        val key = loadConstant<String>(nameIndex).key()
        accumulator = obj.get(key)
    }

    override fun visitLdaKeyedProperty(objectReg: Register) {
        val obj = registers[objectReg] as JSObject
        val key = accumulator.toPropertyKey(realm)
        accumulator = obj.get(key)
    }

    override fun visitStaNamedProperty(objectReg: Register, nameIndex: Index) {
        val obj = registers[objectReg] as JSObject
        val key = loadConstant<String>(nameIndex).key()
        obj.set(key, accumulator)
    }

    override fun visitStaKeyedProperty(objectReg: Register, nameReg: Register) {
        val obj = registers[objectReg] as JSObject
        val key = registers[nameReg].toPropertyKey(realm)
        obj.set(key, accumulator)
    }

    override fun visitCreateArray() {
        accumulator = JSArrayObject.create(function.realm)
    }

    override fun visitStaArrayIndex(arrayReg: Register, index: Literal) {
        val array = registers[arrayReg] as JSObject
        array.indexedProperties.set(array, index, accumulator)
    }

    override fun visitStaArray(arrayReg: Register, indexReg: Register) {
        val array = registers[arrayReg] as JSObject
        val index = (registers[indexReg] as JSNumber).asInt
        array.indexedProperties.set(array, index, accumulator)
    }

    override fun visitCreateObject() {
        accumulator = JSObject.create(function.realm)
    }

    override fun visitAdd(lhsReg: Register) {
        binaryOp("+", lhsReg)
    }

    override fun visitSub(lhsReg: Register) {
        binaryOp("-", lhsReg)
    }

    override fun visitMul(lhsReg: Register) {
        binaryOp("*", lhsReg)
    }

    override fun visitDiv(lhsReg: Register) {
        binaryOp("/", lhsReg)
    }

    override fun visitMod(lhsReg: Register) {
        binaryOp("%", lhsReg)
    }

    override fun visitExp(lhsReg: Register) {
        binaryOp("**", lhsReg)
    }

    override fun visitBitwiseOr(lhsReg: Register) {
        binaryOp("|", lhsReg)
    }

    override fun visitBitwiseXor(lhsReg: Register) {
        binaryOp("^", lhsReg)
    }

    override fun visitBitwiseAnd(lhsReg: Register) {
        binaryOp("&", lhsReg)
    }

    override fun visitShiftLeft(lhsReg: Register) {
        binaryOp("<<", lhsReg)
    }

    override fun visitShiftRight(lhsReg: Register) {
        binaryOp(">>", lhsReg)
    }

    override fun visitShiftRightUnsigned(lhsReg: Register) {
        binaryOp(">>>", lhsReg)
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
        } else Operations.numericBitwiseNOT(realm, accumulator)
    }

    override fun visitStringAppend(lhsStringReg: Register) {
        val template = registers[lhsStringReg] as JSString
        registers[lhsStringReg] = JSString(
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

    override fun visitDeletePropertySloppy(objectReg: Register) {
        val target = registers[objectReg]
        accumulator = if (target is JSObject) {
            target.delete(accumulator.toPropertyKey(realm)).toValue()
        } else JSTrue
    }

    override fun visitDeletePropertyStrict(objectReg: Register) {
        val target = registers[objectReg]
        if (target is JSObject) {
            val key = accumulator.toPropertyKey(realm)
            if (!target.delete(key))
                Errors.StrictModeFailedDelete(key, target.toJSString(realm).string)
        }

        accumulator = JSTrue
    }

    override fun visitLdaGlobal(name: Index) {
        accumulator = realm.globalEnv.getBindingValue(
            loadConstant(name),
            realm.activeEnv.isStrict,
        )
    }

    override fun visitStaGlobal(name: Index) {
        realm.globalEnv.setMutableBinding(
            loadConstant(name),
            accumulator,
            realm.activeEnv.isStrict,
        )
    }

    override fun visitLdaCurrentEnv(name: Index) {
        accumulator = realm.activeEnv.getBindingValue(
            loadConstant(name),
            realm.activeEnv.isStrict,
        )
    }

    override fun visitStaCurrentEnv(name: Index) {
        realm.activeEnv.setMutableBinding(
            loadConstant(name),
            accumulator,
            realm.activeEnv.isStrict,
        )
    }

    override fun visitLdaEnv(name: Index, offset: Literal) {
        accumulator = realm.getOffsetEnv(offset).getBindingValue(
            loadConstant(name),
            realm.activeEnv.isStrict,
        )
    }

    override fun visitStaEnv(name: Index, offset: Literal) {
        realm.getOffsetEnv(offset).setMutableBinding(
            loadConstant(name),
            accumulator,
            realm.activeEnv.isStrict,
        )
    }

    override fun visitPushLexicalEnv() {
        realm.pushEnv(DeclarativeEnvRecord(realm, realm.activeEnv.isStrict))
    }

    override fun visitPopEnv() {
        realm.popEnv()
    }

    override fun visitCall(targetReg: Register, receiverReg: Register, argumentRegs: List<Register>) {
        accumulator = Operations.call(
            realm,
            registers[targetReg],
            registers[receiverReg],
            argumentRegs.map { registers[it] }
        )
    }

    override fun visitCallWithArgArray(targetReg: Register, receiverReg: Register, argumentReg: Register) {
        val argumentsArray = registers[argumentReg] as JSObject
        accumulator = Operations.call(
            realm,
            registers[targetReg],
            registers[receiverReg],
            (0 until argumentsArray.indexedProperties.arrayLikeSize).map {
                argumentsArray.get(it)
            }
        )
    }

    override fun visitConstruct(targetReg: Register, newTargetReg: Register, argumentRegs: List<Register>) {
        val target = registers[targetReg]
        if (!Operations.isConstructor(target))
            Errors.NotACtor(target.toJSString(realm).string).throwTypeError(realm)

        accumulator = Operations.construct(
            target,
            argumentRegs.map { registers[it] },
            registers[newTargetReg],
        )
    }

    override fun visitConstructWithArgArray(targetReg: Register, newTargetReg: Register, argumentReg: Register) {
        TODO()
    }

    override fun visitTestEqual(lhsReg: Register) {
        accumulator = Operations.abstractEqualityComparison(realm, registers[lhsReg], accumulator)
    }

    override fun visitTestNotEqual(lhsReg: Register) {
        accumulator = Operations.abstractEqualityComparison(realm, registers[lhsReg], accumulator).inv()
    }

    override fun visitTestEqualStrict(lhsReg: Register) {
        accumulator = Operations.strictEqualityComparison(registers[lhsReg], accumulator)
    }

    override fun visitTestNotEqualStrict(lhsReg: Register) {
        accumulator = Operations.strictEqualityComparison(registers[lhsReg], accumulator).inv()
    }

    override fun visitTestLessThan(lhsReg: Register) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(realm, lhs, rhs, true)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestGreaterThan(lhsReg: Register) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(realm, rhs, lhs, false)
        accumulator = if (result == JSUndefined) JSFalse else result
    }

    override fun visitTestLessThanOrEqual(lhsReg: Register) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(realm, rhs, lhs, false)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestGreaterThanOrEqual(lhsReg: Register) {
        val lhs = registers[lhsReg]
        val rhs = accumulator
        val result = Operations.abstractRelationalComparison(realm, lhs, rhs, true)
        accumulator = if (result == JSFalse) JSTrue else JSFalse
    }

    override fun visitTestReferenceEqual(lhsReg: Register) {
        accumulator = (accumulator == registers[lhsReg]).toValue()
    }

    override fun visitTestInstanceOf(lhsReg: Register) {
        accumulator = Operations.instanceofOperator(realm, registers[lhsReg], accumulator)
    }

    override fun visitTestIn(lhsReg: Register) {
        val rval = registers[lhsReg].toPropertyKey(realm)
        accumulator = Operations.hasProperty(accumulator, rval).toValue()
    }

    override fun visitTestNullish() {
        accumulator = accumulator.let { it == JSNull || it == JSUndefined }.toValue()
    }

    override fun visitTestNull() {
        accumulator = (accumulator == JSNull).toValue()
    }

    override fun visitTestUndefined() {
        accumulator = (accumulator == JSUndefined).toValue()
    }

    override fun visitToBoolean() {
        accumulator = Operations.toBoolean(accumulator).toValue()
    }

    override fun visitToNumber() {
        accumulator = Operations.toNumber(realm, accumulator)
    }

    override fun visitToNumeric() {
        accumulator = Operations.toNumeric(realm, accumulator)
    }

    override fun visitToObject() {
        accumulator = Operations.toObject(realm, accumulator)
    }

    override fun visitToString() {
        accumulator = Operations.toString(realm, accumulator)
    }

    override fun visitJump(ifBlock: Block) {
        jumpTo(ifBlock)
    }

    override fun visitJumpIfTrue(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator == JSTrue) ifBlock else elseBlock)
    }

    override fun visitJumpIfNull(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator == JSNull) ifBlock else elseBlock)
    }

    override fun visitJumpIfUndefined(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator == JSUndefined) ifBlock else elseBlock)
    }

    override fun visitJumpIfNullish(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator.isNullish) ifBlock else elseBlock)
    }

    override fun visitJumpIfObject(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator is JSObject) ifBlock else elseBlock)
    }

    override fun visitReturn() {
        isDone = true
    }

    override fun visitThrow() {
        throw ThrowException(accumulator)
    }

    override fun visitThrowConstReassignment(nameIndex: Index) {
        Errors.AssignmentToConstant(loadConstant(nameIndex)).throwTypeError(realm)
    }

    override fun visitThrowUseBeforeInitIfEmpty(nameIndex: Index) {
        if (accumulator == JSEmpty)
            Errors.AccessBeforeInitialization(loadConstant(nameIndex)).throwTypeError(realm)
    }

    override fun visitDefineGetterProperty(objectReg: Register, nameReg: Register, methodReg: Register) {
        defineAccessor(
            registers[objectReg] as JSObject,
            registers[nameReg],
            registers[methodReg] as JSFunction,
            isGetter = true,
        )
    }

    override fun visitDefineSetterProperty(objectReg: Int, nameReg: Int, methodReg: Int) {
        defineAccessor(
            registers[objectReg] as JSObject,
            registers[nameReg],
            registers[methodReg] as JSFunction,
            isGetter = false,
        )
    }

    override fun visitDeclareGlobals(declarationsIndex: Index) {
        val array = loadConstant<DeclarationsArray>(declarationsIndex)
        declareGlobals(array)
    }

    override fun visitCreateMappedArgumentsObject() {
        // TODO
    }

    override fun visitCreateUnmappedArgumentsObject() {
        // TODO
    }

    override fun visitGetIterator() {
        accumulator = Operations.getIterator(realm, Operations.toObject(realm, accumulator)).iterator
        if (accumulator !is JSObject)
            Errors.NonObjectIterator.throwTypeError(realm)
    }

    override fun visitCreateClosure(functionInfoIndex: Int) {
        val newInfo = loadConstant<FunctionInfo>(functionInfoIndex)
        val function = IRFunction(function.realm, newInfo).initialize()
        accumulator = function
        val functionEnv = FunctionEnvRecord(realm, newInfo.isStrict, function)
        function.envRecord = functionEnv
    }

    override fun visitDebugBreakpoint() {
        TODO()
    }

    private fun defineAccessor(obj: JSObject, property: JSValue, method: JSFunction, isGetter: Boolean) {
        val key = property.toPropertyKey(realm)
        Operations.setFunctionName(realm, method, key, if (isGetter) "get" else "set")
        val accessor = if (isGetter) JSAccessor(method, null) else JSAccessor(null, method)

        Operations.definePropertyOrThrow(
            realm,
            obj,
            key,
            Descriptor(accessor, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE)
        )
    }

    private fun binaryOp(op: String, lhsReg: Register) {
        val lhs = registers[lhsReg]
        accumulator = Operations.applyStringOrNumericBinaryOperator(realm, lhs, accumulator, op)
    }

    private fun declareGlobals(array: DeclarationsArray) {
        val varNames = array.varIterator().toList()
        val lexNames = array.lexIterator().toList()
        val funcNames = array.funcIterator().toList()

        for (name in lexNames) {
            if (realm.globalEnv.hasRestrictedGlobalProperty(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError(realm)
        }

        for (name in funcNames) {
            if (!realm.globalEnv.canDeclareGlobalFunction(name))
                Errors.InvalidGlobalFunction(name).throwTypeError(realm)
        }

        for (name in varNames) {
            if (!realm.globalEnv.canDeclareGlobalVar(name))
                Errors.InvalidGlobalVar(name).throwTypeError(realm)
        }
    }

    enum class CallMode {
        Normal,
        OneArg,
        LastSpread,
        Spread,
    }

    private fun getMappedConstant(index: Int): JSValue {
        return mappedCPool[index] ?: when (val value = info.code.constantPool[index]) {
            is Int -> JSNumber(value)
            is Double -> JSNumber(value)
            is String -> JSString(value)
            is FunctionInfo -> TODO()
            else -> unreachable()
        }.also { mappedCPool[index] = it }
    }

    private inline fun <reified T> loadConstant(index: Int): T {
        return info.code.constantPool[index] as T
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

    class IRFunction(realm: Realm, val info: FunctionInfo) : JSFunction(realm, info.isStrict) {
        lateinit var envRecord: EnvRecord

        override fun init() {
            super.init()

            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue) + arguments
            val result = Interpreter(this, args).interpret()
            if (result is EvaluationResult.RuntimeError)
                throw ThrowException(result.value)
            return result.value
        }
    }

    companion object {
        fun wrap(info: FunctionInfo, realm: Realm): JSFunction {
            return IRFunction(realm, info).initialize().also {
                it.envRecord = GlobalEnvRecord(realm, info.isStrict)
            }
        }
    }
}
