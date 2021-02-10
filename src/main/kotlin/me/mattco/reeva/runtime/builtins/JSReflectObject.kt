package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

class JSReflectObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Reflect".toValue(), Descriptor.CONFIGURABLE)
        defineNativeFunction("apply", 3, ::apply)
        defineNativeFunction("construct", 2, ::construct)
        defineNativeFunction("defineProperty", 3, ::defineProperty)
        defineNativeFunction("deleteProperty", 2, ::deleteProperty)
        defineNativeFunction("get", 2, ::get)
        defineNativeFunction("getOwnPropertyDescriptor", 2, ::getOwnPropertyDescriptor)
        defineNativeFunction("has", 2, ::has)
        defineNativeFunction("isExtensible", 1, ::isExtensible)
        defineNativeFunction("ownKeys", 1, ::ownKeys)
        defineNativeFunction("preventExtensions", 1, ::preventExtensions)
        defineNativeFunction("set", 2, ::set)
        defineNativeFunction("setPrototypeOf", 2, ::setPrototypeOf)
    }

    fun apply(arguments: JSArguments): JSValue {
        val (target, thisArg, argumentsList) = arguments.takeArgs(0..2)

        if (!Operations.isCallable(target))
            Errors.NotCallable(Operations.toPrintableString(target)).throwTypeError()

        val args = Operations.createListFromArrayLike(argumentsList)
        return Operations.call(target, thisArg, args)
    }

    fun construct(arguments: JSArguments): JSValue {
        val (target, argumentsList) = arguments.takeArgs(0..1)
        val newTarget = if (arguments.size <= 2) {
            target
        } else arguments.argument(2).also {
            if (!Operations.isConstructor(it))
                Errors.NotACtor(Operations.toPrintableString(it)).throwTypeError()
        }

        if (!Operations.isCallable(target))
            Errors.NotCallable(Operations.toPrintableString(target)).throwTypeError()

        val args = Operations.createListFromArrayLike(argumentsList)
        return Operations.construct(target, args, newTarget)
    }

    fun defineProperty(arguments: JSArguments): JSValue {
        val (target, propertyKey, attributes) = arguments.takeArgs(0..2)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("defineProperty").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        val desc = Descriptor.fromObject(attributes)
        return target.defineOwnProperty(key, desc).toValue()
    }

    fun deleteProperty(arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("deleteProperty").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.delete(key).toValue()
    }

    fun get(arguments: JSArguments): JSValue {
        val (target, propertyKey, receiver) = arguments.takeArgs(0..2)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("get").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.get(key, receiver.ifUndefined(target))
    }

    fun getOwnPropertyDescriptor(arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("getOwnPropertyDescriptor").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.getOwnPropertyDescriptor(key)?.toObject(realm, JSUndefined) ?: JSUndefined
    }

    fun getPrototypeOf(arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("getPrototypeOf").throwTypeError()
        return target.getPrototype()
    }

    fun has(arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("has").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.hasProperty(key).toValue()
    }

    fun isExtensible(arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("isExtensible").throwTypeError()
        return target.isExtensible().toValue()
    }

    fun ownKeys(arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("ownKeys").throwTypeError()
        val keys = target.ownPropertyKeys()
        return Operations.createArrayFromList(keys.map { it.asValue })
    }

    fun preventExtensions(arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("preventExtensions").throwTypeError()
        return target.preventExtensions().toValue()
    }

    fun set(arguments: JSArguments): JSValue {
        val (target, propertyKey, value, receiver) = arguments.takeArgs(0..3)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("set").throwTypeError()
        val key = Operations.toPropertyKey(propertyKey)
        return target.set(key, value, receiver.ifUndefined(target)).toValue()
    }

    fun setPrototypeOf(arguments: JSArguments): JSValue {
        val (target, proto) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("setPrototypeOf").throwTypeError()
        if (proto !is JSObject && proto != JSNull)
            Errors.Reflect.BadProto.throwTypeError()
        return target.setPrototype(proto).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSReflectObject(realm).initialize()
    }
}
