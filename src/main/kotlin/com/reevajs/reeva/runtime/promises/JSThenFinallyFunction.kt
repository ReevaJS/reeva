package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.toValue

class JSThenFinallyFunction private constructor(
    realm: Realm,
    private val ctor: JSFunction,
    private val onFinally: JSFunction,
) : JSNativeFunction(realm, "", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            throw IllegalStateException("Unexpected construction of JSThenFinallyFunction")
        val result = Operations.call(onFinally, JSUndefined)
        val promise = Operations.promiseResolve(ctor, result)
        val valueThunk = fromLambda("", 0) { arguments.argument(0) }
        return Operations.invoke(promise, "then".toValue(), listOf(valueThunk))
    }

    companion object {
        fun create(ctor: JSFunction, onFinally: JSFunction, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSThenFinallyFunction(realm, ctor, onFinally).initialize()
    }
}
