package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

open class JSErrorProto protected constructor(
    realm: Realm,
    val errorCtor: JSObject,
    val proto: JSObject,
    val name: String = "Error"
) : JSObject(realm, proto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", errorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty("name", name.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty("message", "".toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineBuiltin("toString", 0, ::toString)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSErrorProto(realm, realm.errorCtor, realm.objectProto).initialize()

        @ECMAImpl("20.5.3.4")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("Error.prototype.toString").throwTypeError()

            val name = thisValue.get("name").let {
                if (it == JSUndefined) {
                    "Error".toValue()
                } else it.toJSString()
            }

            val message = thisValue.get("message").let {
                if (it == JSUndefined) {
                    "".toValue()
                } else it.toJSString()
            }

            if (name.string.isEmpty())
                return message
            if (message.string.isEmpty())
                return name
            return "${name.string}: ${message.string}".toValue()
        }
    }
}
