package me.mattco.reeva.interpreter

import me.mattco.reeva.core.EvaluationResult
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.DeclarativeEnvRecord
import me.mattco.reeva.core.environment.EnvRecord
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

class Interpreter(
    private val realm: Realm,
    private val function: IRFunction,
    private val arguments: List<JSValue>,
    initialEnvRecord: EnvRecord,
) : IrOpcodeVisitor() {
    private val info: FunctionInfo
        get() = function.info

    private val registers = Registers(info.argCount + info.code.registerCount)
    private var accumulator by registers::accumulator
    private var isDone = false
    private var isSuspended = false
    private var exception: ThrowException? = null
    private val mappedCPool = Array<JSValue?>(info.code.constantPool.size) { null }

    private var lexicalEnv = initialEnvRecord
    private var lexicalDepth = 0
    private val lexicalDepthStack = mutableListOf<Int>()
    private val seenHandlers = mutableSetOf<Int>()

    private var currentBlock = info.code.blocks[0]
    private var ip = 0

    private fun interpretImpl(): EvaluationResult {
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
                            lexicalEnv = lexicalEnv.outer!!
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

    override fun visitLdaEmpty() {
        accumulator = JSEmpty
    }

    override fun visitLdaUndefined() {
        accumulator = JSUndefined
    }

    override fun visitLdaNull() {
        accumulator = JSNull
    }

    override fun visitLdaTrue() {
        accumulator = JSTrue
    }

    override fun visitLdaFalse() {
        accumulator = JSFalse
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
        val obj = registers[objectReg].toObject(realm)
        val key = loadConstant<String>(nameIndex).key()
        accumulator = obj.get(key)
    }

    override fun visitLdaKeyedProperty(objectReg: Register) {
        val obj = registers[objectReg].toObject(realm)
        val key = accumulator.toPropertyKey(realm)
        accumulator = obj.get(key)
    }

    override fun visitStaNamedProperty(objectReg: Register, nameIndex: Index) {
        val obj = registers[objectReg].toObject(realm)
        val key = loadConstant<String>(nameIndex).key()
        obj.set(key, accumulator)
    }

    override fun visitStaKeyedProperty(objectReg: Register, nameReg: Register) {
        val obj = registers[objectReg].toObject(realm)
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

        accumulator = realm.globalEnv.getBinding(actualName)
    }

    override fun visitStaGlobal(name: Index) {
        realm.globalEnv.setBinding(loadConstant<String>(name), accumulator)
    }

    override fun visitLdaCurrentRecordSlot(slot: Literal) {
        accumulator = lexicalEnv.getBinding(slot)
    }

    override fun visitStaCurrentRecordSlot(slot: Literal) {
        lexicalEnv.setBinding(slot, accumulator)
    }

    override fun visitLdaRecordSlot(slot: Literal, distance: Literal) {
        var env = lexicalEnv
        repeat(distance) { env = env.outer!! }
        accumulator = env.getBinding(slot)
    }

    override fun visitStaRecordSlot(slot: Literal, distance: Literal) {
        var env = lexicalEnv
        repeat(distance) { env = env.outer!! }
        env.setBinding(slot, accumulator)
    }

    override fun visitPushWithEnvRecord() {
        lexicalDepth++
        TODO()
    }

    override fun visitPushDeclarativeEnvRecord(numSlots: Literal) {
        lexicalDepth++
        lexicalEnv = DeclarativeEnvRecord(lexicalEnv, numSlots)
    }

    override fun visitPopEnvRecord() {
        lexicalDepth--
        lexicalEnv = lexicalEnv.outer!!
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

    override fun visitJumpIfEmpty(ifBlock: Block, elseBlock: Block) {
        jumpTo(if (accumulator == JSEmpty) ifBlock else elseBlock)
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

    override fun visitCreateClass(classDescriptorIndex: Index, constructorReg: Register, superClassReg: Register, argRegs: List<Register>) {
        val classDescriptor = loadConstant<ClassDescriptor>(classDescriptorIndex)
        val methodDescriptors = classDescriptor.methodDescriptors.map { loadConstant<MethodDescriptor>(it) }
        val constructor = registers[constructorReg]
        val superClass = registers[superClassReg]
        val args = argRegs.map { registers[it] }

        expect(constructor is JSFunction)

        val protoParent: JSValue
        val constructorParent: JSObject

        when {
            superClass == JSEmpty -> {
                protoParent = realm.objectProto
                constructorParent = realm.functionProto
            }
            superClass == JSNull -> {
                protoParent = JSNull
                constructorParent = realm.functionProto
            }
            !Operations.isConstructor(superClass) ->
                Errors.NotACtor(superClass.toJSString(realm).string).throwTypeError(realm)
            else -> {
                protoParent = superClass.get("prototype")
                if (protoParent != JSNull && protoParent !is JSObject)
                    Errors.TODO("superClass.prototype invalid type").throwTypeError(realm)
                constructorParent = superClass
            }
        }

        val proto = JSObject.create(realm, protoParent)
        Operations.makeClassConstructor(constructor)

        // TODO
        // Operations.setFunctionName(constructor, className)

        Operations.makeConstructor(realm, constructor, false, proto)

        constructor.setPrototype(constructorParent)
        Operations.makeMethod(constructor, proto)

        if (superClass != JSEmpty)
            constructor.constructorKind = JSFunction.ConstructorKind.Derived

        Operations.createMethodProperty(proto, "constructor".key(), constructor)

        var argIndex = 0

        for (descriptor in methodDescriptors) {
            val name = descriptor.name?.key() ?: args[argIndex++].toPropertyKey(realm)
            val target = if (descriptor.isStatic) constructor else proto
            methodDefinitionEvaluation(name, descriptor, target, enumerable = false)
            argIndex++
        }

        accumulator = constructor
    }

    override fun visitCreateClassConstructor(functionInfoIndex: Int) {
        val newInfo = loadConstant<FunctionInfo>(functionInfoIndex)
        accumulator = NormalIRFunction(realm, newInfo, lexicalEnv, defineProto = false).initialize()
    }

    private fun methodDefinitionEvaluation(name: PropertyKey, method: MethodDescriptor, obj: JSObject, enumerable: Boolean) {
        val (_, _, kind, isGetter, isSetter, infoIndex) = method

        val info = loadConstant<FunctionInfo>(infoIndex)
        val closure = when (kind) {
            Operations.FunctionKind.Normal -> NormalIRFunction(realm, info, lexicalEnv).initialize()
            Operations.FunctionKind.Generator -> GeneratorIRFunction(realm, info, lexicalEnv).initialize()
            else -> TODO()
        }

        Operations.makeMethod(closure, obj)

        if (isGetter || isSetter) {
            val (prefix, desc) = if (isGetter) {
                "get" to Descriptor(JSAccessor(closure, null), Descriptor.CONFIGURABLE)
            } else {
                "set" to Descriptor(JSAccessor(null, closure), Descriptor.CONFIGURABLE)
            }

            Operations.setFunctionName(realm, closure, name, prefix)
            Operations.definePropertyOrThrow(realm, obj, name, desc)
            return
        }

        when (kind) {
            Operations.FunctionKind.Normal -> {}
            Operations.FunctionKind.Generator -> {
                val prototype = JSObject.create(realm, realm.generatorObjectProto)
                Operations.definePropertyOrThrow(realm, closure, "prototype".key(), Descriptor(prototype, Descriptor.WRITABLE))
            }
            else -> TODO()
        }

        Operations.defineMethodProperty(realm, name, obj, closure, enumerable)
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
        TODO()
//        val argsObject = Operations.createMappedArgumentsObject(realm, function, info.parameters!!, arguments.drop(1), realm.varEnv)
//        createArgumentsBinding(argsObject)
    }

    override fun visitCreateUnmappedArgumentsObject() {
        TODO()
//        val argsObject = Operations.createUnmappedArgumentsObject(realm, arguments.drop(1))
//        createArgumentsBinding(argsObject)
    }

    private fun createArgumentsBinding(obj: JSValue) {
        TODO()
//        val varEnv = realm.varEnv
//        if (info.isStrict) {
//            varEnv.createImmutableBinding("arguments", false)
//        } else {
//            varEnv.createMutableBinding("arguments", false)
//        }
//        varEnv.initializeBinding("arguments", obj)
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
        accumulator = NormalIRFunction(realm, newInfo, lexicalEnv).initialize()
    }

    override fun visitCreateGeneratorClosure(functionInfoIndex: Int) {
        val newInfo = loadConstant<FunctionInfo>(functionInfoIndex)
        accumulator = GeneratorIRFunction(realm, newInfo, lexicalEnv).initialize()
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

        // TODO: This is not spec compliant
        for (name in lexNames) {
            if (realm.globalEnv.hasBinding(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError(realm)
        }

        for (name in funcNames) {
            if (realm.globalEnv.hasBinding(name))
                Errors.InvalidGlobalFunction(name).throwTypeError(realm)
        }

        for (name in varNames) {
            if (realm.globalEnv.hasBinding(name))
                Errors.InvalidGlobalVar(name).throwTypeError(realm)
        }

        for (name in varNames)
            realm.globalEnv.setBinding(name, JSUndefined)
        for (name in funcNames)
            realm.globalEnv.setBinding(name, JSUndefined)
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
        val outerEnvRecord: EnvRecord,
        prototype: JSValue = realm.functionProto,
    ) : JSFunction(realm, info.isStrict, prototype)

    class NormalIRFunction(
        realm: Realm,
        info: FunctionInfo,
        outerEnvRecord: EnvRecord,
        // Class constructors cannot have their prototype property defined here
        private val defineProto: Boolean = true,
    ) : IRFunction(realm, info, outerEnvRecord) {
        override fun init() {
            super.init()

            if (defineProto)
                defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue) + arguments
            val result = Interpreter(realm, this, args, outerEnvRecord).interpret()
            if (result is EvaluationResult.RuntimeError)
                throw ThrowException(result.value)
            return result.value
        }
    }

    class GeneratorIRFunction(
        realm: Realm,
        info: FunctionInfo,
        outerEnvRecord: EnvRecord,
    ) : IRFunction(realm, info, outerEnvRecord, realm.generatorFunctionProto) {
        lateinit var generatorObject: JSGeneratorObject

        override fun init() {
            super.init()

            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            if (!::generatorObject.isInitialized) {
                expect(!info.isTopLevelScript)
                val interpreter = Interpreter(realm, this, listOf(arguments.thisValue, *arguments.toTypedArray()), outerEnvRecord)
                generatorObject = JSGeneratorObject.create(realm, interpreter, outerEnvRecord)
            }

            return generatorObject
        }
    }

    companion object {
        fun wrap(
            info: FunctionInfo,
            realm: Realm,
            outerEnvRecord: EnvRecord,
            kind: Operations.FunctionKind = Operations.FunctionKind.Normal
        ) = when (kind) {
            Operations.FunctionKind.Normal -> NormalIRFunction(realm, info, outerEnvRecord).initialize()
            Operations.FunctionKind.Generator -> GeneratorIRFunction(realm, info, outerEnvRecord).initialize()
            else -> TODO()
        }
    }
}
