package me.mattco.reeva.runtime.values.builtins

import me.mattco.reeva.runtime.Agent.Companion.ifError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.throwError
import me.mattco.reeva.utils.toValue

class JSReflectObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineOwnProperty(Realm.`@@toStringTag`, "Reflect".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("apply", 3, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun apply(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, thisArg, argumentsList) = arguments.slice(0..2)

        if (!Operations.isCallable(target)) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }

        val args = Operations.createListFromArrayLike(argumentsList)
        ifError { return INVALID_VALUE }
        return Operations.call(target, thisArg, args)
    }

    @JSMethod("construct", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun construct(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, argumentsList) = arguments.slice(0..1)
        val newTarget = if (arguments.size <= 2) {
            target
        } else arguments.argument(2).also {
            if (!Operations.isConstructor(it)) {
                throwError<JSTypeErrorObject>("TODO: message")
                return INVALID_VALUE
            }
        }

        if (!Operations.isCallable(target)) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }

        val args = Operations.createListFromArrayLike(argumentsList)
        ifError { return INVALID_VALUE }
        return Operations.construct(target, args, newTarget)
    }

    @JSMethod("defineProperty", 3, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun defineProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey, attributes) = arguments.slice(0..2)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val key = Operations.toPropertyKey(propertyKey)
        ifError { return INVALID_VALUE }
        val desc = Descriptor.fromObject(attributes)
        return target.defineOwnProperty(key, desc).toValue()
    }

    @JSMethod("deleteProperty", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun deleteProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.slice(0..1)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val key = Operations.toPropertyKey(propertyKey)
        ifError { return INVALID_VALUE }
        return target.delete(key).toValue()
    }

    @JSMethod("get", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun get(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey, receiver) = arguments.slice(0..2)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val key = Operations.toPropertyKey(propertyKey)
        ifError { return INVALID_VALUE }
        return target.get(key, receiver.ifUndefined(target))
    }

    @JSMethod("getOwnPropertyDescriptor", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun getOwnPropertyDescriptor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.slice(0..1)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val key = Operations.toPropertyKey(propertyKey)
        ifError { return INVALID_VALUE }
        return target.getOwnPropertyDescriptor(key)?.toObject(realm, JSUndefined) ?: JSUndefined
    }

    @JSMethod("getPrototypeOf", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun getPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        return target.getPrototype()
    }

    @JSMethod("has", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun has(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey) = arguments.slice(0..1)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val key = Operations.toPropertyKey(propertyKey)
        ifError { return INVALID_VALUE }
        return target.hasProperty(key).toValue()
    }

    @JSMethod("isExtensible", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun isExtensible(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        return target.isExtensible().toValue()
    }

    @JSMethod("ownKeys", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun ownKeys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val keys = target.ownPropertyKeys()
        ifError { return INVALID_VALUE }
        return Operations.createArrayFromList(keys.map { it.asValue })
    }

    @JSMethod("preventExtensions", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun preventExtensions(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = arguments.argument(0)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        return target.preventExtensions().toValue()
    }

    @JSMethod("set", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun set(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, propertyKey, value, receiver) = arguments.slice(0..3)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        val key = Operations.toPropertyKey(propertyKey)
        ifError { return INVALID_VALUE }
        return target.set(key, value, receiver.ifUndefined(target)).toValue()
    }

    @JSMethod("setPrototypeOf", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun setPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, proto) = arguments.slice(0..1)
        if (target !is JSObject) {
            throwError<JSTypeErrorObject>("TODO: message")
            return INVALID_VALUE
        }
        if (proto !is JSObject && proto !is JSNull) {
            throwError<JSTypeErrorObject>("object prototype must be an object or null")
            return INVALID_VALUE
        }
        return target.setPrototype(proto).toValue()
    }

    companion object {
        fun create(realm: Realm) = JSReflectObject(realm).also { it.init() }
    }
}
