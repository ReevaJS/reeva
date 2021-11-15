package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
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

        val realm = Agent.activeAgent.getActiveRealm()
        defineOwnProperty("constructor", realm.objectCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineBuiltinGetter("__proto__", ReevaBuiltin.ObjectProtoGetProto, attrs { +conf; -enum })
        defineBuiltinSetter("__proto__", ReevaBuiltin.ObjectProtoSetProto, attrs { +conf; -enum })
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
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSObjectProto(realm)

        @ECMAImpl("B.2.2.1.1")
        @JvmStatic
        fun getProto(arguments: JSArguments): JSValue {
            return arguments.thisValue.toObject().getPrototype()
        }

        @ECMAImpl("B.2.2.1.2")
        @JvmStatic
        fun setProto(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.requireObjectCoercible()
            val proto = arguments.argument(0)

            if (proto !is JSObject && proto != JSNull)
                return JSUndefined
            if (obj !is JSObject)
                return JSUndefined
            if (!obj.setPrototype(proto))
                Errors.Object.ProtoValue.throwTypeError()

            return JSUndefined
        }

        @ECMAImpl("B.2.2.2")
        @JvmStatic
        fun defineGetter(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val getter = arguments.argument(1)
            if (!Operations.isCallable(getter))
                Errors.Object.DefineGetterBadArgType.throwTypeError()
            val desc = Descriptor(JSAccessor(getter, null), Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE)
            val key = arguments.argument(0).toPropertyKey()
            Operations.definePropertyOrThrow(obj, key, desc)
            return JSUndefined
        }

        @ECMAImpl("B.2.2.2")
        @JvmStatic
        fun defineSetter(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val setter = arguments.argument(1)
            if (!Operations.isCallable(setter))
                Errors.Object.DefineSetterBadArgType.throwTypeError()
            val desc = Descriptor(JSAccessor(null, setter), Descriptor.ENUMERABLE or Descriptor.CONFIGURABLE)
            val key = arguments.argument(0).toPropertyKey()
            Operations.definePropertyOrThrow(obj, key, desc)
            return JSUndefined
        }

        @ECMAImpl("B.2.2.4")
        @JvmStatic
        fun lookupGetter(arguments: JSArguments): JSValue {
            var obj = arguments.thisValue.toObject()
            val key = arguments.argument(0).toPropertyKey()
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
        fun lookupSetter(arguments: JSArguments): JSValue {
            var obj = arguments.thisValue.toObject()
            val key = arguments.argument(0).toPropertyKey()
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
        fun hasOwnProperty(arguments: JSArguments): JSValue {
            val key = arguments.argument(0).toPropertyKey()
            val o = arguments.thisValue.toObject()
            return Operations.hasOwnProperty(o, key).toValue()
        }

        @ECMAImpl("19.1.3.3")
        @JvmStatic
        fun isPrototypeOf(arguments: JSArguments): JSValue {
            var arg = arguments.argument(0)
            if (arg !is JSObject)
                return JSFalse
            val thisObj = arguments.thisValue.toObject()
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
        fun propertyIsEnumerable(arguments: JSArguments): JSValue {
            val key = arguments.argument(0).toPropertyKey()
            val thisObj = arguments.thisValue.toObject()
            val desc = thisObj.getOwnPropertyDescriptor(key) ?: return JSFalse
            return desc.isEnumerable.toValue()
        }

        @ECMAImpl("19.1.3.5")
        @JvmStatic
        fun toLocaleString(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()
            return Operations.invoke(thisObj, "toString".toValue())
        }

        @ECMAImpl("20.1.3.6")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            if (arguments.thisValue == JSUndefined)
                return "[object Undefined]".toValue()
            if (arguments.thisValue == JSNull)
                return "[object Null]".toValue()

            val obj = arguments.thisValue.toObject()
            val tag = obj.get(Realm.WellKnownSymbols.toStringTag).let {
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

        @JvmStatic
        fun valueOf(arguments: JSArguments): JSValue {
            return arguments.thisValue.toObject()
        }
    }
}
