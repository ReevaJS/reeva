package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.toValue

class JSCatchFinallyFunction private constructor(
    realm: Realm,
    private val ctor: JSFunction,
    private val onFinally: JSFunction,
) : JSNativeFunction(realm, "", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            throw IllegalStateException("Unexpected construction of JSCatchFinallyFunction")
        val result = Operations.call(realm, onFinally, JSUndefined)
        val promise = Operations.promiseResolve(ctor, result)
        val valueThunk = fromLambda(realm, "", 0) { _, _ -> throw ThrowException(arguments.argument(0)) }
        return Operations.invoke(realm, promise, "then".toValue(), listOf(valueThunk))
    }

    companion object {
        fun create(realm: Realm, ctor: JSFunction, onFinally: JSFunction) =
            JSCatchFinallyFunction(realm, ctor, onFinally).initialize()
    }
}
