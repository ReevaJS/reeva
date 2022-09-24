package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSCapabilitiesExecutor private constructor(
    realm: Realm,
    private val capability: Operations.PromiseCapability
) : JSNativeFunction(realm, "", 2) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            throw IllegalStateException("Unexpected construction of JSCapabilitiesExecutor")
        if (capability.resolve != null)
            Errors.TODO("JSCapabilitiesExecutor 1").throwTypeError()
        if (capability.reject != null)
            Errors.TODO("JSCapabilitiesExecutor 2").throwTypeError()

        arguments.argument(0).also {
            if (!Operations.isCallable(it))
                Errors.TODO("JSCapabilitiesExecutor 3").throwTypeError()
            capability.resolve = it
        }

        arguments.argument(1).also {
            if (!Operations.isCallable(it))
                Errors.TODO("JSCapabilitiesExecutor 4").throwTypeError()
            capability.reject = it
        }

        return JSUndefined
    }

    companion object {
        fun create(realm: Realm, capability: Operations.PromiseCapability) =
            JSCapabilitiesExecutor(realm, capability).initialize()
    }
}
