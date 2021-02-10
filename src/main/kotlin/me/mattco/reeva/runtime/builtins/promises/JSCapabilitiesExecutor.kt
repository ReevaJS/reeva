package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors

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
            capability.resolve = it as JSFunction
        }

        arguments.argument(1).also {
            if (!Operations.isCallable(it))
                Errors.TODO("JSCapabilitiesExecutor 4").throwTypeError()
            capability.reject = it as JSFunction
        }

        return JSUndefined
    }

    companion object {
        fun create(realm: Realm, capability: Operations.PromiseCapability) =
            JSCapabilitiesExecutor(realm, capability).initialize()
    }
}
