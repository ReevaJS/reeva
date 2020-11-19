package me.mattco.reeva.runtime.errors

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

open class JSErrorProto protected constructor(
    realm: Realm,
    val name: String = "Error"
) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.errorCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty("name", name.toValue(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @JSMethod("toString", 0)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSObject)
            Errors.IncompatibleMethodCall("Error.prototype.toString").throwTypeError()

        val name = thisValue.get("name").let {
            if (it == JSUndefined) {
                "Error".toValue()
            } else Operations.toString(it)
        }

        val message = thisValue.get("message").let {
            if (it == JSUndefined) {
                "".toValue()
            } else Operations.toString(it)
        }

        if (name.string.isEmpty())
            return message
        if (message.string.isEmpty())
            return name
        return "${name.string}: ${message.string}".toValue()
    }

    companion object {
        fun create(realm: Realm) = JSErrorProto(realm).initialize()
    }
}
