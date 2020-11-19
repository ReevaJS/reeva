package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

class JSBooleanCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Boolean", 1) {
    init {
        isConstructable = true
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (newTarget == JSUndefined)
            return Operations.toBoolean(arguments.argument(0)).toValue()
        return JSBooleanObject.create(realm, Operations.toBoolean(arguments.argument(0)).toValue())
    }

    companion object {
        fun create(realm: Realm) = JSBooleanCtor(realm).initialize()
    }
}
