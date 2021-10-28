package com.reevajs.reeva.interpreter

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.core.ModuleRecord
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.environment.DeclarativeEnvRecord
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.interpreter.transformer.*
import com.reevajs.reeva.interpreter.transformer.opcodes.*
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObject
import com.reevajs.reeva.runtime.iterators.JSObjectPropertyIterator
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObject.Companion.initialize
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.runtime.regexp.JSRegExpObject
import com.reevajs.reeva.utils.*

class Interpreter(
    private val transformedSource: TransformedSource,
    private val arguments: List<JSValue>,
    initialEnvRecord: EnvRecord,
) : OpcodeVisitor {
    private val info: FunctionInfo
        get() = transformedSource.functionInfo

    private val realm: Realm
        get() = transformedSource.realm

    private val stack = ArrayDeque<Any>()
    private val locals = Array<Any?>(info.ir.locals.size) { null }

    var activeEnvRecord = initialEnvRecord
        private set
    private var ip = 0
    private var isDone = false

    // For fast handler lookup in main loop
    private val handlerStarts = mutableMapOf<Int, Handler>()
    private val handlerEnds = mutableMapOf<Int, Handler>()
    private val savedStackHeights = ArrayDeque<Int>()

    init {
        for (handler in info.ir.handlers) {
            handlerStarts[handler.start] = handler
            handlerEnds[handler.end] = handler
        }
    }

    fun interpret(): Result<ThrowException, JSValue> {
        for ((index, arg) in arguments.take(info.ir.argCount).withIndex()) {
            locals[index] = arg
        }

        repeat(info.ir.argCount - arguments.size) {
            locals[it + arguments.size] = JSUndefined
        }

        while (!isDone) {
            try {
                val startHandler = handlerStarts[ip]
                if (startHandler != null) {
                    savedStackHeights.add(stack.size)
                } else {
                    val endHandler = handlerEnds[ip]
                    if (endHandler != null)
                        savedStackHeights.removeLast()
                }

                visit(info.ir.opcodes[ip++])
            } catch (e: ThrowException) {
                // TODO: Can we optimize this lookup?
                var handled = false

                for (handler in info.ir.handlers) {
                    if (ip - 1 in handler.start..handler.end) {
                        val stackDiff = stack.size - savedStackHeights.removeLast()
                        expect(stackDiff >= 0)
                        repeat(stackDiff) {
                            stack.removeLast()
                        }

                        ip = handler.handler
                        push(e.value)
                        handled = true
                        break
                    }
                }

                if (!handled)
                    return Result.error(e)
            } catch (e: Throwable) {
                println("Exception in FunctionInfo ${info.name}, opcode ${ip - 1}")
                throw e
            }
        }

        expect(stack.size == 1)
        expect(stack[0] is JSValue)
        return Result.success(stack[0] as JSValue)
    }

    override fun visitCopyObjectExcludingProperties(opcode: CopyObjectExcludingProperties) {
        val obj = popValue() as JSObject
        val excludedProperties = locals[opcode.propertiesLocal.value] as JSArrayObject
        val excludedNames = (0 until excludedProperties.indexedProperties.arrayLikeSize).map {
            excludedProperties.indexedProperties.get(excludedProperties, it.toInt()).toPropertyKey(realm)
        }.toSet()

        val newObj = JSObject.create(realm)

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

    override fun visitTypeOfGlobal(opcode: TypeOfGlobal) {
        if (!realm.globalEnv.hasBinding(opcode.name)) {
            push(JSString("undefined"))
        } else {
            visitLoadGlobal(LoadGlobal(opcode.name))
            visitTypeOf()
        }
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

    override fun visitStoreArrayIndexed(opcode: StoreArrayIndexed) {
        val value = popValue()
        val array = popValue() as JSObject
        array.indexedProperties.set(array, opcode.index, value)
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
            Operations.call(
                realm,
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
            Operations.call(
                realm,
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

        push(
            Operations.construct(
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
            Operations.construct(
                target,
                (0 until argsArray.indexedProperties.arrayLikeSize).map(argsArray::get),
                newTarget,
            )
        )
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
        activeEnvRecord = DeclarativeEnvRecord(activeEnvRecord, opcode.slotCount)
    }

    override fun visitPushModuleEnvRecord() {
        activeEnvRecord = ModuleEnvRecord(activeEnvRecord)
    }

    override fun visitPopEnvRecord() {
        activeEnvRecord = activeEnvRecord.outer!!
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
        push(activeEnvRecord.getBinding(opcode.slot))
    }

    override fun visitStoreCurrentEnvSlot(opcode: StoreCurrentEnvSlot) {
        activeEnvRecord.setBinding(opcode.slot, popValue())
    }

    override fun visitLoadEnvSlot(opcode: LoadEnvSlot) {
        var env = activeEnvRecord
        repeat(opcode.distance) { env = env.outer!! }
        push(env.getBinding(opcode.slot))
    }

    override fun visitStoreEnvSlot(opcode: StoreEnvSlot) {
        var env = activeEnvRecord
        repeat(opcode.distance) { env = env.outer!! }
        env.setBinding(opcode.slot, popValue())
    }

    override fun visitJump(opcode: Jump) {
        ip = opcode.to
    }

    override fun visitJumpIfTrue(opcode: JumpIfTrue) {
        if (pop() == true)
            ip = opcode.to
    }

    override fun visitJumpIfFalse(opcode: JumpIfFalse) {
        if (pop() == false)
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
        val function = NormalIRFunction(transformedSource.forInfo(opcode.ir), activeEnvRecord).initialize()
        Operations.setFunctionName(realm, function, opcode.ir.name.key())
        Operations.makeConstructor(realm, function)
        push(function)
    }

    override fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure) {
        val function = GeneratorIRFunction(transformedSource.forInfo(opcode.ir), activeEnvRecord).initialize()
        Operations.setFunctionName(realm, function, opcode.ir.name.key())
        push(function)
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
        push(Reeva.activeAgent.activeFunction.getPrototype())
    }

    override fun visitGetSuperBase() {
        val homeObject = Reeva.activeAgent.activeFunction.homeObject
        if (homeObject == JSUndefined) {
            push(JSUndefined)
        } else {
            ecmaAssert(homeObject is JSObject)
            push(homeObject.getPrototype())
        }
    }

    override fun visitCreateUnmappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitCreateMappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitThrowConstantReassignmentError(opcode: ThrowConstantReassignmentError) {
        Errors.AssignmentToConstant(opcode.name).throwTypeError(realm)
    }

    override fun visitThrowLexicalAccessError(opcode: ThrowLexicalAccessError) {
        Errors.AccessBeforeInitialization(opcode.name).throwReferenceError(realm)
    }

    override fun visitThrowSuperNotInitializedIfEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitThrow() {
        throw ThrowException(popValue())
    }

    override fun visitToString() {
        push(Operations.toString(realm, popValue()))
    }

    override fun visitCreateRegExpObject(opcode: CreateRegExpObject) {
        push(JSRegExpObject.create(realm, opcode.source, opcode.flags))
    }

    override fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral) {
        val args = (0..opcode.numberOfParts).map { popValue() }.asReversed()
        val string = buildString {
            for (arg in args) {
                expect(arg is JSString)
                append(arg.string)
            }
        }
        push(JSString(string))
    }

    override fun visitPushClosure() {
        push(Reeva.activeAgent.activeFunction)
    }

    override fun visitReturn() {
        isDone = true
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
        val key = property.toPropertyKey(realm)
        Operations.setFunctionName(realm, method, key, if (isGetter) "get" else "set")
        val accessor = if (isGetter) JSAccessor(method, null) else JSAccessor(null, method)
        val descriptor = Descriptor(accessor, Descriptor.CONFIGURABLE or Descriptor.ENUMERABLE)
        Operations.definePropertyOrThrow(realm, obj, key, descriptor)
    }

    override fun visitGetGeneratorPhase() {
        val state = locals[Transformer.GENERATOR_STATE_LOCAL.value] as GeneratorState
        push(state.phase)
    }

    override fun visitPushToGeneratorState() {
        val state = locals[Transformer.GENERATOR_STATE_LOCAL.value] as GeneratorState
        state.push(pop())
    }

    override fun visitPopFromGeneratorState() {
        val state = locals[Transformer.GENERATOR_STATE_LOCAL.value] as GeneratorState
        push(state.pop())
    }

    override fun visitJumpTable(opcode: JumpTable) {
        val target = popInt()
        val result = opcode.table[target]
        expect(result != null)
        ip = result
    }

    override fun visitPushBigInt(opcode: PushBigInt) {
        push(JSBigInt(opcode.bigint))
    }

    override fun visitPushEmpty() {
        push(JSEmpty)
    }

    override fun visitSetGeneratorPhase(opcode: SetGeneratorPhase) {
        val state = locals[Transformer.GENERATOR_STATE_LOCAL.value] as GeneratorState
        state.phase = opcode.phase
    }

    override fun visitGeneratorSentValue() {
        val state = locals[Transformer.GENERATOR_STATE_LOCAL.value] as GeneratorState
        push(state.sentValue)
    }

    override fun visitCreateClassConstructor(opcode: CreateMethod) {
        push(NormalIRFunction(transformedSource.forInfo(opcode.ir), activeEnvRecord).initialize())
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

        // TODO// Operations.setFunctionName(constructor, className)

        Operations.makeConstructor(realm, constructor, false, proto)

        if (superClass != JSEmpty)
            constructor.constructorKind = JSFunction.ConstructorKind.Derived

        constructor.setPrototype(constructorParent)
        Operations.makeMethod(constructor, proto)
        Operations.createMethodProperty(proto, "constructor".key(), constructor)

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
        val name = popValue().toPropertyKey(realm)
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
                NormalIRFunction(transformedSource.forInfo(info), activeEnvRecord).initialize()
            MethodDefinitionNode.Kind.Generator ->
                GeneratorIRFunction(transformedSource.forInfo(info), activeEnvRecord).initialize()
            else -> TODO()
        }

        Operations.makeMethod(closure, obj)

        if (kind == MethodDefinitionNode.Kind.Getter || kind == MethodDefinitionNode.Kind.Setter) {
            val (prefix, desc) = if (kind == MethodDefinitionNode.Kind.Getter) {
                "get" to Descriptor(JSAccessor(closure, null), Descriptor.CONFIGURABLE)
            } else {
                "set" to Descriptor(JSAccessor(null, closure), Descriptor.CONFIGURABLE)
            }

            Operations.setFunctionName(realm, closure, name, prefix)
            Operations.definePropertyOrThrow(realm, obj, name, desc)
            return
        }

        when (kind) {
            MethodDefinitionNode.Kind.Normal,
            MethodDefinitionNode.Kind.Getter,
            MethodDefinitionNode.Kind.Setter -> {}
            MethodDefinitionNode.Kind.Generator -> {
                val prototype = JSObject.create(realm, realm.generatorObjectProto)
                Operations.definePropertyOrThrow(
                    realm,
                    closure,
                    "prototype".key(),
                    Descriptor(prototype, Descriptor.WRITABLE),
                )
            }
            else -> TODO()
        }

        Operations.defineMethodProperty(realm, name, obj, closure, enumerable)
    }

    override fun visitFinalizeClass() {
        push((pop() as ClassCtorAndProto).constructor)
    }

    override fun visitDeclareNamedImports(opcode: DeclareNamedImports) {
        val moduleRecord = pop() as ModuleRecord
        for (name in opcode.namedImports) {
            if (moduleRecord.getNamedExport(name) == null)
                Errors.NonExistentImport(moduleRecord.specifier, name).throwSyntaxError(realm)
        }
    }

    override fun visitStoreModuleRecord() {
        (activeEnvRecord as ModuleEnvRecord).storeModuleRecord(pop() as ModuleRecord)
    }

    private fun pop(): Any = stack.removeLast()

    private fun popInt(): Int = pop() as Int

    private fun popValue(): JSValue = pop() as JSValue

    private fun push(value: Any) {
        stack.add(value)
    }

    abstract class IRFunction(
        val transformedSource: TransformedSource,
        val outerEnvRecord: EnvRecord,
        prototype: JSValue = transformedSource.realm.functionProto,
    ) : JSFunction(
        transformedSource.realm,
        transformedSource.functionInfo.name,
        transformedSource.functionInfo.isStrict,
        prototype,
    )

    class NormalIRFunction(
        transformedSource: TransformedSource,
        outerEnvRecord: EnvRecord,
    ) : IRFunction(transformedSource, outerEnvRecord) {
        override fun evaluate(arguments: JSArguments): JSValue {
            val args = listOf(arguments.thisValue, arguments.newTarget) + arguments
            val result = Interpreter(transformedSource, args, outerEnvRecord).interpret()
            return result.valueOrElse { throw result.error() }
        }
    }

    class GeneratorIRFunction(
        transformedSource: TransformedSource,
        outerEnvRecord: EnvRecord,
    ) : IRFunction(transformedSource, outerEnvRecord) {
        private lateinit var generatorObject: JSGeneratorObject

        override fun init() {
            super.init()
            defineOwnProperty("prototype", realm.functionProto)
        }

        override fun evaluate(arguments: JSArguments): JSValue {
            if (!::generatorObject.isInitialized) {
                generatorObject = JSGeneratorObject.create(
                    transformedSource,
                    arguments.thisValue,
                    arguments,
                    GeneratorState(),
                    outerEnvRecord,
                )
            }

            return generatorObject
        }
    }

    class ModuleIRFunction(
        transformedSource: TransformedSource,
        outerEnvRecord: EnvRecord,
    ) : IRFunction(transformedSource, outerEnvRecord) {
        private val generatorFunction = GeneratorIRFunction(transformedSource, outerEnvRecord).initialize()

        override fun evaluate(arguments: JSArguments): JSValue {
            // TODO: Avoid the JS runtime here

            val realm = transformedSource.realm
            val generatorObj = generatorFunction.evaluate(arguments)
            var result = Operations.invoke(realm, generatorObj, "next".key())

            while (!Operations.getV(realm, result, "done".key()).asBoolean) {
                val moduleToImport = Operations.getV(realm, result, "value".key()).asString
                result = Operations.invoke(realm, generatorObj, "next".key(), listOf(TODO()))
            }

            return JSEmpty
        }
    }

    // Extends from JSValue so it can be passed as an argument
    data class GeneratorState(
        var phase: Int = 0,
        var yieldedValue: JSValue = JSEmpty,
        var sentValue: JSValue = JSEmpty,
        var shouldThrow: Boolean = false,
        var shouldReturn: Boolean = false,
    ) : JSValue() {
        // Used to preserve the stack in between yields
        private val stack = ArrayDeque<Any>()

        fun push(value: Any) {
            stack.addLast(value)
        }

        fun pop() = stack.removeLast()
    }

    companion object {
        fun wrap(
            transformedSource: TransformedSource
        ) = if (transformedSource.sourceInfo.type.isModule) {
            ModuleIRFunction(transformedSource, transformedSource.realm.globalEnv)
        } else {
            NormalIRFunction(transformedSource, transformedSource.realm.globalEnv)
        }.initialize()
    }
}
