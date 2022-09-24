package com.reevajs.reeva.test262

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.expect

class JSAsyncDoneFunction private constructor(realm: Realm) : JSNativeFunction(realm, "\$DONE", 1) {
    var invocationCount = 0
        private set

    var result: JSValue = JSUndefined
        private set

    override fun evaluate(arguments: JSArguments): JSValue {
        expect(arguments.newTarget == JSUndefined)
        invocationCount++
        result = arguments.argument(0)
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSAsyncDoneFunction(realm).initialize()
    }
}
