package me.mattco.reeva.test262

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument

class JSAsyncDoneFunction private constructor(realm: Realm) : JSNativeFunction(realm, "\$DONE", 1) {
    var invocationCount = 0
        private set

    var result: JSValue = JSUndefined
        private set

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        invocationCount++
        result = arguments.argument(0)
        return JSUndefined
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        throw IllegalStateException("Unexpected constructino of JSAsyncDoneFunction")
    }

    companion object {
        fun create(realm: Realm) = JSAsyncDoneFunction(realm).also { it.init() }
    }
}
