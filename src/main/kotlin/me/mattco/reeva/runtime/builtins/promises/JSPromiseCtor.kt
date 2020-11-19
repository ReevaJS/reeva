package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSPromiseCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Promise", 2) {
    init {
        isConstructable = true
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Promise").throwTypeError()

        val executor = arguments.argument(0)
        if (!Operations.isCallable(executor))
            Errors.Promise.CtorFirstArgCallable.throwTypeError()

        val promise = JSPromiseObject.create(Operations.PromiseState.Pending, JSEmpty, realm)
        val (resolveFunction, rejectFunction) = Operations.createResolvingFunctions(promise)
        try {
            Operations.call(executor, JSUndefined, listOf(resolveFunction, rejectFunction))
        } catch (e: ThrowException) {
            Operations.call(rejectFunction, JSUndefined, listOf(e.value))
        }
        return promise
    }

    @ECMAImpl("26.6.4.1")
    @ECMAImpl("26.6.4.1.2", name = "PerformPromiseAll")
    @JSMethod("all", 1)
    fun all(thisValue: JSValue, arguments: JSArguments): JSValue {
        val capability = Operations.newPromiseCapability(thisValue)
        val resolve = ifAbruptRejectPromise(capability, { return it }) {
            Operations.getPromiseResolve(thisValue)
        }
        val iteratorRecord = ifAbruptRejectPromise(capability, { return it }) {
            Operations.getIterator(arguments.argument(0))
        }

        return try {
            performPromiseAll(iteratorRecord, thisValue, capability, resolve)
        } catch (e: ThrowException) {
            try {
                if (!iteratorRecord.done)
                    Operations.iteratorClose(iteratorRecord, e.value)
                else JSEmpty
            } finally {
                Operations.call(capability.reject!!, JSUndefined, listOf(e.value))
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

        val values = mutableListOf<JSValue>()
        val remainingElementCount = Operations.Wrapper(1)
        var index = 0

        while (true) {
            val next = try {
                Operations.iteratorStep(iteratorRecord)
            } catch (e: ThrowException) {
                iteratorRecord.done = true
                throw e
            }

            if (next == JSFalse) {
                iteratorRecord.done = true
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
                iteratorRecord.done = true
                throw e
            }

            values.add(JSUndefined)
            val nextPromise = Operations.call(promiseResolve, constructor, listOf(nextValue))
            val resolveElement = JSPromiseAllSettledResolver.create(realm, index, values, resultCapability, remainingElementCount, false)
            val rejectElement = JSPromiseAllSettledResolver.create(realm, index, values, resultCapability, remainingElementCount, true)
            remainingElementCount.value++
            Operations.invoke(nextPromise, "then".toValue(), listOf(resolveElement, rejectElement))
            index++
        }
    }

    @JSMethod("allSettled", 1)
    fun allSettled(thisValue: JSValue, arguments: JSArguments): JSValue {
        val capability = Operations.newPromiseCapability(thisValue)
        val resolve = ifAbruptRejectPromise(capability, { return it }) {
            Operations.getPromiseResolve(thisValue)
        }
        val iteratorRecord = ifAbruptRejectPromise(capability, { return it }) {
            Operations.getIterator(arguments.argument(0))
        }

        return try {
            performPromiseAllSettled(iteratorRecord, thisValue, capability, resolve)
        } catch (e: ThrowException) {
            try {
                if (!iteratorRecord.done)
                    Operations.iteratorClose(iteratorRecord, e.value)
                else JSEmpty
            } finally {
                Operations.call(capability.reject!!, JSUndefined, listOf(e.value))
                return capability.promise
            }
        }
    }

    fun performPromiseAll(
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
                iteratorRecord.done = true
                throw e
            }

            if (next == JSFalse) {
                iteratorRecord.done = true
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
                iteratorRecord.done = true
                throw e
            }

            values.add(JSUndefined)
            val nextPromise = Operations.call(promiseResolve, constructor, listOf(nextValue))
            val resolveElement =
                JSPromiseAllResolver.create(realm, index, values, resultCapability, remainingElementCount)
            remainingElementCount.value++
            Operations.invoke(nextPromise, "then".toValue(), listOf(resolveElement, resultCapability.reject!!))
            index++
        }
    }

    @JSMethod("reject", 1)
    fun reject(thisValue: JSValue, arguments: JSArguments): JSValue {
        val capability = Operations.newPromiseCapability(thisValue)
        Operations.call(capability.reject!!, JSUndefined, listOf(arguments.argument(0)))
        return capability.promise
    }

    @JSMethod("resolve", 1)
    fun resolve(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Promise.resolve").throwTypeError()
        return Operations.promiseResolve(thisValue, arguments.argument(0))
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

    companion object {
        fun create(realm: Realm) = JSPromiseCtor(realm).initialize()
    }
}
