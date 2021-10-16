package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSObjectProto private constructor(realm: Realm) : JSObject(realm, JSNull) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.objectCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineBuiltinGetter("__proto__", ReevaBuiltin.ObjectProtoGetProto, attrs { +conf - enum })
        defineBuiltinSetter("__proto__", ReevaBuiltin.ObjectProtoSetProto, attrs { +conf - enum })
        defineBuiltin("__defineGetter__", 2, ReevaBuiltin.ObjectProtoDefineGetter)
        defineBuiltin("__defineSetter__", 2, ReevaBuiltin.ObjectProtoDefineSetter)
        defineBuiltin("__lookupGetter__", 1, ReevaBuiltin.ObjectProtoLookupGetter)
        defineBuiltin("__lookupSetter__", 1, ReevaBuiltin.ObjectProtoLookupSetter)
        defineBuiltin("hasOwnProperty", 1, ReevaBuiltin.ObjectProtoHasOwnProperty)
        defineBuiltin("isPrototypeOf", 1, ReevaBuiltin.ObjectProtoIsPrototypeOf)
        defineBuiltin("propertyIsEnumerable", 1, ReevaBuiltin.ObjectProtoPropertyIsEnumerable)
        defineBuiltin("toLocaleString", 0, ReevaBuiltin.ObjectProtoToLocaleString)
        defineBuiltin("toString", 0, ReevaBuiltin.ObjectProtoToString)
        defineBuiltin("valueOf", 0, ReevaBuiltin.ObjectProtoValueOf)
    }

    @ECMAImpl("10.4.7.1")
    override fun setPrototype(newPrototype: JSValue): Boolean {
        return Operations.setImmutablePrototype(this, newPrototype)
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm) = JSObjectProto(realm)

        @ECMAImpl("B.2.2.1.1")
        @JvmStatic
        fun getProto(realm: Realm, arguments: JSArguments): JSValue {
            return Operations.toObject(realm, arguments.thisValue).getPrototype()
        }

        @ECMAImpl("B.2.2.1.2")
        @JvmStatic
        fun setProto(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
            val proto = arguments.argument(0)

            if (proto !is JSObject && proto != JSNull)
                return JSUndefined
            if (obj !is JSObject)
                return JSUndefined
            if (!obj.setPrototype(proto))
                Errors.Object.ProtoValue.throwTypeError(realm)

            return JSUndefined
        }

        @ECMAImpl("B.2.2.2")
        @JvmStatic
        fun defineGetter(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.thisValue)
            val getter = arguments.argument(1)
            if (!Operations.isCallable(getter))
                Errors.Object.DefineGetterBadArgType.throwTypeError(realm)
            val desc = Descriptor(JSAccessor(getter, null), Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE)
            val key = Operations.toPropertyKey(realm, arguments.argument(0))
            Operations.definePropertyOrThrow(realm, obj, key, desc)
            return JSUndefined
        }

        @ECMAImpl("B.2.2.2")
        @JvmStatic
        fun defineSetter(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.thisValue)
            val setter = arguments.argument(1)
            if (!Operations.isCallable(setter))
                Errors.Object.DefineSetterBadArgType.throwTypeError(realm)
            val desc = Descriptor(JSAccessor(null, setter), Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE)
            val key = Operations.toPropertyKey(realm, arguments.argument(0))
            Operations.definePropertyOrThrow(realm, obj, key, desc)
            return JSUndefined
        }

        @ECMAImpl("B.2.2.4")
        @JvmStatic
        fun lookupGetter(realm: Realm, arguments: JSArguments): JSValue {
            var obj = Operations.toObject(realm, arguments.thisValue)
            val key = Operations.toPropertyKey(realm, arguments.argument(0))
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
        @JvmStatic
        fun lookupSetter(realm: Realm, arguments: JSArguments): JSValue {
            var obj = Operations.toObject(realm, arguments.thisValue)
            val key = Operations.toPropertyKey(realm, arguments.argument(0))
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
        @JvmStatic
        fun hasOwnProperty(realm: Realm, arguments: JSArguments): JSValue {
            val key = Operations.toPropertyKey(realm, arguments.argument(0))
            val o = Operations.toObject(realm, arguments.thisValue)
            return Operations.hasOwnProperty(o, key).toValue()
        }

        @ECMAImpl("19.1.3.3")
        @JvmStatic
        fun isPrototypeOf(realm: Realm, arguments: JSArguments): JSValue {
            var arg = arguments.argument(0)
            if (arg !is JSObject)
                return JSFalse
            val thisObj = Operations.toObject(realm, arguments.thisValue)
            while (true) {
                arg = (arg as JSObject).getPrototype()
                if (arg == JSNull)
                    return JSFalse
                if (arg.sameValue(thisObj))
                    return JSTrue
            }
        }

        @ECMAImpl("19.1.3.4")
        @JvmStatic
        fun propertyIsEnumerable(realm: Realm, arguments: JSArguments): JSValue {
            val key = Operations.toPropertyKey(realm, arguments.argument(0))
            val thisObj = Operations.toObject(realm, arguments.thisValue)
            val desc = thisObj.getOwnPropertyDescriptor(key) ?: return JSFalse
            return desc.isEnumerable.toValue()
        }

        @ECMAImpl("19.1.3.5")
        @JvmStatic
        fun toLocaleString(realm: Realm, arguments: JSArguments): JSValue {
            val thisObj = Operations.toObject(realm, arguments.thisValue)
            return Operations.invoke(realm, thisObj, "toString".toValue())
        }

        @ECMAImpl("20.1.3.6")
        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.thisValue == JSUndefined)
                return "[object Undefined]".toValue()
            if (arguments.thisValue == JSNull)
                return "[object Null]".toValue()

            val obj = Operations.toObject(realm, arguments.thisValue)
            val tag = obj.get(Realm.`@@toStringTag`).let {
                if (it is JSString) {
                    it.string
                } else {
                    when {
                        Operations.isArray(realm, obj) -> "Array"
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

        @JvmStatic
        fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
            return Operations.toObject(realm, arguments.thisValue)
        }
    }
}
