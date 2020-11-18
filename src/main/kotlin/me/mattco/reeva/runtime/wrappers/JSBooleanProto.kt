package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSBoolean
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

class JSBooleanProto private constructor(realm: Realm) : JSBooleanObject(realm, JSFalse) {
    override fun init() {
        annotationInit()
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, 0)

        defineOwnProperty("constructor", realm.booleanCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @ECMAImpl("19.3.3.2")
    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val b = thisBooleanValue(thisValue, "toString")
        return if (b.value) "true".toValue() else "false".toValue()
    }

    @ECMAImpl("19.3.3.2")
    @JSMethod("valueOf", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisBooleanValue(thisValue, "valueOf")
    }

    companion object {
        fun create(realm: Realm) = JSBooleanProto(realm).initialize()

        private fun thisBooleanValue(value: JSValue, methodName: String): JSBoolean {
            if (value.isBoolean)
                return value as JSBoolean
            if (value is JSBooleanObject)
                return value.value
            Errors.IncompatibleMethodCall("Boolean.prototype.$methodName").throwTypeError()
        }
    }
}
