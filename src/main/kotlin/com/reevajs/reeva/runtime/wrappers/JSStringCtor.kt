package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSStringCtor private constructor(realm: Realm) : JSNativeFunction(realm, "String", 1) {
    override fun init() {
        super.init()

        defineNativeFunction("fromCharCode", 1, ::fromCharCode)
        defineNativeFunction("fromCodePoint", 1, ::fromCodePoint)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget

        val theString = if (arguments.isEmpty()) {
            "".toValue()
        } else {
            val value = arguments.argument(0)
            if (newTarget == JSUndefined && value is JSSymbol)
                return value.descriptiveString().toValue()
            Operations.toString(realm, value)
        }

        if (newTarget == JSUndefined)
            return theString

        return JSStringObject.create(realm, theString).also {
            it.setPrototype(Operations.getPrototypeFromConstructor(newTarget, realm.stringProto))
        }
    }

    fun fromCharCode(realm: Realm, arguments: JSArguments): JSValue {
        return buildString {
            arguments.forEach {
                appendCodePoint(Operations.toUint16(realm, it).asInt)
            }
        }.toValue()
    }

    fun fromCodePoint(realm: Realm, arguments: JSArguments): JSValue {
        return buildString {
            arguments.forEach {
                val nextCP = Operations.toNumber(realm, it)
                if (!Operations.isIntegralNumber(nextCP))
                    Errors.Strings.InvalidCodepoint(Operations.toPrintableString(nextCP)).throwRangeError(realm)
                val value = nextCP.asInt
                if (value < 0 || value > 0x10ffff)
                    Errors.Strings.InvalidCodepoint(value.toString()).throwRangeError(realm)
                appendCodePoint(value)
            }
        }.toValue()
    }

    companion object {
        fun create(realm: Realm) = JSStringCtor(realm).initialize()
    }
}
