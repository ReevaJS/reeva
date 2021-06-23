package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

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
        defineNativeFunction("toString", 0, ::toString)
    }

    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Error.prototype.toString").throwTypeError(realm)

        val name = thisValue.get("name").let {
            if (it == JSUndefined) {
                "Error".toValue()
            } else Operations.toString(realm, it)
        }

        val message = thisValue.get("message").let {
            if (it == JSUndefined) {
                "".toValue()
            } else Operations.toString(realm, it)
        }

        if (name.string.isEmpty())
            return message
        if (message.string.isEmpty())
            return name
        return "${name.string}: ${message.string}".toValue()
    }

    companion object {
        fun create(realm: Realm) = JSErrorProto(realm, realm.errorCtor, realm.objectProto).initialize()
    }
}
