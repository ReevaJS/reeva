package me.mattco.reeva.runtime.objects

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.SlotName
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
import me.mattco.reeva.utils.*

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.objectCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeAccessor("__proto__", attrs { +conf -enum }, ::getProto, ::setProto)
        defineNativeFunction("__defineGetter__", 2, ::defineGetter)
        defineNativeFunction("__defineSetter__", 2, ::defineSetter)
        defineNativeFunction("__lookupGetter__", 1, ::lookupGetter)
        defineNativeFunction("__lookupSetter__", 1, ::lookupSetter)
        defineNativeFunction("hasOwnProperty", 1, ::hasOwnProperty)
        defineNativeFunction("isPrototypeOf", 1, ::isPrototypeOf)
        defineNativeFunction("propertyIsEnumerable", 1, ::propertyIsEnumerable)
        defineNativeFunction("toLocaleString", 0, ::toLocaleString)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    @ECMAImpl("B.2.2.1.1")
    fun getProto(thisValue: JSValue): JSValue {
        return Operations.toObject(thisValue).getPrototype()
    }

    @ECMAImpl("B.2.2.1.2")
    fun setProto(thisValue: JSValue, proto: JSValue): JSValue {
        val obj = Operations.requireObjectCoercible(thisValue)
        if (proto !is JSObject && proto != JSNull)
            Errors.Object.ProtoValue.throwTypeError()
        if (obj is JSObject && !obj.setPrototype(proto))
            Errors.TODO("set Object.prototype.__proto__").throwTypeError()
        return JSUndefined
    }

    @ECMAImpl("B.2.2.2")
    fun defineGetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val getter = arguments.argument(1)
        if (!Operations.isCallable(getter))
            Errors.Object.DefineGetterBadArgType.throwTypeError()
        val desc = Descriptor(JSAccessor(getter as JSFunction, null), Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE)
        val key = Operations.toPropertyKey(arguments.argument(0))
        Operations.definePropertyOrThrow(obj, key, desc)
        return JSUndefined
    }

    @ECMAImpl("B.2.2.2")
    fun defineSetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val setter = arguments.argument(1)
        if (!Operations.isCallable(setter))
            Errors.Object.DefineSetterBadArgType.throwTypeError()
        val desc = Descriptor(JSAccessor(null, setter as JSFunction), Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE)
        val key = Operations.toPropertyKey(arguments.argument(0))
        Operations.definePropertyOrThrow(obj, key, desc)
        return JSUndefined
    }

    @ECMAImpl("B.2.2.4")
    fun lookupGetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        var obj = Operations.toObject(thisValue)
        val key = Operations.toPropertyKey(arguments.argument(0))
        while (true) {
            val desc = obj.getOwnPropertyDescriptor(key)
            if (desc != null) {
                if (desc.isAccessorDescriptor)
                    return desc.getter ?: JSNull
                return JSUndefined
            }
            obj = obj.getPrototype() as? JSObject ?: return JSUndefined
        }
    }

    @ECMAImpl("B.2.2.4")
    fun lookupSetter(thisValue: JSValue, arguments: JSArguments): JSValue {
        var obj = Operations.toObject(thisValue)
        val key = Operations.toPropertyKey(arguments.argument(0))
        while (true) {
            val desc = obj.getOwnPropertyDescriptor(key)
            if (desc != null) {
                if (desc.isAccessorDescriptor)
                    return desc.setter ?: JSNull
                return JSUndefined
            }
            obj = obj.getPrototype() as? JSObject ?: return JSUndefined
        }
    }

    @ECMAImpl("19.1.3.2")
    fun hasOwnProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        val o = Operations.toObject(thisValue)
        return Operations.hasOwnProperty(o, key).toValue()
    }

    @ECMAImpl("19.1.3.3")
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
    fun propertyIsEnumerable(thisValue: JSValue, arguments: JSArguments): JSValue {
        val key = Operations.toPropertyKey(arguments.argument(0))
        val thisObj = Operations.toObject(thisValue)
        val desc = thisObj.getOwnPropertyDescriptor(key) ?: return JSFalse
        return desc.isEnumerable.toValue()
    }

    @ECMAImpl("19.1.3.5")
    fun toLocaleString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        return Operations.invoke(thisObj, "toString".toValue())
    }

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
                when {
                    Operations.isArray(obj) -> "Array"
                    obj.hasSlot(SlotName.ParameterMap) -> "Arguments"
                    obj is JSFunction -> "Function" // TODO: Slot check? Can you extend Function?
                    obj.hasSlot(SlotName.ErrorData) -> "Error"
                    obj.hasSlot(SlotName.BooleanData) -> "Boolean"
                    obj.hasSlot(SlotName.NumberData) -> "Number"
                    obj.hasSlot(SlotName.StringData) -> "String"
                    obj.hasSlot(SlotName.DateValue) -> "Date"
                    obj.hasSlot(SlotName.RegExpMatcher) -> "RegExp"
                    else -> "Object"
                }
            }
        }

        // TODO: @@toStringTag
        return "[object $tag]".toValue()
    }

    fun valueOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        return Operations.toObject(thisValue)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSObjectProto(realm)
    }
}
