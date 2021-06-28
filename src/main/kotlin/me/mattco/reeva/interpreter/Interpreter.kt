package me.mattco.reeva.interpreter

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.interpreter.transformer.Block
import me.mattco.reeva.interpreter.transformer.FunctionInfo
import me.mattco.reeva.interpreter.transformer.opcodes.Index
import me.mattco.reeva.interpreter.transformer.opcodes.IrOpcodeVisitor
import me.mattco.reeva.interpreter.transformer.opcodes.Literal
import me.mattco.reeva.interpreter.transformer.opcodes.Register
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.generators.JSGeneratorObject
import me.mattco.reeva.runtime.iterators.JSObjectPropertyIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.JSObject.Companion.initialize
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.*

class DeclarationsArray(
    varDecls: List<String>,
    lexDecls: List<String>,
    funcDecls: List<String>,
) : Iterable<String> {
    private val values = varDecls.toTypedArray() + lexDecls.toTypedArray() + funcDecls.toTypedArray()
    private val firstLex = varDecls.size
    private val firstFunc = firstLex + lexDecls.size

    val size: Int
        get() = values.size

    override fun iterator() = values.iterator()

    fun varIterator() = getValuesIterator(0, firstLex)
    fun lexIterator() = getValuesIterator(firstLex, firstFunc)
    fun funcIterator() = getValuesIterator(firstFunc, values.size)

    private fun getValuesIterator(start: Int, end: Int) = object : Iterable<String> {
        override fun iterator() = object : Iterator<String> {
            private var i = start

            override fun hasNext() = i < end
            override fun next() = values[i++]
        }
    }
}

class JumpTable private constructor(
    private val table: MutableMap<Int, Block>
) : MutableMap<Int, Block> by table {
    constructor() : this(mutableMapOf())

    companion object {
        const val FALLTHROUGH = 0
        const val RETURN = 1
        const val THROW = 2
    }
}

class Interpreter(
    private val realm: Realm,
    private val function: IRFunction,
    private val arguments: List<JSValue>
) : IrOpcodeVisitor() {
    private val info: FunctionInfo
        get() = function.info

    private val registers = Registers(info.argCount + info.code.registerCount)
    private var accumulator by registers::accumulator
    private var isDone = false
    private var isSuspended = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.code.constantPool.size) { null }

    private var lexicalDepth = 0
    private val lexicalDepthStack = mutableListOf<Int>()
    private val seenHandlers = mutableSetOf<Int>()

    private var currentBlock = info.code.blocks[0]
    private var ip = 0

    fun interpretImpl(): EvaluationResult {
        try {
            while (!isDone && !isSuspended) {
                try {
                    visit(currentBlock[ip++])
                } catch (e: ThrowException) {
                    val handler = currentBlock.handler
                    if (handler != null) {
                        val lexicalEnvsToRemove = lexicalDepth - lexicalDepthStack.last()
                        expect(lexicalEnvsToRemove >= 0)
                        repeat(lexicalEnvsToRemove) {
                            realm.lexEnv = realm.lexEnv.outer!!
                        }

                        accumulator = e.value
                        jumpTo(handler)
                    } else {
                        exception = e
                        break
                    }
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

    fun interpret(): EvaluationResult {
        lexicalDepthStack.add(lexicalDepth)
        return interpretImpl()
    }

    fun reenterGeneratorFunction(entryMode: GeneratorEntryMode, value: JSValue): JSValue? {
        if (isDone)
            return null

        isSuspended = false

        when (entryMode) {
            GeneratorEntryMode.Next -> {
                accumulator = value
            }
            GeneratorEntryMode.Return -> {
                isDone = true
                return value
            }
            GeneratorEntryMode.Throw -> {
                TODO()
            }
        }

        val result = interpret()

        if (result is EvaluationResult.RuntimeError)
            throw ThrowException(result.value)

        expect(result is EvaluationResult.Success)
        return result.value
    }

    private fun jumpTo(block: Block) {
        val currentHandler = currentBlock.handler?.index
        val newHandler = block.handler?.index

        if (currentHandler != newHandler) {
            when {
                currentHandler == null && newHandler != null -> {
                    seenHandlers.add(newHandler)
                    lexicalDepthStack.add(lexicalDepth)
                }
                currentHandler != null && newHandler == null -> {
                    lexicalDepthStack.removeLast()
                }
                currentHandler != null && newHandler != null -> {
                    if (newHandler in seenHandlers) {
                        lexicalDepthStack.removeLast()
                    } else {
                        seenHandlers.add(newHandler)
                        lexicalDepthStack.add(lexicalDepth)
                    }
                }
            }
        }

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
        accumulator = JSArrayObject.create(realm)
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
        accumulator = JSObject.create(realm)
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
        val actualName = loadConstant<String>(name)
        if (!realm.globalEnv.hasBinding(actualName))
            Errors.NotDefined(actualName).throwReferenceError(realm)

        accumulator = realm.globalEnv.getBindingValue(
            actualName,
            realm.varEnv.isStrict,
        )
    }

    override fun visitStaGlobal(name: Index) {
        realm.globalEnv.setMutableBinding(
            loadConstant(name),
            accumulator,
            realm.varEnv.isStrict,
        )
    }

    override fun visitLdaCurrentEnv(name: Index) {
        // The current env will always be the lexical env. Even at the top-level
        // of a function, the lexEnv will be equal to the varEnv, so we can still
        // access this through .lexEnv
        accumulator = realm.lexEnv.getBindingValue(
            loadConstant(name),
            realm.varEnv.isStrict,
        )
    }

    override fun visitStaCurrentEnv(name: Index) {
        // The current env will always be the lexical env. Even at the top-level
        // of a function, the lexEnv will be equal to the varEnv, so we can still
        // access this through .lexEnv
        realm.lexEnv.setMutableBinding(
            loadConstant(name),
            accumulator,
            realm.varEnv.isStrict,
        )
    }

    override fun visitLdaEnv(name: Index, offset: Literal) {
        accumulator = realm.getOffsetLexEnv(offset).getBindingValue(
            loadConstant(name),
            realm.varEnv.isStrict,
        )
    }

    override fun visitStaEnv(name: Index, offset: Literal) {
        realm.getOffsetLexEnv(offset).setMutableBinding(
            loadConstant(name),
            accumulator,
            realm.varEnv.isStrict,
        )
    }

    override fun visitPushLexicalEnv() {
        lexicalDepth++
        realm.lexEnv = DeclarativeEnvRecord(realm, realm.varEnv.isStrict, realm.lexEnv)
    }

    override fun visitPopEnv() {
        lexicalDepth--
        realm.lexEnv = realm.lexEnv.outer!!
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
        val argumentsArray = registers[argumentReg] as JSObject
        accumulator = Operations.construct(
            registers[targetReg],
            (0 until argumentsArray.indexedProperties.arrayLikeSize).map {
                argumentsArray.get(it)
            },
            registers[newTargetReg],
        )
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

    override fun visitJumpIfToBooleanTrue(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator.toBoolean()) ifBlock else elseBlock)
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

    override fun visitJumpFromTable(tableIndex: Index) {
        val table = loadConstant<JumpTable>(tableIndex)
        jumpTo(table[accumulator.asInt]!!)
    }

    override fun visitForInEnumerate() {
        val target = accumulator.toObject(realm)
        val iterator = JSObjectPropertyIterator.create(realm, target)
        val nextMethod = Operations.getV(realm, iterator, PropertyKey.from("next"))
        val iteratorRecord = Operations.IteratorRecord(iterator, nextMethod, false)
        accumulator = iteratorRecord
    }

    override fun visitReturn() {
        isDone = true
    }

    override fun visitYield(continuationBlock: Block) {
        isSuspended = true
        jumpTo(continuationBlock)
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
        val argsObject = Operations.createMappedArgumentsObject(realm, function, info.parameters!!, arguments.drop(1), realm.varEnv)
        createArgumentsBinding(argsObject)
    }

    override fun visitCreateUnmappedArgumentsObject() {
        val argsObject = Operations.createUnmappedArgumentsObject(realm, arguments.drop(1))
        createArgumentsBinding(argsObject)
    }

    private fun createArgumentsBinding(obj: JSValue) {
        val varEnv = realm.varEnv
        if (varEnv.isStrict) {
            varEnv.createImmutableBinding("arguments", false)
        } else {
            varEnv.createMutableBinding("arguments", false)
        }
        varEnv.initializeBinding("arguments", obj)
    }

    override fun visitGetIterator() {
        accumulator = Operations.getIterator(realm, Operations.toObject(realm, accumulator))
    }

    override fun visitIteratorNext() {
        accumulator = Operations.iteratorNext(accumulator as Operations.IteratorRecord)
    }

    override fun visitIteratorResultDone() {
        accumulator = Operations.iteratorComplete(accumulator).toValue()
    }

    override fun visitIteratorResultValue() {
        accumulator = Operations.iteratorValue(accumulator)
    }

    override fun visitCreateClosure(functionInfoIndex: Int) {
        val newInfo = loadConstant<FunctionInfo>(functionInfoIndex)
        accumulator = NormalIRFunction(realm, newInfo, realm.lexEnv).initialize()
    }

    override fun visitCreateGeneratorClosure(functionInfoIndex: Int) {
        val newInfo = loadConstant<FunctionInfo>(functionInfoIndex)
        accumulator = GeneratorIRFunction(realm, newInfo).initialize()
    }

    override fun visitCreateRestParam() {
        accumulator = Operations.createArrayFromList(realm, arguments.takeLast(arguments.size - info.argCount + 1))
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

    enum class CallMode {
        Normal,
        OneArg,
        LastSpread,
        Spread,
    }

    enum class GeneratorEntryMode {
        Next,
        Return,
        Throw,
    }

    abstract class IRFunction(
        realm: Realm,
        val info: FunctionInfo,
        prototype: JSValue = realm.functionProto,
    ) : JSFunction(realm, info.isStrict, prototype)

    class NormalIRFunction(realm: Realm, info: FunctionInfo, val outerEnvRecord: EnvRecord?) : IRFunction(realm, info) {
        override fun init() {
            super.init()

            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            val envRecord = if (info.isTopLevelScript) {
                GlobalEnvRecord(realm, info.isStrict)
            } else {
                FunctionEnvRecord(realm, info.isStrict, outerEnvRecord!!, this)
            }

            return realm.withEnv(envRecord) {
                val args = listOf(arguments.thisValue) + arguments
                val result = Interpreter(realm, this, args).interpret()
                if (result is EvaluationResult.RuntimeError)
                    throw ThrowException(result.value)
                result.value
            }
        }
    }

    // This does not need an outerEnvRecord field as it's envrecord is initialized in the
    // init method, which is invoked immediately after construction (so realm.lexEnv is
    // guaranteed to not be null)
    class GeneratorIRFunction(realm: Realm, info: FunctionInfo) : IRFunction(realm, info, realm.generatorFunctionProto) {
        lateinit var generatorObject: JSGeneratorObject
        lateinit var envRecord: EnvRecord

        override fun init() {
            super.init()

            envRecord = FunctionEnvRecord(realm, info.isStrict, realm.lexEnv, this)

            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            if (!::generatorObject.isInitialized) {
                expect(!info.isTopLevelScript)
                val interpreter = Interpreter(realm, this, arguments)
                generatorObject = JSGeneratorObject.create(realm, interpreter, envRecord)
            }

            return generatorObject
        }
    }

    companion object {
        fun wrap(
            info: FunctionInfo,
            realm: Realm,
            outerEnvRecord: EnvRecord? = null,
        ) = NormalIRFunction(realm, info, outerEnvRecord).initialize()
    }
}
