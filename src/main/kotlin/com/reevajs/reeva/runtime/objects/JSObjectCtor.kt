package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.toValue

@Suppress("UNUSED_PARAMETER")
class JSObjectCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor", 1) {
    override fun init() {
        super.init()

        defineNativeFunction("assign", 2, ::assign)
        defineNativeFunction("create", 2, ::create)
        defineNativeFunction("defineProperties", 2, ::defineProperties)
        defineNativeFunction("defineProperty", 3, ::defineProperty)
        defineNativeFunction("entries", 1, ::entries)
        defineNativeFunction("freeze", 1, ::freeze)
        defineNativeFunction("fromEntries", 1, ::fromEntries)
        defineNativeFunction("getOwnPropertyDescriptor", 2, ::getOwnPropertyDescriptor)
        defineNativeFunction("getOwnPropertyDescriptors", 1, ::getOwnPropertyDescriptors)
        defineNativeFunction("getOwnPropertyNames", 1, ::getOwnPropertyNames)
        defineNativeFunction("getOwnPropertySymbols", 1, ::getOwnPropertySymbols)
        defineNativeFunction("getPrototypeOf", 1, ::getPrototypeOf)
        defineNativeFunction("is", 2, ::`is`)
        defineNativeFunction("isExtensible", 1, ::isExtensible)
        defineNativeFunction("isFrozen", 1, ::isFrozen)
        defineNativeFunction("isSealed", 1, ::isSealed)
        defineNativeFunction("preventExtensions", 1, ::preventExtensions)
        defineNativeFunction("seal", 1, ::seal)
        defineNativeFunction("setPrototypeOf", 2, ::setPrototypeOf)
        defineNativeFunction("values", 1, ::values)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val value = arguments.argument(0)
        val newTarget = arguments.newTarget
        // TODO: "If NewTarget is neither undefined nor the active function, then..."
        if (newTarget != JSUndefined /*&& newTarget != Agent.runningContext.function*/)
            return Operations.ordinaryCreateFromConstructor(realm, newTarget, realm.objectProto)
        if (value.isUndefined || value.isNull)
            return JSObject.create(realm)
        return Operations.toObject(realm, value)
    }

    fun assign(realm: Realm, arguments: JSArguments): JSValue {
        val target = Operations.toObject(realm, arguments.argument(0))
        if (arguments.size == 1)
            return target
        arguments.subList(1, arguments.size).forEach {
            if (it == JSUndefined || it == JSNull)
                return@forEach
            val from = Operations.toObject(realm, it)
            from.ownPropertyKeys().forEach { key ->
                val desc = from.getOwnPropertyDescriptor(key)
                if (desc != null && desc.isEnumerable) {
                    val value = from.get(key)
                    if (!target.set(key, value))
                        Errors.Object.AssignFailedSet(key).throwTypeError(realm)
                }
            }
        }

        return target
    }

    fun create(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject && obj != JSNull)
            Errors.Object.CreateBadArgType.throwTypeError(realm)
        val newObj = create(realm, obj)
        val properties = arguments.argument(1)
        if (properties != JSUndefined)
            objectDefineProperties(realm, newObj, properties)
        return newObj
    }

    fun defineProperties(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            Errors.Object.DefinePropertiesBadArgType.throwTypeError(realm)
        return objectDefineProperties(realm, obj, arguments.argument(1))
    }

    fun defineProperty(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            Errors.Object.DefinePropertyBadArgType.throwTypeError(realm)
        val key = Operations.toPropertyKey(realm, arguments.argument(1))
        val desc = Descriptor.fromObject(realm, arguments.argument(2))
        Operations.definePropertyOrThrow(realm, obj, key.asValue, desc)
        return obj
    }

    fun entries(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.argument(0))
        val names = Operations.enumerableOwnPropertyNames(realm, obj, PropertyKind.KeyValue)
        return Operations.createArrayFromList(realm, names)
    }

    fun freeze(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!Operations.setIntegrityLevel(realm, obj, Operations.IntegrityLevel.Frozen)) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.freeze").throwTypeError(realm)
        }
        return obj
    }

    fun fromEntries(realm: Realm, arguments: JSArguments): JSValue {
        val iterable = arguments.argument(0)
        Operations.requireObjectCoercible(realm, iterable)
        val obj = JSObject.create(realm)
        val adder = fromLambda(realm, "", 0) { r, args ->
            val key = args.argument(0)
            val value = args.argument(1)
            ecmaAssert(args.thisValue is JSObject)
            Operations.createDataPropertyOrThrow(r, args.thisValue, key, value)
            return@fromLambda JSUndefined
        }
        return Operations.addEntriesFromIterable(realm, obj, iterable, adder)
    }

    fun getOwnPropertyDescriptor(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.argument(0))
        val key = Operations.toPropertyKey(realm, arguments.argument(1))
        val desc = obj.getOwnPropertyDescriptor(key) ?: return JSUndefined
        return desc.toObject(realm, obj)
    }

    fun getOwnPropertyDescriptors(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.argument(0))
        val descriptors = JSObject.create(realm)
        obj.ownPropertyKeys().forEach { key ->
            val desc = obj.getOwnPropertyDescriptor(key)!!
            val descObj = desc.toObject(realm, obj)
            Operations.createDataPropertyOrThrow(realm, descriptors, key, descObj)
        }
        return descriptors
    }

    fun getOwnPropertyNames(realm: Realm, arguments: JSArguments): JSValue {
        return getOwnPropertyKeys(realm, arguments.argument(0), false)
    }

    fun getOwnPropertySymbols(realm: Realm, arguments: JSArguments): JSValue {
        return getOwnPropertyKeys(realm, arguments.argument(0), true)
    }

    fun getPrototypeOf(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.argument(0))
        return obj.getPrototype()
    }

    fun `is`(realm: Realm, arguments: JSArguments): JSValue {
        return arguments.argument(0).sameValue(arguments.argument(1)).toValue()
    }

    fun isExtensible(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSFalse
        return obj.isExtensible().toValue()
    }

    fun isFrozen(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSTrue
        return obj.isFrozen.toValue()
    }

    fun isSealed(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSTrue
        return obj.isSealed.toValue()
    }

    fun keys(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.argument(0))
        val nameList = Operations.enumerableOwnPropertyNames(realm, obj, PropertyKind.Key)
        return Operations.createArrayFromList(realm, nameList)
    }

    fun preventExtensions(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!obj.preventExtensions()) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.preventExtensions").throwTypeError(realm)
        }
        return obj
    }

    fun seal(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!Operations.setIntegrityLevel(realm, obj, Operations.IntegrityLevel.Sealed)) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.seal").throwTypeError(realm)
        }
        return obj
    }

    fun setPrototypeOf(realm: Realm, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        Operations.requireObjectCoercible(realm, obj)
        val proto = arguments.argument(1)
        if (proto !is JSObject && proto != JSNull)
            Errors.Object.SetPrototypeOfBadArgType.throwTypeError(realm)
        if (obj !is JSObject)
            return obj
        if (!obj.setPrototype(proto))
            Errors.TODO("Object.setPrototypeOf").throwTypeError(realm)
        return obj
    }

    fun values(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(realm, arguments.argument(0))
        val nameList = Operations.enumerableOwnPropertyNames(realm, obj, PropertyKind.Value)
        return Operations.createArrayFromList(realm, nameList)
    }

    fun getOwnPropertyKeys(realm: Realm, target: JSValue, isSymbols: Boolean): JSValue {
        val obj = Operations.toObject(realm, target)
        val keyList = mutableListOf<JSValue>()
        obj.ownPropertyKeys().forEach { key ->
            if (!key.isSymbol xor isSymbols)
                keyList.add(key.asValue)
        }
        return Operations.createArrayFromList(realm, keyList)
    }

    @ECMAImpl("19.1.2.3.1")
    private fun objectDefineProperties(realm: Realm, target: JSObject, properties: JSValue): JSObject {
        val props = Operations.toObject(realm, properties)
        val descriptors = mutableListOf<Pair<PropertyKey, Descriptor>>()
        props.ownPropertyKeys().forEach { key ->
            val propDesc = props.getOwnPropertyDescriptor(key)!!
            if (propDesc.isEnumerable) {
                val descObj = props.get(key)
                descriptors.add(key to Descriptor.fromObject(realm, descObj))
            }
        }
        descriptors.forEach { (key, descriptor) ->
            Operations.definePropertyOrThrow(realm, target, key.asValue, descriptor)
        }
        return target
    }

    companion object {
        fun create(realm: Realm) = JSObjectCtor(realm).initialize()
    }
}
