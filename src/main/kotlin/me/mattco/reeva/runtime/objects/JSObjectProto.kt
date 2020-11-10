package me.mattco.reeva.runtime.objects

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.*
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.builtins.JSDateObject
import me.mattco.reeva.runtime.builtins.JSMappedArgumentsObject
import me.mattco.reeva.runtime.builtins.JSUnmappedArgumentsObject
import me.mattco.reeva.runtime.errors.JSErrorObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.wrappers.JSBooleanObject
import me.mattco.reeva.runtime.wrappers.JSNumberObject
import me.mattco.reeva.runtime.wrappers.JSStringObject
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    override fun init() {
        super.init()
        defineOwnProperty("constructor", realm.objectCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    @ECMAImpl("B.2.2.1.1")
    @JSNativeAccessorGetter("__proto__", Descriptor.CONFIGURABLE)
    fun getProto(thisValue: JSValue): JSValue {
        return Operations.toObject(thisValue).getPrototype()
    }

    @ECMAImpl("B.2.2.1.2")
    @JSNativeAccessorSetter("__proto__", Descriptor.CONFIGURABLE)
    fun setProto(thisValue: JSValue, proto: JSValue): JSValue {
        val obj = Operations.requireObjectCoercible(thisValue)
        if (proto !is JSObject && proto != JSNull)
            Errors.Object.ProtoValue.throwTypeError()
        if (obj is JSObject && !obj.setPrototype(proto))
            Errors.TODO("set Object.prototype.__proto__").throwTypeError()
        return JSUndefined
    }

    @ECMAImpl("B.2.2.2")
    @JSMethod("__defineGetter__", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun defineGetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val getter = arguments.argument(1)
        if (!Operations.isCallable(getter))
            Errors.Object.DefineGetterBadArgType.throwTypeError()
        val desc = Descriptor(JSEmpty, Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE, getter)
        val key = Operations.toPropertyKey(arguments.argument(0))
        Operations.definePropertyOrThrow(obj, key, desc)
        return JSUndefined
    }

    @ECMAImpl("B.2.2.2")
    @JSMethod("__defineSetter__", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun defineSetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val setter = arguments.argument(1)
        if (!Operations.isCallable(setter))
            Errors.Object.DefineSetterBadArgType.throwTypeError()
        val desc = Descriptor(JSEmpty, Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE, setter = setter)
        val key = Operations.toPropertyKey(arguments.argument(0))
        Operations.definePropertyOrThrow(obj, key, desc)
        return JSUndefined
    }

    @ECMAImpl("B.2.2.4")
    @JSMethod("__lookupGetter__", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun lookupGetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        var obj = Operations.toObject(thisValue)
        val key = Operations.toPropertyKey(arguments.argument(0))
        while (true) {
            val desc = obj.getOwnPropertyDescriptor(key)
            if (desc != null) {
                if (desc.isAccessorDescriptor)
                    return desc.getter
                return JSUndefined
            }
            obj = obj.getPrototype() as? JSObject ?: return JSUndefined
        }
    }

    @ECMAImpl("B.2.2.4")
    @JSMethod("__lookupSetter__", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun lookupSetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        var obj = Operations.toObject(thisValue)
        val key = Operations.toPropertyKey(arguments.argument(0))
        while (true) {
            val desc = obj.getOwnPropertyDescriptor(key)
            if (desc != null) {
                if (desc.isAccessorDescriptor)
                    return desc.setter
                return JSUndefined
            }
            obj = obj.getPrototype() as? JSObject ?: return JSUndefined
        }
    }

    @ECMAImpl("19.1.3.2")
    @JSMethod("hasOwnProperty", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun hasOwnProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        val o = Operations.toObject(thisValue)
        return Operations.hasOwnProperty(o, key).toValue()
    }

    @ECMAImpl("19.1.3.3")
    @JSMethod("isPrototypeOf", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        var arg = arguments.argument(0)
        if (arg !is JSObject)
            return JSFalse
        val thisObj = Operations.toObject(thisValue)
        while (true) {
            arg = (arg as JSObject).getPrototype()
            if (arg == JSNull)
                return JSFalse
            if (arg.sameValue(thisObj))
                return JSTrue
        }
    }

    @ECMAImpl("19.1.3.4")
    @JSMethod("propertyIsEnumerable", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun propertyIsEnumerable(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        val thisObj = Operations.toObject(thisValue)
        val desc = thisObj.getOwnPropertyDescriptor(key) ?: return JSFalse
        return desc.isEnumerable.toValue()
    }

    @ECMAImpl("19.1.3.5")
    @JSMethod("toLocaleString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toLocaleString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        return Operations.invoke(thisObj, "toString".toValue())
    }

    // Doesn't throw because Symbol overrides this method,
    @JSMethod("toString", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun toString(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        if (thisValue == JSUndefined)
            return "[object Undefined]".toValue()
        if (thisValue == JSNull)
            return "[object Null]".toValue()

        val obj = Operations.toObject(thisValue)
        val tag = obj.get(Realm.`@@toStringTag`).let {
            if (it is JSString) {
                it.string
            } else {
                when (obj) {
                    is JSArrayObject -> "Array"
                    is JSDateObject -> "Date"
                    is JSFunction -> "Function"
                    is JSErrorObject -> "Error"
                    is JSBooleanObject -> "Boolean"
                    is JSNumberObject -> "Number"
                    is JSStringObject -> "String"
                    is JSUnmappedArgumentsObject, is JSMappedArgumentsObject -> "Arguments"
                    else -> "Object"
                }
            }
        }

        // TODO: @@toStringTag
        return "[object $tag]".toValue()
    }

    @JSMethod("valueOf", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.toObject(thisValue)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSObjectProto(realm)
    }
}
