package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.shouldThrowError
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

    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString_(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSObject)
            shouldThrowError("TypeError")

        val name = get("name").let {
            if (it == JSUndefined) {
                "Error".toValue()
            } else Operations.toString(it)
        }

        val message = get("message").let {
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
        fun create(realm: Realm) = JSErrorProto(realm).also { it.init() }
    }
}
