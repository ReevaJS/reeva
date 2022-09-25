package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.functions.JSRunnableFunction
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.toValue

class JSCatchFinallyFunction private constructor(
    realm: Realm,
    private val ctor: JSFunction,
    private val onFinally: JSFunction,
) : JSNativeFunction(realm, "", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            throw IllegalStateException("Unexpected construction of JSCatchFinallyFunction")
        val result = AOs.call(onFinally, JSUndefined)
        val promise = AOs.promiseResolve(ctor, result)
        val valueThunk = JSRunnableFunction.create("", 0) { throw ThrowException(arguments.argument(0)) }
        return AOs.invoke(promise, "then".toValue(), listOf(valueThunk))
    }

    companion object {
        fun create(ctor: JSFunction, onFinally: JSFunction, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSCatchFinallyFunction(realm, ctor, onFinally).initialize()
    }
}
