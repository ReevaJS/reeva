package com.reevajs.reeva.runtime.wrappers.strings

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSStringCtor private constructor(realm: Realm) : JSNativeFunction(realm, "String", 1) {
    override fun init() {
        super.init()

        defineBuiltin("fromCharCode", 1, ::fromCharCode)
        defineBuiltin("fromCodePoint", 1, ::fromCodePoint)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget

        val theString = if (arguments.isEmpty()) {
            "".toValue()
        } else {
            val value = arguments.argument(0)
            if (newTarget == JSUndefined && value is JSSymbol)
                return value.descriptiveString().toValue()
            value.toJSString()
        }

        if (newTarget == JSUndefined)
            return theString

        return JSStringObject.create(theString).also {
            it.setPrototype(
                Operations.getPrototypeFromConstructor(newTarget, Realm::stringProto),
            )
        }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSStringCtor(realm).initialize()

        @ECMAImpl("22.1.2.1")
        @JvmStatic
        fun fromCharCode(arguments: JSArguments): JSValue {
            return buildString {
                arguments.forEach {
                    appendCodePoint(it.toUint16().asInt)
                }
            }.toValue()
        }

        @ECMAImpl("22.1.2.2")
        @JvmStatic
        fun fromCodePoint(arguments: JSArguments): JSValue {
            return buildString {
                arguments.forEach {
                    val nextCP = it.toNumber()
                    if (!Operations.isIntegralNumber(nextCP))
                        Errors.Strings.InvalidCodepoint(nextCP.toString()).throwRangeError()
                    val value = nextCP.asInt
                    if (value < 0 || value > 0x10ffff)
                        Errors.Strings.InvalidCodepoint(value.toString()).throwRangeError()
                    appendCodePoint(value)
                }
            }.toValue()
        }
    }
}
