package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.toValue

class JSPromiseCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Promise", 2) {
    override fun init() {
        super.init()

        defineNativeFunction("all", 1, ::all)
        defineNativeFunction("allSettled", 1, ::allSettled)
        defineNativeFunction("resolve", 1, ::resolve)
        defineNativeFunction("reject", 1, ::reject)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Promise").throwTypeError(realm)

        val executor = arguments.argument(0)
        if (!Operations.isCallable(executor))
            Errors.Promise.CtorFirstArgCallable.throwTypeError(realm)

        val promise = Operations.ordinaryCreateFromConstructor(
            realm,
            arguments.newTarget,
            realm.promiseProto,
            listOf(
                SlotName.PromiseState,
                SlotName.PromiseResult,
                SlotName.PromiseFulfillReactions,
                SlotName.PromiseRejectReactions,
                SlotName.PromiseIsHandled,
            )
        )

        promise.setSlot(SlotName.PromiseState, Operations.PromiseState.Pending)
        promise.setSlot(SlotName.PromiseFulfillReactions, mutableListOf<Operations.PromiseReaction>())
        promise.setSlot(SlotName.PromiseRejectReactions, mutableListOf<Operations.PromiseReaction>())
        promise.setSlot(SlotName.PromiseIsHandled, false)
        promise.setSlot(SlotName.PromiseResult, JSUndefined)

        val (resolveFunction, rejectFunction) = Operations.createResolvingFunctions(promise)
        try {
            Operations.call(realm, executor, JSUndefined, listOf(resolveFunction, rejectFunction))
        } catch (e: ThrowException) {
            Operations.call(realm, rejectFunction, JSUndefined, listOf(e.value))
        }
        return promise
    }

    @ECMAImpl("26.6.4.1")
    @ECMAImpl("26.6.4.1.2", name = "PerformPromiseAll")
    fun all(realm: Realm, arguments: JSArguments): JSValue {
        val capability = Operations.newPromiseCapability(realm, arguments.thisValue)
        val resolve = ifAbruptRejectPromise(realm, capability, { return it }) {
            Operations.getPromiseResolve(arguments.thisValue)
        }
        val iteratorRecord = ifAbruptRejectPromise(realm, capability, { return it }) {
            Operations.getIterator(realm, arguments.argument(0))
        }

        return try {
            performPromiseAll(realm, iteratorRecord, arguments.thisValue, capability, resolve)
        } catch (e: ThrowException) {
            try {
                if (!iteratorRecord.isDone)
                    Operations.iteratorClose(iteratorRecord, e.value)
                else JSEmpty
            } finally {
                Operations.call(realm, capability.reject!!, JSUndefined, listOf(e.value))
                return capability.promise
            }
        }
    }

    fun performPromiseAllSettled(
        iteratorRecord: Operations.IteratorRecord,
        constructor: JSValue,
        resultCapability: Operations.PromiseCapability,
        promiseResolve: JSValue
    ): JSValue {
        ecmaAssert(Operations.isConstructor(constructor))
        ecmaAssert(Operations.isCallable(promiseResolve))

        val realm = iteratorRecord.iterator.realm
        val values = mutableListOf<JSValue>()
        val remainingElementCount = Operations.Wrapper(1)
        var index = 0

        while (true) {
            val next = try {
                Operations.iteratorStep(iteratorRecord)
            } catch (e: ThrowException) {
                iteratorRecord.isDone = true
                throw e
            }

            if (next == JSFalse) {
                iteratorRecord.isDone = true
                remainingElementCount.value--
                if (remainingElementCount.value == 0) {
                    val valuesArray = Operations.createArrayFromList(realm, values)
                    Operations.call(realm, resultCapability.resolve!!, JSUndefined, listOf(valuesArray))
                }
                return resultCapability.promise
            }

            val nextValue = try {
                Operations.iteratorValue(next)
            } catch (e: ThrowException) {
                iteratorRecord.isDone = true
                throw e
            }

            values.add(JSUndefined)
            val nextPromise = Operations.call(realm, promiseResolve, constructor, listOf(nextValue))
            val resolveElement = JSPromiseAllSettledResolver.create(realm, index, values, resultCapability, remainingElementCount, false)
            val rejectElement = JSPromiseAllSettledResolver.create(realm, index, values, resultCapability, remainingElementCount, true)
            remainingElementCount.value++
            Operations.invoke(realm, nextPromise, "then".toValue(), listOf(resolveElement, rejectElement))
            index++
        }
    }

    fun allSettled(realm: Realm, arguments: JSArguments): JSValue {
        val capability = Operations.newPromiseCapability(realm, arguments.thisValue)
        val resolve = ifAbruptRejectPromise(realm, capability, { return it }) {
            Operations.getPromiseResolve(arguments.thisValue)
        }
        val iteratorRecord = ifAbruptRejectPromise(realm, capability, { return it }) {
            Operations.getIterator(realm, arguments.argument(0))
        }

        return try {
            performPromiseAllSettled(iteratorRecord, arguments.thisValue, capability, resolve)
        } catch (e: ThrowException) {
            try {
                if (!iteratorRecord.isDone)
                    Operations.iteratorClose(iteratorRecord, e.value)
                else JSEmpty
            } finally {
                Operations.call(realm, capability.reject!!, JSUndefined, listOf(e.value))
                return capability.promise
            }
        }
    }

    fun performPromiseAll(
        realm: Realm,
        iteratorRecord: Operations.IteratorRecord,
        constructor: JSValue,
        resultCapability: Operations.PromiseCapability,
        promiseResolve: JSValue
    ): JSValue {
        ecmaAssert(Operations.isConstructor(constructor))
        ecmaAssert(Operations.isCallable(promiseResolve))

        val values = mutableListOf<JSValue>()
        val remainingElementCount = Operations.Wrapper(1)
        var index = 0

        while (true) {
            val next = try {
                Operations.iteratorStep(iteratorRecord)
            } catch (e: ThrowException) {
                iteratorRecord.isDone = true
                throw e
            }

            if (next == JSFalse) {
                iteratorRecord.isDone = true
                remainingElementCount.value--
                if (remainingElementCount.value == 0) {
                    val valuesArray = Operations.createArrayFromList(realm, values)
                    Operations.call(realm, resultCapability.resolve!!, JSUndefined, listOf(valuesArray))
                }
                return resultCapability.promise
            }

            val nextValue = try {
                Operations.iteratorValue(next)
            } catch (e: ThrowException) {
                iteratorRecord.isDone = true
                throw e
            }

            values.add(JSUndefined)
            val nextPromise = Operations.call(realm, promiseResolve, constructor, listOf(nextValue))
            val resolveElement =
                JSPromiseAllResolver.create(realm, index, values, resultCapability, remainingElementCount)
            remainingElementCount.value++
            Operations.invoke(realm, nextPromise, "then".toValue(), listOf(resolveElement, resultCapability.reject!!))
            index++
        }
    }

    fun reject(realm: Realm, arguments: JSArguments): JSValue {
        val capability = Operations.newPromiseCapability(realm, arguments.thisValue)
        Operations.call(realm, capability.reject!!, JSUndefined, listOf(arguments.argument(0)))
        return capability.promise
    }

    fun resolve(realm: Realm, arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Promise.resolve").throwTypeError(realm)
        return Operations.promiseResolve(arguments.thisValue, arguments.argument(0))
    }

    private inline fun <T> ifAbruptRejectPromise(
        realm: Realm,
        capability: Operations.PromiseCapability,
        returnBlock: (JSValue) -> Nothing,
        producer: () -> T
    ): T {
        return try {
            producer()
        } catch (e: ThrowException) {
            Operations.call(realm, capability.reject!!, JSUndefined, listOf(e.value))
            returnBlock(capability.promise)
        }
    }

    companion object {
        fun create(realm: Realm) = JSPromiseCtor(realm).initialize()
    }
}
