package com.reevajs.reeva.interpreter

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.DeclarativeEnvRecord
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSUnmappedArgumentsObject
import com.reevajs.reeva.runtime.functions.JSBuiltinFunction
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.iterators.JSObjectPropertyIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.runtime.regexp.JSRegExpObject
import com.reevajs.reeva.transformer.*
import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.utils.*

class Interpreter(
    private val transformedSource: TransformedSource,
    private val arguments: List<JSValue>,
    private val topLevelPromiseCapability: AOs.PromiseCapability? = null,
) : OpcodeVisitor {
    private val info: FunctionInfo
        inline get() = transformedSource.functionInfo

    private val agent: Agent
        inline get() = Agent.activeAgent

    private val realm: Realm
        inline get() = agent.getActiveRealm()

    private val stack = ArrayDeque<Any>()
    private val locals = Array<Any?>(info.ir.locals.size) { null }

    private var moduleEnv = agent.activeEnvRecord as? ModuleEnvRecord

    private var activeBlock = info.ir.blocks[BlockIndex(0)]!!
    private var ip = 0
    private var shouldLoop = true

    var isDone: Boolean = false
        private set

    private var innerPromiseCapability = if (topLevelPromiseCapability != null) {
        AOs.newPromiseCapability(realm.promiseCtor)
    } else null

    private var pendingAwaitValue: JSValue? = null
    val isAwaiting: Boolean
        get() = pendingAwaitValue != null

    init {
        for ((index, arg) in arguments.take(info.ir.argCount).withIndex()) {
            locals[index] = arg
        }

        repeat(info.ir.argCount - arguments.size) {
            locals[it + arguments.size] = JSUndefined
        }
    }

    fun interpretWithYieldContinuation(value: JSValue, mode: YieldContinuation): JSValue {
        if (isDone)
            return JSUndefined

        shouldLoop = true

        when (mode) {
            YieldContinuation.Continue -> {
                // Don't push the value received from the first invocation of the generator (this
                // value is simply ignored)
                if (activeBlock.index != BlockIndex(0))
                    push(value)
            }
            YieldContinuation.Throw -> handleThrownException(ThrowException(value))
            YieldContinuation.Return -> {
                push(value)
                isDone = true
            }
        }

        return interpret()
    }

    fun interpret(): JSValue {
        while (shouldLoop && !isDone) {
            try {
                visit(activeBlock.opcodes[ip++])
            } catch (e: ThrowException) {
                handleThrownException(e)
            } catch (e: Throwable) {
                println("Exception in FunctionInfo ${info.name}, block @${activeBlock.index} opcode ${ip - 1}")
                throw e
            }
        }

        if (isAwaiting)
            return JSUndefined

        expect(stack.isNotEmpty())
        expect(stack.last() is JSValue)
        val returnValue = stack.removeLast() as JSValue

        if (isDone)
            expect(stack.isEmpty())

        return returnValue
    }

    private fun handleThrownException(e: ThrowException) {
        if (activeBlock.handlerBlock != null) {
            stack.clear()
            push(e.value)
            jumpToBlock(activeBlock.handlerBlock!!)
        } else {
            throw e
        }
    }

    override fun visitCopyObjectExcludingProperties(opcode: CopyObjectExcludingProperties) {
        val obj = popValue() as JSObject
        val excludedProperties = locals[opcode.propertiesLocal.value] as JSArrayObject
        val excludedNames = (0 until excludedProperties.indexedProperties.arrayLikeSize).map {
            excludedProperties.indexedProperties.get(excludedProperties, it.toInt()).toPropertyKey()
        }.toSet()

        val newObj = JSObject.create()

        for (name in obj.ownPropertyKeys()) {
            if (name !in excludedNames)
                newObj.set(name, obj.get(name))
        }

        push(newObj)
    }

    override fun visitLoadBoolean(opcode: LoadBoolean) {
        push(locals[opcode.local.value] as Boolean)
    }

    override fun visitStoreBoolean(opcode: StoreBoolean) {
        locals[opcode.local.value] = pop() as Boolean
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

    override fun visitDupX2() {
        expect(stack.size >= 3)

        // Inserting into a deque isn't great, but this is a very rare opcode
        stack.add(stack.size - 3, stack.last())
    }

    override fun visitSwap() {
        val temp = stack.last()
        stack[stack.lastIndex] = stack[stack.lastIndex - 1]
        stack[stack.lastIndex - 1] = temp
    }

    override fun visitLoadInt(opcode: LoadInt) {
        expect(info.ir.locals[opcode.local.value] == LocalKind.Int)
        push(locals[opcode.local.value]!!)
    }

    override fun visitStoreInt(opcode: StoreInt) {
        expect(info.ir.locals[opcode.local.value] == LocalKind.Int)
        locals[opcode.local.value] = pop()
    }

    override fun visitIncInt(opcode: IncInt) {
        expect(info.ir.locals[opcode.local.value] == LocalKind.Int)
        locals[opcode.local.value] = (locals[opcode.local.value] as Int) + 1
    }

    override fun visitLoadValue(opcode: LoadValue) {
        expect(info.ir.locals[opcode.local.value] == LocalKind.Value)
        push(locals[opcode.local.value]!!)
    }

    override fun visitStoreValue(opcode: StoreValue) {
        expect(info.ir.locals[opcode.local.value] == LocalKind.Value)
        locals[opcode.local.value] = pop()
    }

    private fun visitBinaryOperator(operator: String) {
        val rhs = popValue()
        val lhs = popValue()
        push(AOs.applyStringOrNumericBinaryOperator(lhs, rhs, operator))
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
        push(AOs.isLooselyEqual(lhs, rhs))
    }

    override fun visitTestNotEqualStrict() {
        val rhs = popValue()
        val lhs = popValue()
        push(AOs.isLooselyEqual(lhs, rhs).inv())
    }

    override fun visitTestEqual() {
        val rhs = popValue()
        val lhs = popValue()
        push(AOs.isStrictlyEqual(lhs, rhs))
    }

    override fun visitTestNotEqual() {
        val rhs = popValue()
        val lhs = popValue()
        push(AOs.isStrictlyEqual(lhs, rhs).inv())
    }

    override fun visitTestLessThan() {
        val rhs = popValue()
        val lhs = popValue()
        val result = AOs.isLessThan(lhs, rhs, true)
        push(result.ifUndefined(JSFalse))
    }

    override fun visitTestLessThanOrEqual() {
        val rhs = popValue()
        val lhs = popValue()
        val result = AOs.isLessThan(rhs, lhs, false)
        push(if (result == JSFalse) JSTrue else JSFalse)
    }

    override fun visitTestGreaterThan() {
        val rhs = popValue()
        val lhs = popValue()
        val result = AOs.isLessThan(rhs, lhs, false)
        push(result.ifUndefined(JSFalse))
    }

    override fun visitTestGreaterThanOrEqual() {
        val rhs = popValue()
        val lhs = popValue()
        val result = AOs.isLessThan(lhs, rhs, true)
        push(if (result == JSFalse) JSTrue else JSFalse)
    }

    override fun visitTestInstanceOf() {
        val ctor = popValue()
        push(AOs.instanceofOperator(popValue(), ctor))
    }

    override fun visitTestIn() {
        val rhs = popValue()
        if (rhs !is JSObject)
            Errors.InBadRHS.throwTypeError(realm)
        val lhs = popValue().toPropertyKey()
        push(AOs.hasProperty(rhs, lhs).toValue())
    }

    override fun visitTypeOf() {
        push(AOs.typeofOperator(popValue()))
    }

    override fun visitTypeOfGlobal(opcode: TypeOfGlobal) {
        if (!realm.globalEnv.hasBinding(opcode.name)) {
            push(JSString("undefined"))
        } else {
            visitLoadGlobal(LoadGlobal(opcode.name, opcode.isStrict))
            visitTypeOf()
        }
    }

    override fun visitToNumber() {
        push(popValue().toNumber())
    }

    override fun visitToNumeric() {
        push(popValue().toNumeric())
    }

    override fun visitNegate() {
        val value = popValue().let {
            if (it is JSBigInt) {
                AOs.bigintUnaryMinus(it)
            } else AOs.numericUnaryMinus(it)
        }
        push(value)
    }

    override fun visitBitwiseNot() {
        val value = popValue().let {
            if (it is JSBigInt) {
                AOs.bigintBitwiseNOT(it)
            } else AOs.numericBitwiseNOT(it)
        }
        push(value)
    }

    override fun visitToBooleanLogicalNot() {
        push((!popValue().toBoolean()).toValue())
    }

    override fun visitInc() {
        push(JSNumber(popValue().asInt + 1))
    }

    override fun visitDec() {
        push(JSNumber(popValue().asInt - 1))
    }

    override fun visitLoadKeyedProperty() {
        val key = popValue().toPropertyKey()
        val obj = popValue().toObject()
        push(obj.get(key))
    }

    override fun visitStoreKeyedProperty() {
        val value = popValue()
        val key = popValue().toPropertyKey()
        val obj = popValue().toObject()
        obj.set(key, value)
    }

    override fun visitLoadNamedProperty(opcode: LoadNamedProperty) {
        val obj = popValue().toObject()
        when (val name = opcode.name) {
            is String -> push(obj.get(name))
            is JSSymbol -> push(obj.get(name))
            else -> unreachable()
        }
    }

    override fun visitStoreNamedProperty(opcode: StoreNamedProperty) {
        val value = popValue()
        val obj = popValue().toObject()
        when (val name = opcode.name) {
            is String -> obj.set(name, value)
            is JSSymbol -> obj.set(name, value)
            else -> unreachable()
        }
    }

    override fun visitCreateObject() {
        push(JSObject.create())
    }

    override fun visitCreateArray() {
        push(JSArrayObject.create())
    }

    override fun visitStoreArray(opcode: StoreArray) {
        val value = popValue()
        val indexLocal = opcode.indexLocal.value
        val index = locals[indexLocal] as Int
        val array = locals[opcode.arrayLocal.value] as JSObject
        array.indexedProperties.set(array, index, value)
        locals[indexLocal] = locals[indexLocal] as Int + 1
    }

    override fun visitStoreArrayIndexed(opcode: StoreArrayIndexed) {
        val value = popValue()
        val array = locals[opcode.arrayLocal.value] as JSObject
        array.indexedProperties.set(array, opcode.index, value)
    }

    override fun visitDeletePropertyStrict() {
        val property = popValue()
        val target = popValue()
        if (target is JSObject) {
            val key = property.toPropertyKey()
            if (!target.delete(key))
                Errors.StrictModeFailedDelete(key, target.toJSString().string)
        }
        push(JSTrue)
    }

    override fun visitDeletePropertySloppy() {
        val property = popValue()
        val target = popValue()
        if (target is JSObject) {
            push(target.delete(property.toPropertyKey()).toValue())
        } else {
            push(JSTrue)
        }
    }

    override fun visitGetIterator() {
        push(AOs.getIterator(popValue().toObject()))
    }

    override fun visitIteratorNext() {
        push(AOs.iteratorNext(pop() as AOs.IteratorRecord))
    }

    override fun visitIteratorResultDone() {
        push(AOs.iteratorComplete(popValue()))
    }

    override fun visitIteratorResultValue() {
        push(AOs.iteratorValue(popValue()))
    }

    override fun visitPushJVMFalse() {
        push(false)
    }

    override fun visitPushJVMTrue() {
        push(true)
    }

    override fun visitPushJVMInt(opcode: PushJVMInt) {
        push(opcode.int)
    }

    override fun visitCall(opcode: Call) {
        val args = mutableListOf<JSValue>()

        repeat(opcode.argCount) {
            args.add(popValue())
        }

        val receiver = popValue()
        val target = popValue()

        push(
            AOs.call(
                target,
                receiver,
                args.asReversed(),
            )
        )
    }

    override fun visitCallArray() {
        val argsArray = popValue() as JSObject
        val receiver = popValue()
        val target = popValue()
        push(
            AOs.call(
                target,
                receiver,
                (0 until argsArray.indexedProperties.arrayLikeSize).map(argsArray::get),
            )
        )
    }

    override fun visitConstruct(opcode: Construct) {
        val args = mutableListOf<JSValue>()

        repeat(opcode.argCount) {
            args.add(popValue())
        }

        val newTarget = popValue()
        val target = popValue()

        // For some reason, the spec says this check should happen here instead
        // of in Construct
        if (!AOs.isConstructor(target))
            Errors.NotACtor(target.toString()).throwTypeError()

        push(
            AOs.construct(
                target,
                args.asReversed(),
                newTarget,
            )
        )
    }

    override fun visitConstructArray() {
        val argsArray = popValue() as JSObject
        val newTarget = popValue()
        val target = popValue()
        push(
            AOs.construct(
                target,
                (0 until argsArray.indexedProperties.arrayLikeSize).map(argsArray::get),
                newTarget,
            )
        )
    }

    override fun visitDeclareGlobalVars(opcode: DeclareGlobalVars) {
        val env = realm.globalEnv

        for ((name, _) in opcode.lexs) {
            if (env.hasVarDeclaration(name))
                Errors.InvalidGlobalVar(name).throwSyntaxError(realm)

            if (env.hasLexicalDeclaration(name))
                Errors.InvalidGlobalVar(name).throwSyntaxError(realm)

            if (env.hasRestrictedGlobalProperty(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError(realm)
        }

        for (name in opcode.vars) {
            if (env.hasLexicalDeclaration(name))
                Errors.InvalidGlobalVar(name).throwSyntaxError(realm)
        }

        for ((name, _) in opcode.lexs) {
            if (!realm.globalEnv.canDeclareGlobalVar(name))
                Errors.RestrictedGlobalPropertyName(name).throwSyntaxError(realm)
        }

        for ((name, isConstant) in opcode.lexs) {
            if (isConstant) {
                env.createImmutableBinding(name, true)
            } else {
                env.createMutableBinding(name, false)
            }
        }

        for (name in opcode.vars)
            env.createGlobalVarBinding(name, false)
    }

    override fun visitDeclareGlobalFunc(opcode: DeclareGlobalFunc) {
        val env = realm.globalEnv

        if (!env.canDeclareGlobalFunction(opcode.name))
            Errors.InvalidGlobalFunction(opcode.name).throwSyntaxError(realm)

        val func = popValue()
        env.createGlobalFunctionBinding(opcode.name, func, deletable = false)
    }

    override fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord) {
        val runningContext = agent.runningExecutionContext
        runningContext.envRecord = DeclarativeEnvRecord(realm, runningContext.envRecord)
    }

    override fun visitPushModuleEnvRecord() {
        val runningContext = agent.runningExecutionContext
        runningContext.envRecord = ModuleEnvRecord(realm, runningContext.envRecord)
    }

    override fun visitPopEnvRecord() {
        val runningContext = agent.runningExecutionContext
        runningContext.envRecord = runningContext.envRecord!!.outer
    }

    override fun visitLoadGlobal(opcode: LoadGlobal) {
        if (!realm.globalEnv.hasBinding(opcode.name))
            Errors.NotDefined(opcode.name).throwReferenceError(realm)
        push(realm.globalEnv.getBindingValue(opcode.name, opcode.isStrict))
    }

    override fun visitStoreGlobal(opcode: StoreGlobal) {
        realm.globalEnv.setMutableBinding(opcode.name, popValue(), opcode.isStrict)
    }

    override fun visitLoadCurrentEnvName(opcode: LoadCurrentEnvName) {
        push(agent.runningExecutionContext.envRecord!!.getBindingValue(opcode.name, opcode.isStrict))
    }

    override fun visitStoreCurrentEnvName(opcode: StoreCurrentEnvName) {
        agent.runningExecutionContext.envRecord!!.setMutableBinding(opcode.name, popValue(), opcode.isStrict)
    }

    override fun visitLoadEnvName(opcode: LoadEnvName) {
        var env = agent.runningExecutionContext.envRecord!!
        repeat(opcode.distance) { env = env.outer!! }
        push(env.getBindingValue(opcode.name, opcode.isStrict))
    }

    override fun visitStoreEnvName(opcode: StoreEnvName) {
        var env = agent.runningExecutionContext.envRecord!!
        repeat(opcode.distance) { env = env.outer!! }
        env.setMutableBinding(opcode.name, popValue(), opcode.isStrict)
    }

    override fun visitJump(opcode: Jump) {
        jumpToBlock(opcode.target)
    }

    override fun visitJumpIfTrue(opcode: JumpIfTrue) {
        jumpToBlock(if (pop() == true) opcode.trueTarget else opcode.falseTarget)
    }

    override fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue) {
        jumpToBlock(if (popValue().toBoolean()) opcode.trueTarget else opcode.falseTarget)
    }

    override fun visitJumpIfUndefined(opcode: JumpIfUndefined) {
        jumpToBlock(if (popValue() == JSUndefined) opcode.undefinedTarget else opcode.elseTarget)
    }

    override fun visitJumpIfNullish(opcode: JumpIfNullish) {
        jumpToBlock(if (popValue().isNullish) opcode.nullishTarget else opcode.elseTarget)
    }

    override fun visitForInEnumerate() {
        val target = popValue().toObject()
        val iterator = JSObjectPropertyIterator.create(target)
        val nextMethod = AOs.getV(iterator, "next".key())
        val iteratorRecord = AOs.IteratorRecord(iterator, nextMethod, false)
        push(iteratorRecord)
    }

    override fun visitCreateClosure(opcode: CreateClosure) {
        val function = NormalInterpretedFunction.create(transformedSource.forInfo(opcode.ir))
        AOs.setFunctionName(function, opcode.ir.name.key())
        AOs.makeConstructor(function)
        AOs.setFunctionLength(function, opcode.ir.length)
        push(function)
    }

    override fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure) {
        val function = GeneratorInterpretedFunction.create(transformedSource.forInfo(opcode.ir))
        AOs.setFunctionName(function, opcode.ir.name.key())
        AOs.setFunctionLength(function, opcode.ir.length)
        push(function)
    }

    override fun visitCreateAsyncClosure(opcode: CreateAsyncClosure) {
        val function = AsyncInterpretedFunction.create(transformedSource.forInfo(opcode.ir))
        AOs.setFunctionName(function, opcode.ir.name.key())
        AOs.setFunctionLength(function, opcode.ir.length)
        push(function)
    }

    override fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitGetSuperConstructor() {
        push(Agent.activeAgent.getActiveFunction()!!.getPrototype())
    }

    override fun visitGetSuperBase() {
        val homeObject = Agent.activeAgent.getActiveFunction()!!.homeObject
        if (homeObject == JSUndefined) {
            push(JSUndefined)
        } else {
            ecmaAssert(homeObject is JSObject)
            push(homeObject.getPrototype())
        }
    }

    override fun visitCreateUnmappedArgumentsObject() {
        push(createArgumentsObject())
    }

    override fun visitCreateMappedArgumentsObject() {
        // TODO: Create a mapped arguments object
        push(createArgumentsObject())
    }

    private fun createArgumentsObject(): JSObject {
        val arguments = this.arguments.drop(Transformer.RESERVED_LOCALS_COUNT)

        val obj = JSUnmappedArgumentsObject.create()
        AOs.definePropertyOrThrow(
            obj,
            "length".key(),
            Descriptor(arguments.size.toValue(), attrs { +conf; -enum; +writ })
        )

        for ((index, arg) in arguments.withIndex())
            AOs.createDataPropertyOrThrow(obj, index.key(), arg)

        AOs.definePropertyOrThrow(
            obj,
            Realm.WellKnownSymbols.iterator,
            Descriptor(realm.arrayProto.get("values"), attrs { +conf; -enum; +writ })
        )

        AOs.definePropertyOrThrow(
            obj,
            "callee".key(),
            Descriptor(JSAccessor(realm.throwTypeError, realm.throwTypeError), 0),
        )

        return obj
    }

    override fun visitThrowConstantReassignmentError(opcode: ThrowConstantReassignmentError) {
        Errors.AssignmentToConstant(opcode.name).throwTypeError()
    }

    override fun visitThrowLexicalAccessError(opcode: ThrowLexicalAccessErrorIfEmpty) {
        if (popValue() == JSEmpty)
            Errors.AccessBeforeInitialization(opcode.name).throwReferenceError(realm)
    }

    override fun visitThrowSuperNotInitializedIfEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitThrow() {
        throw ThrowException(popValue())
    }

    override fun visitToString() {
        push(popValue().toJSString())
    }

    override fun visitCreateRegExpObject(opcode: CreateRegExpObject) {
        push(JSRegExpObject.create(opcode.source, opcode.flags, opcode.regexp))
    }

    override fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral) {
        val args = (0 until opcode.numberOfParts).map { popValue() }.asReversed()
        val string = buildString {
            for (arg in args) {
                expect(arg is JSString)
                append(arg.string)
            }
        }
        push(JSString(string))
    }

    override fun visitPushClosure() {
        push(Agent.activeAgent.getActiveFunction()!!)
    }

    override fun visitReturn() {
        shouldLoop = false
        isDone = true
    }

    override fun visitYield(opcode: Yield) {
        shouldLoop = false
        jumpToBlock(opcode.target)
    }

    override fun visitAwait(opcode: Await) {
        shouldLoop = false

        pendingAwaitValue = popValue()
        queueAwaitMicrotask()
        jumpToBlock(opcode.target)
    }

    private fun queueAwaitMicrotask() {
        val innerPromise = AOs.promiseResolve(realm.promiseCtor, pendingAwaitValue!!)

        AOs.performPromiseThen(
            innerPromise as JSObject,
            JSBuiltinFunction.create {
                interpretWithYieldContinuation(it.argument(0), YieldContinuation.Continue)
            },
            JSBuiltinFunction.create {
                interpretWithYieldContinuation(it.argument(0), YieldContinuation.Throw)
            },
        )
    }

    override fun visitCollectRestArgs() {
        push(AOs.createArrayFromList(arguments.drop(info.ir.argCount - 1)))
    }

    override fun visitDefineGetterProperty() {
        val method = popValue() as JSFunction
        val key = popValue()
        val obj = popValue() as JSObject
        defineAccessor(obj, key, method, isGetter = true)
    }

    override fun visitDefineSetterProperty() {
        val method = popValue() as JSFunction
        val key = popValue()
        val obj = popValue() as JSObject
        defineAccessor(obj, key, method, isGetter = false)
    }

    private fun defineAccessor(obj: JSObject, property: JSValue, method: JSFunction, isGetter: Boolean) {
        val key = property.toPropertyKey()
        AOs.setFunctionName(method, key, if (isGetter) "get" else "set")
        val accessor = if (isGetter) JSAccessor(method, null) else JSAccessor(null, method)
        val descriptor = Descriptor(accessor, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE)
        AOs.definePropertyOrThrow(obj, key, descriptor)
    }

    override fun visitPushBigInt(opcode: PushBigInt) {
        push(JSBigInt(opcode.bigint))
    }

    override fun visitPushEmpty() {
        push(JSEmpty)
    }

    override fun visitCreateClassConstructor(opcode: CreateMethod) {
        push(NormalInterpretedFunction.create(transformedSource.forInfo(opcode.ir)))
    }

    override fun visitCreateClass() {
        val superClass = popValue()
        val constructor = popValue()

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
            !AOs.isConstructor(superClass) ->
                Errors.NotACtor(superClass.toJSString().string).throwTypeError()
            else -> {
                protoParent = superClass.get("prototype")
                if (protoParent != JSNull && protoParent !is JSObject)
                    Errors.TODO("superClass.prototype invalid type").throwTypeError()
                constructorParent = superClass
            }
        }

        val proto = JSObject.create(proto = protoParent)
        AOs.makeClassConstructor(constructor)

        // TODO// Operations.setFunctionName(constructor, className)

        AOs.makeConstructor(constructor, false, proto)

        if (superClass != JSEmpty)
            constructor.constructorKind = JSFunction.ConstructorKind.Derived

        constructor.setPrototype(constructorParent)
        AOs.makeMethod(constructor, proto)
        AOs.createMethodProperty(proto, "constructor".key(), constructor)

        push(ClassCtorAndProto(constructor, proto))
    }

    data class ClassCtorAndProto(val constructor: JSFunction, val proto: JSObject)

    override fun visitAttachClassMethod(opcode: AttachClassMethod) {
        val (constructor, proto) = pop() as ClassCtorAndProto
        methodDefinitionEvaluation(
            opcode.name.key(),
            opcode.kind,
            if (opcode.isStatic) constructor else proto,
            enumerable = false,
            opcode.ir,
        )
    }

    override fun visitAttachComputedClassMethod(opcode: AttachComputedClassMethod) {
        val name = popValue().toPropertyKey()
        val (constructor, proto) = pop() as ClassCtorAndProto
        methodDefinitionEvaluation(
            name,
            opcode.kind,
            if (opcode.isStatic) constructor else proto,
            enumerable = false,
            opcode.ir,
        )
    }

    private fun methodDefinitionEvaluation(
        name: PropertyKey,
        kind: MethodDefinitionNode.Kind,
        obj: JSObject,
        enumerable: Boolean,
        info: FunctionInfo,
    ) {
        val closure = when (kind) {
            MethodDefinitionNode.Kind.Normal,
            MethodDefinitionNode.Kind.Getter,
            MethodDefinitionNode.Kind.Setter ->
                NormalInterpretedFunction.create(transformedSource.forInfo(info))
            MethodDefinitionNode.Kind.Generator ->
                GeneratorInterpretedFunction.create(transformedSource.forInfo(info))
            MethodDefinitionNode.Kind.Async ->
                AsyncInterpretedFunction.create(transformedSource.forInfo(info))
            else -> TODO()
        }

        AOs.makeMethod(closure, obj)

        if (kind == MethodDefinitionNode.Kind.Getter || kind == MethodDefinitionNode.Kind.Setter) {
            val (prefix, desc) = if (kind == MethodDefinitionNode.Kind.Getter) {
                "get" to Descriptor(JSAccessor(closure, null), Descriptor.CONFIGURABLE)
            } else {
                "set" to Descriptor(JSAccessor(null, closure), Descriptor.CONFIGURABLE)
            }

            AOs.setFunctionName(closure, name, prefix)
            AOs.definePropertyOrThrow(obj, name, desc)
            return
        }

        when (kind) {
            MethodDefinitionNode.Kind.Normal,
            MethodDefinitionNode.Kind.Async,
            MethodDefinitionNode.Kind.Getter,
            MethodDefinitionNode.Kind.Setter -> {
            }
            MethodDefinitionNode.Kind.Generator -> {
                val prototype = JSObject.create(proto = realm.generatorObjectProto)
                AOs.definePropertyOrThrow(
                    closure,
                    "prototype".key(),
                    Descriptor(prototype, Descriptor.WRITABLE),
                )
            }
            else -> TODO()
        }

        AOs.defineMethodProperty(name, obj, closure, enumerable)
    }

    override fun visitFinalizeClass() {
        push((pop() as ClassCtorAndProto).constructor)
    }

    override fun visitLoadModuleVar(opcode: LoadModuleVar) {
        val value = moduleEnv!!.getBindingValue(opcode.name, isStrict = true)
        if (value == JSEmpty)
            Errors.CircularImport(opcode.name).throwReferenceError(realm)
        push(value)
    }

    override fun visitStoreModuleVar(opcode: StoreModuleVar) {
        moduleEnv!!.setMutableBinding(opcode.name, popValue(), isStrict = true)
    }

    private fun jumpToBlock(block: BlockIndex) {
        ip = 0
        activeBlock = info.ir.blocks[block]!!
    }

    private fun pop(): Any = stack.removeLast()

    private fun popInt(): Int = pop() as Int

    private fun popValue(): JSValue = pop() as JSValue

    private fun push(value: Any) {
        stack.add(value)
    }

    enum class YieldContinuation {
        Continue,
        Throw,
        Return,
    }
}
