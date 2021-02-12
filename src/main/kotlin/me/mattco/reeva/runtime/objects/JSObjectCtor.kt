package me.mattco.reeva.runtime.objects

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.ECMAImpl
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
            return Operations.ordinaryCreateFromConstructor(newTarget, realm.objectProto)
        if (value.isUndefined || value.isNull)
            return JSObject.create(realm)
        return Operations.toObject(value)
    }

    fun assign(arguments: JSArguments): JSValue {
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

    fun create(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject && obj != JSNull)
            Errors.Object.CreateBadArgType.throwTypeError()
        val newObj = create(Reeva.activeAgent.runningContext.realm, obj)
        val properties = arguments.argument(1)
        if (properties != JSUndefined)
            objectDefineProperties(newObj, properties)
        return newObj
    }

    fun defineProperties(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            Errors.Object.DefinePropertiesBadArgType.throwTypeError()
        return objectDefineProperties(obj, arguments.argument(1))
    }

    fun defineProperty(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            Errors.Object.DefinePropertyBadArgType.throwTypeError()
        val key = Operations.toPropertyKey(arguments.argument(1))
        val desc = Descriptor.fromObject(arguments.argument(2))
        Operations.definePropertyOrThrow(obj, key.asValue, desc)
        return obj
    }

    fun entries(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val names = Operations.enumerableOwnPropertyNames(obj, PropertyKind.KeyValue)
        return Operations.createArrayFromList(names)
    }

    fun freeze(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!Operations.setIntegrityLevel(obj, Operations.IntegrityLevel.Frozen)) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.freeze").throwTypeError()
        }
        return obj
    }

    fun fromEntries(arguments: JSArguments): JSValue {
        val iterable = arguments.argument(0)
        Operations.requireObjectCoercible(iterable)
        val obj = JSObject.create(realm)
        val adder = fromLambda(realm, "", 0) { args ->
            val key = args.argument(0)
            val value = args.argument(1)
            ecmaAssert(args.thisValue is JSObject)
            Operations.createDataPropertyOrThrow(args.thisValue, key, value)
            return@fromLambda JSUndefined
        }
        return Operations.addEntriesFromIterable(obj, iterable, adder)
    }

    fun getOwnPropertyDescriptor(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val key = Operations.toPropertyKey(arguments.argument(1))
        val desc = obj.getOwnPropertyDescriptor(key) ?: return JSUndefined
        return desc.toObject(realm, obj)
    }

    fun getOwnPropertyDescriptors(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val descriptors = JSObject.create(realm)
        obj.ownPropertyKeys().forEach { key ->
            val desc = obj.getOwnPropertyDescriptor(key)!!
            val descObj = desc.toObject(realm, obj)
            Operations.createDataPropertyOrThrow(descriptors, key, descObj)
        }
        return descriptors
    }

    fun getOwnPropertyNames(arguments: JSArguments): JSValue {
        return getOwnPropertyKeys(arguments.argument(0), false)
    }

    fun getOwnPropertySymbols(arguments: JSArguments): JSValue {
        return getOwnPropertyKeys(arguments.argument(0), true)
    }

    fun getPrototypeOf(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        return obj.getPrototype()
    }

    fun `is`(arguments: JSArguments): JSValue {
        return arguments.argument(0).sameValue(arguments.argument(1)).toValue()
    }

    fun isExtensible(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSFalse
        return obj.isExtensible().toValue()
    }

    fun isFrozen(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSTrue
        return obj.isFrozen.toValue()
    }

    fun isSealed(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return JSTrue
        return obj.isSealed.toValue()
    }

    fun keys(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val nameList = Operations.enumerableOwnPropertyNames(obj, PropertyKind.Key)
        return Operations.createArrayFromList(nameList)
    }

    fun preventExtensions(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!obj.preventExtensions()) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.preventExtensions").throwTypeError()
        }
        return obj
    }

    fun seal(arguments: JSArguments): JSValue {
        val obj = arguments.argument(0)
        if (obj !is JSObject)
            return obj
        if (!Operations.setIntegrityLevel(obj, Operations.IntegrityLevel.Sealed)) {
            // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
            Errors.TODO("Object.seal").throwTypeError()
        }
        return obj
    }

    fun setPrototypeOf(arguments: JSArguments): JSValue {
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

    fun values(arguments: JSArguments): JSValue {
        val obj = Operations.toObject(arguments.argument(0))
        val nameList = Operations.enumerableOwnPropertyNames(obj, JSObject.PropertyKind.Value)
        return Operations.createArrayFromList(nameList)
    }

    fun getOwnPropertyKeys(target: JSValue, isSymbols: Boolean): JSValue {
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
