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

    fun apply(realm: Realm, arguments: JSArguments): JSValue {
        val (target, thisArg, argumentsList) = arguments.takeArgs(0..2)

        if (!Operations.isCallable(target))
            Errors.NotCallable(Operations.toPrintableString(target)).throwTypeError(realm)

        val args = Operations.createListFromArrayLike(realm, argumentsList)
        return Operations.call(realm, target, thisArg, args)
    }

    fun construct(realm: Realm, arguments: JSArguments): JSValue {
        val (target, argumentsList) = arguments.takeArgs(0..1)
        val newTarget = if (arguments.size <= 2) {
            target
        } else arguments.argument(2).also {
            if (!Operations.isConstructor(it))
                Errors.NotACtor(Operations.toPrintableString(it)).throwTypeError(realm)
        }

        if (!Operations.isCallable(target))
            Errors.NotCallable(Operations.toPrintableString(target)).throwTypeError(realm)

        val args = Operations.createListFromArrayLike(realm, argumentsList)
        return Operations.construct(target, args, newTarget)
    }

    fun defineProperty(realm: Realm, arguments: JSArguments): JSValue {
        val (target, propertyKey, attributes) = arguments.takeArgs(0..2)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("defineProperty").throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, propertyKey)
        val desc = Descriptor.fromObject(realm, attributes)
        return target.defineOwnProperty(key, desc).toValue()
    }

    fun deleteProperty(realm: Realm, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("deleteProperty").throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, propertyKey)
        return target.delete(key).toValue()
    }

    fun get(realm: Realm, arguments: JSArguments): JSValue {
        val (target, propertyKey, receiver) = arguments.takeArgs(0..2)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("get").throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, propertyKey)
        return target.get(key, receiver.ifUndefined(target))
    }

    fun getOwnPropertyDescriptor(realm: Realm, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("getOwnPropertyDescriptor").throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, propertyKey)
        return target.getOwnPropertyDescriptor(key)?.toObject(realm, JSUndefined) ?: JSUndefined
    }

    fun getPrototypeOf(realm: Realm, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("getPrototypeOf").throwTypeError(realm)
        return target.getPrototype()
    }

    fun has(realm: Realm, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("has").throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, propertyKey)
        return target.hasProperty(key).toValue()
    }

    fun isExtensible(realm: Realm, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("isExtensible").throwTypeError(realm)
        return target.isExtensible().toValue()
    }

    fun ownKeys(realm: Realm, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("ownKeys").throwTypeError(realm)
        val keys = target.ownPropertyKeys()
        return Operations.createArrayFromList(realm, keys.map { it.asValue })
    }

    fun preventExtensions(realm: Realm, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("preventExtensions").throwTypeError(realm)
        return target.preventExtensions().toValue()
    }

    fun set(realm: Realm, arguments: JSArguments): JSValue {
        val (target, propertyKey, value, receiver) = arguments.takeArgs(0..3)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("set").throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, propertyKey)
        return target.set(key, value, receiver.ifUndefined(target)).toValue()
    }

    fun setPrototypeOf(realm: Realm, arguments: JSArguments): JSValue {
        val (target, proto) = arguments.takeArgs(0..1)
        if (target !is JSObject)
            Errors.Reflect.FirstArgNotCallable("setPrototypeOf").throwTypeError(realm)
        if (proto !is JSObject && proto != JSNull)
            Errors.Reflect.BadProto.throwTypeError(realm)
        return target.setPrototype(proto).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSReflectObject(realm).initialize()
    }
}
