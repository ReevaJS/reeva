package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

class JSStringCtor private constructor(realm: Realm) : JSNativeFunction(realm, "String", 1) {
    init {
        isConstructable = true
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        return construct(arguments, JSUndefined)
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        val argument = arguments.argument(0)
        if (newTarget == JSUndefined && argument.isSymbol)
            return argument.asSymbol.descriptiveString().toValue()
        val s = if (arguments.isEmpty()) {
            "".toValue()
        } else Operations.toString(argument)
        if (newTarget == JSUndefined)
            return s
        // TODO: GetPrototypeFromConstructor?
        return JSStringObject.create(realm, s)
    }

    companion object {
        fun create(realm: Realm) = JSStringCtor(realm).initialize()
    }
}
