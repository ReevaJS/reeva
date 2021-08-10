package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSBoolean
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSBooleanProto private constructor(realm: Realm) : JSBooleanObject(realm, JSFalse) {
    override fun init() {
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.booleanCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineBuiltin("toString", 0, ReevaBuiltin.BooleanProtoToString)
        defineBuiltin("valueOf", 0, ReevaBuiltin.BooleanProtoValueOf)
    }

    companion object {
        fun create(realm: Realm) = JSBooleanProto(realm).initialize()

        private fun thisBooleanValue(realm: Realm, value: JSValue, methodName: String): JSBoolean {
            if (value.isBoolean)
                return value as JSBoolean
            if (value !is JSObject)
                Errors.IncompatibleMethodCall("Boolean.prototype.$methodName").throwTypeError(realm)
            return value.getSlotAs(SlotName.BooleanData) ?:
                Errors.IncompatibleMethodCall("Boolean.prototype.$methodName").throwTypeError(realm)
        }

        @ECMAImpl("19.3.3.2")
        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            val b = thisBooleanValue(realm, arguments.thisValue, "toString")
            return if (b.boolean) "true".toValue() else "false".toValue()
        }

        @ECMAImpl("19.3.3.2")
        @JvmStatic
        fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
            return thisBooleanValue(realm, arguments.thisValue, "valueOf")
        }
    }
}
