package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.toValue

class JSPromiseCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Promise", 2) {
    override fun init() {
        super.init()

        defineBuiltin("all", 1, ReevaBuiltin.PromiseCtorAll)
        defineBuiltin("allSettled", 1, ReevaBuiltin.PromiseCtorAllSettled)
        defineBuiltin("resolve", 1, ReevaBuiltin.PromiseCtorResolve)
        defineBuiltin("reject", 1, ReevaBuiltin.PromiseCtorReject)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Promise").throwTypeError(realm)

        val executor = arguments.argument(0)
        if (!Operations.isCallable(executor))
            Errors.Promise.CtorFirstArgCallable.throwTypeError(realm)

        val promise = Operations.createPromise(realm, arguments.newTarget)
        val (resolveFunction, rejectFunction) = Operations.createResolvingFunctions(promise)

        try {
            Operations.call(realm, executor, JSUndefined, listOf(resolveFunction, rejectFunction))
        } catch (e: ThrowException) {
            Operations.call(realm, rejectFunction, JSUndefined, listOf(e.value))
        }
        return promise
    }

    companion object {
        fun create(realm: Realm) = JSPromiseCtor(realm).initialize()

        @ECMAImpl("26.6.4.1")
        @ECMAImpl("26.6.4.1.2", name = "PerformPromiseAll")
        @JvmStatic
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

        private fun performPromiseAllSettled(
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
                val resolveElement = JSPromiseAllSettledResolver.create(
                    realm,
                    index,
                    values,
                    resultCapability,
                    remainingElementCount,
                    false
                )
                val rejectElement = JSPromiseAllSettledResolver.create(
                    realm,
                    index,
                    values,
                    resultCapability,
                    remainingElementCount,
                    true
                )
                remainingElementCount.value++
                Operations.invoke(realm, nextPromise, "then".toValue(), listOf(resolveElement, rejectElement))
                index++
            }
        }

        @ECMAImpl("27.2.4.2")
        @JvmStatic
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

        private fun performPromiseAll(
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
                Operations.invoke(
                    realm,
                    nextPromise,
                    "then".toValue(),
                    listOf(resolveElement, resultCapability.reject!!)
                )
                index++
            }
        }

        @ECMAImpl("27.2.4.6")
        @JvmStatic
        fun reject(realm: Realm, arguments: JSArguments): JSValue {
            val capability = Operations.newPromiseCapability(realm, arguments.thisValue)
            Operations.call(realm, capability.reject!!, JSUndefined, listOf(arguments.argument(0)))
            return capability.promise
        }

        @ECMAImpl("27.2.4.7")
        @JvmStatic
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
    }
}
