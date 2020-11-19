package me.mattco.reeva.runtime.objects

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

@Suppress("UNUSED_PARAMETER")
class JSObjectCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor", 1) {
    init {
        isConstructable = true
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val value = arguments.argument(0)
        val newTarget = super.newTarget
        if (newTarget != JSUndefined && newTarget != Agent.runningContext.function)
            return Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
        if (value.isUndefined || value.isNull)
            return JSObject.create(realm)
        return Operations.toObject(value)
    }

    @JSMethod("assign", 2)
    fun assign(thisValue: JSValue, arguments: JSArguments): JSValue {
        val target = Operations.toObject(arguments.argument(0))
        if (arguments.size == 1)
            return target
        arguments.subList(1, arguments.size).forEach {
            if (it == JSUndefined || it == JSNull)
                return@forEach
            val from = Operations.toObject(it)
            from.ownPropertyKeys().forEach { key ->
                val desc = from.getOwnPropertyDescriptor(key)
                if (desc != null && desc.isEnumerable) {
                    val value = from.get(key)
                    if (!target.set(key, value))
                        Errors.Object.AssignFailedSet(key).throwTypeError()
                }
            }
        }

        return target
    }

    @JSMethod("create", 2)
    fun create(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject && obj != JSNull)
            Errors.Object.CreateBadArgType.throwTypeError()
        val newObj = create(Agent.runningContext.realm, obj)
        val properties = arguments.argument(1)
        if (properties != JSUndefined)
            objectDefineProperties(newObj, properties)
        return newObj
    }

    @JSMethod("defineProperties", 2)
    fun defineProperties(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            Errors.Object.DefinePropertiesBadArgType.throwTypeError()
        return objectDefineProperties(obj, arguments.argument(1))
    }

    @JSMethod("defineProperty", 3)
    fun defineProperty(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            Errors.Object.DefinePropertyBadArgType.throwTypeError()
        val key = Operations.toPropertyKey(arguments.argument(1))
        val desc = Descriptor.fromObject(arguments.argument(2))
        Operations.definePropertyOrThrow(obj, key.asValue, desc)
        return obj
    }

    @JSMethod("entries", 1)
    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val names = Operations.enumerableOwnPropertyNames(obj, PropertyKind.KeyValue)
        return Operations.createArrayFromList(names)
    }

    @JSMethod("freeze", 1)
    fun freeze(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!Operations.setIntegrityLevel(obj, Operations.IntegrityLevel.Frozen)) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.freeze").throwTypeError()
        }
        return obj
    }

    @JSMethod("fromEntries", 1)
    fun fromEntries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val iterable = arguments.argument(0)
        Operations.requireObjectCoercible(iterable)
        val obj = JSObject.create(realm)
        val adder = fromLambda(realm, "", 0) { tv, args ->
            val key = args.argument(0)
            val value = args.argument(1)
            ecmaAssert(tv is JSObject)
            Operations.createDataPropertyOrThrow(tv, key, value)
            return@fromLambda JSUndefined
        }
        return Operations.addEntriesFromIterable(obj, iterable, adder)
    }

    @JSMethod("getOwnPropertyDescriptor", 2)
    fun getOwnPropertyDescriptor(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val key = Operations.toPropertyKey(arguments.argument(1))
        val desc = obj.getOwnPropertyDescriptor(key) ?: return JSUndefined
        return desc.toObject(realm, obj)
    }

    @JSMethod("getOwnPropertyDescriptors", 1)
    fun getOwnPropertyDescriptors(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val descriptors = JSObject.create(realm)
        obj.ownPropertyKeys().forEach { key ->
            val desc = obj.getOwnPropertyDescriptor(key)!!
            val descObj = desc.toObject(realm, obj)
            Operations.createDataPropertyOrThrow(descriptors, key, descObj)
        }
        return descriptors
    }

    @JSMethod("getOwnPropertyNames", 1)
    fun getOwnPropertyNames(thisValue: JSValue, arguments: JSArguments): JSValue {
        return getOwnPropertyKeys(arguments.argument(0), false)
    }

    @JSMethod("getOwnPropertySymbols", 1)
    fun getOwnPropertySymbols(thisValue: JSValue, arguments: JSArguments): JSValue {
        return getOwnPropertyKeys(arguments.argument(0), true)
    }

    @JSMethod("getPrototypeOf", 0)
    fun getPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        return obj.getPrototype()
    }

    @JSMethod("is", 1)
    fun `is`(thisValue: JSValue, arguments: JSArguments): JSValue {
        return arguments.argument(0).sameValue(arguments.argument(1)).toValue()
    }

    @JSMethod("isExtensible", 1)
    fun isExtensible(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSFalse
        return obj.isExtensible().toValue()
    }

    @JSMethod("isFrozen", 1)
    fun isFrozen(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSTrue
        return obj.isFrozen.toValue()
    }

    @JSMethod("isSealed", 1)
    fun isSealed(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSTrue
        return obj.isSealed.toValue()
    }

    @JSMethod("keys", 1)
    fun keys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val nameList = Operations.enumerableOwnPropertyNames(obj, PropertyKind.Key)
        return Operations.createArrayFromList(nameList)
    }

    @JSMethod("preventExtensions", 1)
    fun preventExtensions(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!obj.preventExtensions()) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.preventExtensions").throwTypeError()
        }
        return obj
    }

    @JSMethod("seal", 1)
    fun seal(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!Operations.setIntegrityLevel(obj, Operations.IntegrityLevel.Sealed)) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.seal").throwTypeError()
        }
        return obj
    }

    @JSMethod("setPrototypeOf", 2)
    fun setPrototypeOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        Operations.requireObjectCoercible(obj)
        val proto = arguments.argument(1)
        if (proto !is JSObject && proto != JSNull)
            Errors.Object.SetPrototypeOfBadArgType.throwTypeError()
        if (obj !is JSObject)
            return obj
        if (!obj.setPrototype(proto))
            Errors.TODO("Object.setPrototypeOf").throwTypeError()
        return obj
    }

    @JSMethod("values", 0)
    fun values(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val nameList = Operations.enumerableOwnPropertyNames(obj, JSObject.PropertyKind.Value)
        return Operations.createArrayFromList(nameList)
    }

    private fun getOwnPropertyKeys(target: JSValue, isSymbols: Boolean): JSValue {
        val obj = Operations.toObject(target)
        val keyList = mutableListOf<JSValue>()
        obj.ownPropertyKeys().forEach { key ->
            if (!key.isSymbol xor isSymbols)
                keyList.add(key.asValue)
        }
        return Operations.createArrayFromList(keyList)
    }

    @ECMAImpl("19.1.2.3.1")
    private fun objectDefineProperties(target: JSObject, properties: JSValue): JSObject {
        val props = Operations.toObject(properties)
        val descriptors = mutableListOf<Pair<PropertyKey, Descriptor>>()
        props.ownPropertyKeys().forEach { key ->
            val propDesc = props.getOwnPropertyDescriptor(key)!!
            if (propDesc.isEnumerable) {
                val descObj = props.get(key)
                descriptors.add(key to Descriptor.fromObject(descObj))
            }
        }
        descriptors.forEach { (key, descriptor) ->
            Operations.definePropertyOrThrow(target, key.asValue, descriptor)
        }
        return target
    }

    companion object {
        fun create(realm: Realm) = JSObjectCtor(realm).initialize()
    }
}
