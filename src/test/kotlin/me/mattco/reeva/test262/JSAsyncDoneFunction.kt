package me.mattco.reeva.test262

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.utils.expect

class JSAsyncDoneFunction private constructor(realm: Realm) : JSNativeFunction(realm, "\$DONE", 1) {
    var invocationCount = 0
        private set

    var result: JSValue = JSUndefined
        private set

    override fun evaluate(arguments: JSArguments): JSValue {
        expect(newTarget == JSUndefined)
        invocationCount++
        result = arguments.argument(0)
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSAsyncDoneFunction(realm).initialize()
    }
}
