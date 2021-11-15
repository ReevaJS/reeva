package com.reevajs.reeva.runtime.singletons

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toPropertyKey
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSReflectObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Reflect".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("apply", 3, ReevaBuiltin.ReflectApply)
        defineBuiltin("construct", 2, ReevaBuiltin.ReflectConstruct)
        defineBuiltin("defineProperty", 3, ReevaBuiltin.ReflectDefineProperty)
        defineBuiltin("deleteProperty", 2, ReevaBuiltin.ReflectDeleteProperty)
        defineBuiltin("get", 2, ReevaBuiltin.ReflectGet)
        defineBuiltin("getOwnPropertyDescriptor", 2, ReevaBuiltin.ReflectGetOwnPropertyDescriptor)
        defineBuiltin("has", 2, ReevaBuiltin.ReflectHas)
        defineBuiltin("isExtensible", 1, ReevaBuiltin.ReflectIsExtensible)
        defineBuiltin("ownKeys", 1, ReevaBuiltin.ReflectOwnKeys)
        defineBuiltin("preventExtensions", 1, ReevaBuiltin.ReflectPreventExtensions)
        defineBuiltin("set", 2, ReevaBuiltin.ReflectSet)
        defineBuiltin("setPrototypeOf", 2, ReevaBuiltin.ReflectSetPrototypeOf)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSReflectObject(realm).initialize()

        @ECMAImpl("28.1.1")
        @JvmStatic
        fun apply(arguments: JSArguments): JSValue {
            val (target, thisArg, argumentsList) = arguments.takeArgs(0..2)

            if (!Operations.isCallable(target))
                Errors.NotCallable(target.toString()).throwTypeError()

            val args = Operations.createListFromArrayLike(argumentsList)
            return Operations.call(target, thisArg, args)
        }

        @ECMAImpl("28.1.2")
        @JvmStatic
        fun construct(arguments: JSArguments): JSValue {
            val (target, argumentsList) = arguments.takeArgs(0..1)
            val newTarget = if (arguments.size <= 2) {
                target
            } else arguments.argument(2).also {
                if (!Operations.isConstructor(it))
                    Errors.NotACtor(it.toString()).throwTypeError()
            }

            if (!Operations.isCallable(target))
                Errors.NotCallable(target.toString()).throwTypeError()

            val args = Operations.createListFromArrayLike(argumentsList)
            return Operations.construct(target, args, newTarget)
        }

        @ECMAImpl("28.1.3")
        @JvmStatic
        fun defineProperty(arguments: JSArguments): JSValue {
            val (target, propertyKey, attributes) = arguments.takeArgs(0..2)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("defineProperty").throwTypeError()
            val key = propertyKey.toPropertyKey()
            val desc = Descriptor.fromObject(attributes)
            return target.defineOwnProperty(key, desc).toValue()
        }

        @ECMAImpl("28.1.4")
        @JvmStatic
        fun deleteProperty(arguments: JSArguments): JSValue {
            val (target, propertyKey) = arguments.takeArgs(0..1)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("deleteProperty").throwTypeError()
            val key = propertyKey.toPropertyKey()
            return target.delete(key).toValue()
        }

        @ECMAImpl("28.1.5")
        @JvmStatic
        fun get(arguments: JSArguments): JSValue {
            val (target, propertyKey, receiver) = arguments.takeArgs(0..2)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("get").throwTypeError()
            val key = propertyKey.toPropertyKey()
            return target.get(key, receiver.ifUndefined(target))
        }

        @ECMAImpl("28.1.6")
        @JvmStatic
        fun getOwnPropertyDescriptor(arguments: JSArguments): JSValue {
            val (target, propertyKey) = arguments.takeArgs(0..1)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("getOwnPropertyDescriptor").throwTypeError()
            val key = propertyKey.toPropertyKey()
            return target.getOwnPropertyDescriptor(key)?.toObject(JSUndefined) ?: JSUndefined
        }

        @ECMAImpl("28.1.7")
        @JvmStatic
        fun getPrototypeOf(arguments: JSArguments): JSValue {
            val target = arguments.argument(0)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("getPrototypeOf").throwTypeError()
            return target.getPrototype()
        }

        @ECMAImpl("28.1.8")
        @JvmStatic
        fun has(arguments: JSArguments): JSValue {
            val (target, propertyKey) = arguments.takeArgs(0..1)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("has").throwTypeError()
            val key = propertyKey.toPropertyKey()
            return target.hasProperty(key).toValue()
        }

        @ECMAImpl("28.1.9")
        @JvmStatic
        fun isExtensible(arguments: JSArguments): JSValue {
            val target = arguments.argument(0)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("isExtensible").throwTypeError()
            return target.isExtensible().toValue()
        }

        @ECMAImpl("28.1.10")
        @JvmStatic
        fun ownKeys(arguments: JSArguments): JSValue {
            val target = arguments.argument(0)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("ownKeys").throwTypeError()
            val keys = target.ownPropertyKeys()
            return Operations.createArrayFromList(keys.map { it.asValue })
        }

        @ECMAImpl("28.1.11")
        @JvmStatic
        fun preventExtensions(arguments: JSArguments): JSValue {
            val target = arguments.argument(0)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("preventExtensions").throwTypeError()
            return target.preventExtensions().toValue()
        }

        @ECMAImpl("28.1.12")
        @JvmStatic
        fun set(arguments: JSArguments): JSValue {
            val (target, propertyKey, value, receiver) = arguments.takeArgs(0..3)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("set").throwTypeError()
            val key = propertyKey.toPropertyKey()
            return target.set(key, value, receiver.ifUndefined(target)).toValue()
        }

        @ECMAImpl("28.1.13")
        @JvmStatic
        fun setPrototypeOf(arguments: JSArguments): JSValue {
            val (target, proto) = arguments.takeArgs(0..1)
            if (target !is JSObject)
                Errors.Reflect.FirstArgNotCallable("setPrototypeOf").throwTypeError()
            if (proto !is JSObject && proto != JSNull)
                Errors.Reflect.BadProto.throwTypeError()
            return target.setPrototype(proto).toValue()
        }
    }
}
