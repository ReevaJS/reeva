package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.utils.*
import kotlin.math.max

class JSFunctionCtor private constructor(realm: Realm) : JSNativeFunction(realm, "FunctionConstructor", 1) {
    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO("Not yet implemented")
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        TODO("Not yet implemented")
    }

    companion object {
        fun create(realm: Realm) = JSFunctionCtor(realm).initialize()
    }
}
