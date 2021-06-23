package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

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
