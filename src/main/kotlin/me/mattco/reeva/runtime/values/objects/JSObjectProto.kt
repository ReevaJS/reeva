package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.arrays.JSArray
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.primitives.JSFalse
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSTrue
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.runtime.values.wrappers.JSStringObject
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    override fun init() {
        defineOwnProperty("constructor", realm.objectCtor, 0)
    }

    @JSThrows
    @ECMAImpl("Object.prototype.hasOwnProperty", "19.1.3.2")
    @JSMethod("hasOwnProperty", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun hasOwnProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        checkError() ?: return INVALID_VALUE
        val o = Operations.toObject(thisValue)
        checkError() ?: return INVALID_VALUE
        return Operations.hasOwnProperty(o, key)
    }

    @JSThrows
    @ECMAImpl("Object.prototype.isPrototypeOf", "19.1.3.3")
    @JSMethod("isPrototypeOf", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        var arg = arguments.argument(0)
        if (arg !is JSObject)
            return JSFalse
        val thisObj = Operations.toObject(thisValue)
        checkError() ?: return INVALID_VALUE
        while (true) {
            arg = (arg as JSObject).getPrototype()
            checkError() ?: return INVALID_VALUE
            if (arg == JSNull)
                return JSFalse
            if (arg.sameValue(thisObj))
                return JSTrue
        }
    }

    @JSThrows
    @ECMAImpl("Object.prototype.propertyIsEnumerable", "19.1.3.4")
    @JSMethod("propertyIsEnumerable", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun propertyIsEnumerable(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        checkError() ?: return INVALID_VALUE
        val thisObj = Operations.toObject(thisValue)
        checkError() ?: return INVALID_VALUE
        val desc = thisObj.getOwnPropertyDescriptor(key) ?: return JSFalse
        return desc.isEnumerable.toValue()
    }

    @JSThrows
    @ECMAImpl("Object.prototype.toLocaleString", "19.1.3.5")
    @JSMethod("toLocaleString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toLocaleString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        checkError() ?: return INVALID_VALUE
        return Operations.invoke(thisObj, "toString".toValue())
    }

    // Doesn't throw because Symbol overrides this method,
    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString_(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        if (thisValue == JSUndefined)
            return "[object Undefined]".toValue()
        if (thisValue == JSNull)
            return "[object Null]".toValue()

        val obj = Operations.toObject(thisValue)
        checkError() ?: return INVALID_VALUE
        var builtinTag = obj.get(realm.`@@toStringTag`)
        if (builtinTag == JSUndefined) {
            builtinTag = when (obj) {
                is JSArray -> "Array".toValue()
                is JSFunction -> "Function".toValue()
                is JSStringObject -> "String".toValue()
                else -> "Object".toValue()
            }
        }

        // TODO: @@toStringTag
        return "[object $builtinTag]".toValue()
    }

    @JSThrows
    @JSMethod("valueOf", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.toObject(thisValue)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSObjectProto(realm)
    }
}
