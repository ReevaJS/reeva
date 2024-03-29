package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
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
        if (!AOs.isCallable(executor))
            Errors.Promise.CtorFirstArgCallable.throwTypeError(realm)

        val promise = AOs.ordinaryCreateFromConstructor(
            arguments.newTarget,
            listOf(
                Slot.PromiseState,
                Slot.PromiseResult,
                Slot.PromiseFulfillReactions,
                Slot.PromiseRejectReactions,
                Slot.PromiseIsHandled,
            ),
        ) {
            it.promiseProto
        }

        promise[Slot.PromiseState] = AOs.PromiseState.Pending
        promise[Slot.PromiseFulfillReactions] = mutableListOf<AOs.PromiseReaction>()
        promise[Slot.PromiseRejectReactions] = mutableListOf<AOs.PromiseReaction>()
        promise[Slot.PromiseIsHandled] = false
        promise[Slot.PromiseResult] = JSUndefined

        val (resolveFunction, rejectFunction) = AOs.createResolvingFunctions(promise)

        try {
            AOs.call(executor, JSUndefined, listOf(resolveFunction, rejectFunction))
        } catch (e: ThrowException) {
            AOs.call(rejectFunction, JSUndefined, listOf(e.value))
        }
        return promise
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSPromiseCtor(realm).initialize()

        @ECMAImpl("26.6.4.1", "26.6.4.1.2")
        @JvmStatic
        fun all(arguments: JSArguments): JSValue {
            val capability = AOs.newPromiseCapability(arguments.thisValue)
            val resolve = ifAbruptRejectPromise(capability, { return it }) {
                AOs.getPromiseResolve(arguments.thisValue)
            }
            val iteratorRecord = ifAbruptRejectPromise(capability, { return it }) {
                AOs.getIterator(arguments.argument(0))
            }

            return try {
                performPromiseAll(iteratorRecord, arguments.thisValue, capability, resolve)
            } catch (e: ThrowException) {
                try {
                    if (!iteratorRecord.isDone)
                        AOs.iteratorClose(iteratorRecord, e.value)
                    else JSEmpty
                } finally {
                    AOs.call(capability.reject!!, JSUndefined, listOf(e.value))
                    capability.promise
                }
            }
        }

        private fun performPromiseAllSettled(
            iteratorRecord: AOs.IteratorRecord,
            constructor: JSValue,
            resultCapability: AOs.PromiseCapability,
            promiseResolve: JSValue
        ): JSValue {
            ecmaAssert(AOs.isConstructor(constructor))
            ecmaAssert(AOs.isCallable(promiseResolve))

            val values = mutableListOf<JSValue>()
            val remainingElementCount = AOs.Wrapper(1)
            var index = 0

            while (true) {
                val next = try {
                    AOs.iteratorStep(iteratorRecord)
                } catch (e: ThrowException) {
                    iteratorRecord.isDone = true
                    throw e
                }

                if (next == JSFalse) {
                    iteratorRecord.isDone = true
                    remainingElementCount.value--
                    if (remainingElementCount.value == 0) {
                        val valuesArray = AOs.createArrayFromList(values)
                        AOs.call(resultCapability.resolve!!, JSUndefined, listOf(valuesArray))
                    }
                    return resultCapability.promise
                }

                val nextValue = try {
                    AOs.iteratorValue(next)
                } catch (e: ThrowException) {
                    iteratorRecord.isDone = true
                    throw e
                }

                values.add(JSUndefined)
                val nextPromise = AOs.call(promiseResolve, constructor, listOf(nextValue))
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
                AOs.invoke(nextPromise, "then".toValue(), listOf(resolveElement, rejectElement))
                index++
            }
        }

        @ECMAImpl("27.2.4.2")
        @JvmStatic
        fun allSettled(arguments: JSArguments): JSValue {
            val capability = AOs.newPromiseCapability(arguments.thisValue)
            val resolve = ifAbruptRejectPromise(capability, { return it }) {
                AOs.getPromiseResolve(arguments.thisValue)
            }
            val iteratorRecord = ifAbruptRejectPromise(capability, { return it }) {
                AOs.getIterator(arguments.argument(0))
            }

            return try {
                performPromiseAllSettled(iteratorRecord, arguments.thisValue, capability, resolve)
            } catch (e: ThrowException) {
                try {
                    if (!iteratorRecord.isDone)
                        AOs.iteratorClose(iteratorRecord, e.value)
                    else JSEmpty
                } finally {
                    AOs.call(capability.reject!!, JSUndefined, listOf(e.value))
                    return capability.promise
                }
            }
        }

        private fun performPromiseAll(
            iteratorRecord: AOs.IteratorRecord,
            constructor: JSValue,
            resultCapability: AOs.PromiseCapability,
            promiseResolve: JSValue
        ): JSValue {
            ecmaAssert(AOs.isConstructor(constructor))
            ecmaAssert(AOs.isCallable(promiseResolve))

            val values = mutableListOf<JSValue>()
            val remainingElementCount = AOs.Wrapper(1)
            var index = 0

            while (true) {
                val next = try {
                    AOs.iteratorStep(iteratorRecord)
                } catch (e: ThrowException) {
                    iteratorRecord.isDone = true
                    throw e
                }

                if (next == JSFalse) {
                    iteratorRecord.isDone = true
                    remainingElementCount.value--
                    if (remainingElementCount.value == 0) {
                        val valuesArray = AOs.createArrayFromList(values)
                        AOs.call(resultCapability.resolve!!, JSUndefined, listOf(valuesArray))
                    }
                    return resultCapability.promise
                }

                val nextValue = try {
                    AOs.iteratorValue(next)
                } catch (e: ThrowException) {
                    iteratorRecord.isDone = true
                    throw e
                }

                values.add(JSUndefined)
                val nextPromise = AOs.call(promiseResolve, constructor, listOf(nextValue))
                val resolveElement =
                    JSPromiseAllResolver.create(index, values, resultCapability, remainingElementCount)
                remainingElementCount.value++
                AOs.invoke(
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
            val capability = AOs.newPromiseCapability(arguments.thisValue)
            AOs.call(capability.reject!!, JSUndefined, listOf(arguments.argument(0)))
            return capability.promise
        }

        @ECMAImpl("27.2.4.7")
        @JvmStatic
        fun resolve(arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Promise.resolve").throwTypeError()
            return AOs.promiseResolve(arguments.thisValue, arguments.argument(0))
        }

        private inline fun <T> ifAbruptRejectPromise(
            capability: AOs.PromiseCapability,
            returnBlock: (JSValue) -> Nothing,
            producer: () -> T
        ): T {
            return try {
                producer()
            } catch (e: ThrowException) {
                AOs.call(capability.reject!!, JSUndefined, listOf(e.value))
                returnBlock(capability.promise)
            }
        }
    }
}
