package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.toValue

class JSPromiseCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Promise", 2) {
    override fun init() {
        super.init()

        defineBuiltin("all", 1, ::all)
        defineBuiltin("allSettled", 1, ::allSettled)
        defineBuiltin("resolve", 1, ::resolve)
        defineBuiltin("reject", 1, ::reject)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Promise").throwTypeError(realm)

        val executor = arguments.argument(0)
        if (!Operations.isCallable(executor))
            Errors.Promise.CtorFirstArgCallable.throwTypeError(realm)

        val promise = Operations.ordinaryCreateFromConstructor(
            arguments.newTarget,
            listOf(
                SlotName.PromiseState,
                SlotName.PromiseResult,
                SlotName.PromiseFulfillReactions,
                SlotName.PromiseRejectReactions,
                SlotName.PromiseIsHandled,
            ),
        ) {
            it.promiseProto
        }

        promise.setSlot(SlotName.PromiseState, Operations.PromiseState.Pending)
        promise.setSlot(SlotName.PromiseFulfillReactions, mutableListOf<Operations.PromiseReaction>())
        promise.setSlot(SlotName.PromiseRejectReactions, mutableListOf<Operations.PromiseReaction>())
        promise.setSlot(SlotName.PromiseIsHandled, false)
        promise.setSlot(SlotName.PromiseResult, JSUndefined)

        val (resolveFunction, rejectFunction) = Operations.createResolvingFunctions(promise)

        try {
            Operations.call(executor, JSUndefined, listOf(resolveFunction, rejectFunction))
        } catch (e: ThrowException) {
            Operations.call(rejectFunction, JSUndefined, listOf(e.value))
        }
        return promise
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPromiseCtor(realm).initialize()

        @ECMAImpl("26.6.4.1", "26.6.4.1.2")
        @JvmStatic
        fun all(arguments: JSArguments): JSValue {
            val capability = Operations.newPromiseCapability(arguments.thisValue)
            val resolve = ifAbruptRejectPromise(capability, { return it }) {
                Operations.getPromiseResolve(arguments.thisValue)
            }
            val iteratorRecord = ifAbruptRejectPromise(capability, { return it }) {
                Operations.getIterator(arguments.argument(0))
            }

            return try {
                performPromiseAll(iteratorRecord, arguments.thisValue, capability, resolve)
            } catch (e: ThrowException) {
                try {
                    if (!iteratorRecord.isDone)
                        Operations.iteratorClose(iteratorRecord, e.value)
                    else JSEmpty
                } finally {
                    Operations.call(capability.reject!!, JSUndefined, listOf(e.value))
                    capability.promise
                }
            }
        }

        private fun performPromiseAllSettled(
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
                        val valuesArray = Operations.createArrayFromList(values)
                        Operations.call(resultCapability.resolve!!, JSUndefined, listOf(valuesArray))
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
                val nextPromise = Operations.call(promiseResolve, constructor, listOf(nextValue))
                val resolveElement = JSPromiseAllSettledResolver.create(
                    index,
                    values,
                    resultCapability,
                    remainingElementCount,
                    false
                )
                val rejectElement = JSPromiseAllSettledResolver.create(
                    index,
                    values,
                    resultCapability,
                    remainingElementCount,
                    true
                )
                remainingElementCount.value++
                Operations.invoke(nextPromise, "then".toValue(), listOf(resolveElement, rejectElement))
                index++
            }
        }

        @ECMAImpl("27.2.4.2")
        @JvmStatic
        fun allSettled(arguments: JSArguments): JSValue {
            val capability = Operations.newPromiseCapability(arguments.thisValue)
            val resolve = ifAbruptRejectPromise(capability, { return it }) {
                Operations.getPromiseResolve(arguments.thisValue)
            }
            val iteratorRecord = ifAbruptRejectPromise(capability, { return it }) {
                Operations.getIterator(arguments.argument(0))
            }

            return try {
                performPromiseAllSettled(iteratorRecord, arguments.thisValue, capability, resolve)
            } catch (e: ThrowException) {
                try {
                    if (!iteratorRecord.isDone)
                        Operations.iteratorClose(iteratorRecord, e.value)
                    else JSEmpty
                } finally {
                    Operations.call(capability.reject!!, JSUndefined, listOf(e.value))
                    return capability.promise
                }
            }
        }

        private fun performPromiseAll(
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
                        val valuesArray = Operations.createArrayFromList(values)
                        Operations.call(resultCapability.resolve!!, JSUndefined, listOf(valuesArray))
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
                val nextPromise = Operations.call(promiseResolve, constructor, listOf(nextValue))
                val resolveElement =
                    JSPromiseAllResolver.create(index, values, resultCapability, remainingElementCount)
                remainingElementCount.value++
                Operations.invoke(
                    nextPromise,
                    "then".toValue(),
                    listOf(resolveElement, resultCapability.reject!!)
                )
                index++
            }
        }

        @ECMAImpl("27.2.4.6")
        @JvmStatic
        fun reject(arguments: JSArguments): JSValue {
            val capability = Operations.newPromiseCapability(arguments.thisValue)
            Operations.call(capability.reject!!, JSUndefined, listOf(arguments.argument(0)))
            return capability.promise
        }

        @ECMAImpl("27.2.4.7")
        @JvmStatic
        fun resolve(arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Promise.resolve").throwTypeError()
            return Operations.promiseResolve(arguments.thisValue, arguments.argument(0))
        }

        private inline fun <T> ifAbruptRejectPromise(
            capability: Operations.PromiseCapability,
            returnBlock: (JSValue) -> Nothing,
            producer: () -> T
        ): T {
            return try {
                producer()
            } catch (e: ThrowException) {
                Operations.call(capability.reject!!, JSUndefined, listOf(e.value))
                returnBlock(capability.promise)
            }
        }
    }
}
