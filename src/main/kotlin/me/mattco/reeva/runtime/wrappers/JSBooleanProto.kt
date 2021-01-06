package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSBoolean
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

class JSBooleanProto private constructor(realm: Realm) : JSBooleanObject(realm, JSFalse) {
    override fun init() {
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.booleanCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    @ECMAImpl("19.3.3.2")
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val b = thisBooleanValue(thisValue, "toString")
        return if (b.boolean) "true".toValue() else "false".toValue()
    }

    @ECMAImpl("19.3.3.2")
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisBooleanValue(thisValue, "valueOf")
    }

    companion object {
        fun create(realm: Realm) = JSBooleanProto(realm).initialize()

        private fun thisBooleanValue(value: JSValue, methodName: String): JSBoolean {
            if (value.isBoolean)
                return value as JSBoolean
            if (value !is JSObject)
                Errors.IncompatibleMethodCall("Boolean.prototype.$methodName").throwTypeError()
            return value.getSlotAs(SlotName.BooleanData) ?:
                Errors.IncompatibleMethodCall("Boolean.prototype.$methodName").throwTypeError()
        }
    }
}
