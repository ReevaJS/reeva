package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument

class JSRejectFunction private constructor(
    val promise: JSPromiseObject,
    var alreadyResolved: Operations.Wrapper<Boolean>,
    realm: Realm
) : JSNativeFunction(realm, "", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (newTarget != JSUndefined)
            TODO()
        if (alreadyResolved.value)
            return JSUndefined
        alreadyResolved.value = true
        return Operations.rejectPromise(promise, arguments.argument(0))
    }

    companion object {
        fun create(
            promise: JSPromiseObject,
            alreadyResolved: Operations.Wrapper<Boolean>,
            realm: Realm
        ) = JSRejectFunction(promise, alreadyResolved, realm).initialize()
    }
}
