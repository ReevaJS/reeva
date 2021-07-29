package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSRejectFunction private constructor(
    val promise: JSObject,
    var alreadyResolved: Operations.Wrapper<Boolean>,
    realm: Realm
) : JSNativeFunction(realm, "", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            TODO()
        if (alreadyResolved.value)
            return JSUndefined
        alreadyResolved.value = true
        return Operations.rejectPromise(realm, promise, arguments.argument(0))
    }

    companion object {
        fun create(
            promise: JSObject,
            alreadyResolved: Operations.Wrapper<Boolean>,
            realm: Realm
        ) = JSRejectFunction(promise, alreadyResolved, realm).initialize()
    }
}
