package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSNativeFunction
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.toValue

class JSStringCtor private constructor(realm: Realm) : JSNativeFunction(realm, "String", 1) {
    override fun call(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        return if (arguments.isEmpty()) {
            "".toValue()
        } else Operations.toString(arguments[0])
    }

    override fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue {
        if (newTarget == JSUndefined && arguments[0].isSymbol)
            return arguments[0].asSymbol.descriptiveString().toValue()
        val s = if (arguments.isEmpty()) {
            "".toValue()
        } else {
            Operations.toString(arguments[0])
        }
        if (newTarget == JSUndefined)
            return s
        // TODO: GetPrototypeFromConstructor?
        return JSStringObject.create(realm, s)
    }

    companion object {
        fun create(realm: Realm) = JSStringCtor(realm).also { it.init() }
    }
}
