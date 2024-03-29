package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.functions.JSRunnableFunction
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.toValue

class JSObjectCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ObjectConstructor", 1) {
    override fun init() {
        super.init()

        defineBuiltin("assign", 2, ::assign)
        defineBuiltin("create", 2, ::create_)
        defineBuiltin("defineProperties", 2, ::defineProperties)
        defineBuiltin("defineProperty", 3, ::defineProperty)
        defineBuiltin("entries", 1, ::entries)
        defineBuiltin("freeze", 1, ::freeze)
        defineBuiltin("fromEntries", 1, ::fromEntries)
        defineBuiltin("getOwnPropertyDescriptor", 2, ::getOwnPropertyDescriptor)
        defineBuiltin("getOwnPropertyDescriptors", 1, ::getOwnPropertyDescriptors)
        defineBuiltin("getOwnPropertyNames", 1, ::getOwnPropertyNames)
        defineBuiltin("getOwnPropertySymbols", 1, ::getOwnPropertySymbols)
        defineBuiltin("getPrototypeOf", 1, ::getPrototypeOf)
        defineBuiltin("is", 2, ::is_)
        defineBuiltin("isExtensible", 1, ::isExtensible)
        defineBuiltin("isFrozen", 1, ::isFrozen)
        defineBuiltin("isSealed", 1, ::isSealed)
        defineBuiltin("keys", 1, ::keys)
        defineBuiltin("preventExtensions", 1, ::preventExtensions)
        defineBuiltin("seal", 1, ::seal)
        defineBuiltin("setPrototypeOf", 2, ::setPrototypeOf)
        defineBuiltin("values", 1, ::values)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val agent = Agent.activeAgent

        val value = arguments.argument(0)
        val newTarget = arguments.newTarget
        // TODO: "If NewTarget is neither undefined nor the active function, then..."
        if (newTarget != JSUndefined && newTarget != agent.getActiveFunction())
            return AOs.ordinaryCreateFromConstructor(newTarget, defaultProto = Realm::objectProto)
        if (value.isUndefined || value.isNull)
            return JSObject.create()
        return value.toObject()
    }

    companion object {
        // Special object: do not initialize
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSObjectCtor(realm)

        @ECMAImpl("20.1.2.1")
        @JvmStatic
        fun assign(arguments: JSArguments): JSValue {
            val target = arguments.argument(0).toObject()
            if (arguments.size == 1)
                return target
            arguments.subList(1, arguments.size).forEach {
                if (it == JSUndefined || it == JSNull)
                    return@forEach
                val from = it.toObject()
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

        @ECMAImpl("20.1.2.2")
        @JvmStatic
        fun create_(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject && obj != JSNull)
                Errors.Object.CreateBadArgType.throwTypeError()
            val newObj = create(proto = obj)
            val properties = arguments.argument(1)
            if (properties != JSUndefined)
                objectDefineProperties(newObj, properties)
            return newObj
        }

        @ECMAImpl("20.1.2.3")
        @JvmStatic
        fun defineProperties(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                Errors.Object.DefinePropertiesBadArgType.throwTypeError()
            return objectDefineProperties(obj, arguments.argument(1))
        }

        @ECMAImpl("20.1.2.4")
        @JvmStatic
        fun defineProperty(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                Errors.Object.DefinePropertyBadArgType.throwTypeError()
            val key = arguments.argument(1).toPropertyKey()
            val desc = Descriptor.fromObject(arguments.argument(2))
            AOs.definePropertyOrThrow(obj, key.asValue, desc)
            return obj
        }

        @ECMAImpl("20.1.2.5")
        @JvmStatic
        fun entries(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).toObject()
            val names = AOs.enumerableOwnPropertyNames(obj, PropertyKind.KeyValue)
            return AOs.createArrayFromList(names)
        }

        @ECMAImpl("20.1.2.6")
        @JvmStatic
        fun freeze(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return obj
            if (!AOs.setIntegrityLevel(obj, AOs.IntegrityLevel.Frozen)) {
                // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
                Errors.TODO("Object.freeze").throwTypeError()
            }
            return obj
        }

        @ECMAImpl("20.1.2.7")
        @JvmStatic
        fun fromEntries(arguments: JSArguments): JSValue {
            val iterable = arguments.argument(0).requireObjectCoercible()
            val obj = JSObject.create()
            val adder = JSRunnableFunction.create("", 0) { args ->
                val key = args.argument(0)
                val value = args.argument(1)
                ecmaAssert(args.thisValue is JSObject)
                AOs.createDataPropertyOrThrow(args.thisValue, key, value)
                JSUndefined
            }
            return AOs.addEntriesFromIterable( obj, iterable, adder)
        }

        @ECMAImpl("20.1.2.8")
        @JvmStatic
        fun getOwnPropertyDescriptor(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).toObject()
            val key = arguments.argument(1).toPropertyKey()
            val desc = obj.getOwnPropertyDescriptor(key) ?: return JSUndefined
            return desc.toObject(obj)
        }

        @ECMAImpl("20.1.2.9")
        @JvmStatic
        fun getOwnPropertyDescriptors(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).toObject()
            val descriptors = JSObject.create()
            obj.ownPropertyKeys().forEach { key ->
                val desc = obj.getOwnPropertyDescriptor(key)!!
                val descObj = desc.toObject(obj)
                AOs.createDataPropertyOrThrow(descriptors, key, descObj)
            }
            return descriptors
        }

        @ECMAImpl("20.1.2.10")
        @JvmStatic
        fun getOwnPropertyNames(arguments: JSArguments): JSValue {
            return getOwnPropertyKeys(arguments.argument(0), false)
        }

        @ECMAImpl("20.1.2.11")
        @JvmStatic
        fun getOwnPropertySymbols(arguments: JSArguments): JSValue {
            return getOwnPropertyKeys(arguments.argument(0), true)
        }

        @ECMAImpl("20.1.2.12")
        @JvmStatic
        fun getPrototypeOf(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).toObject()
            return obj.getPrototype()
        }

        @ECMAImpl("20.1.2.13")
        @JvmStatic
        fun is_(arguments: JSArguments): JSValue {
            return arguments.argument(0).sameValue(arguments.argument(1)).toValue()
        }

        @ECMAImpl("20.1.2.14")
        @JvmStatic
        fun isExtensible(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return JSFalse
            return obj.isExtensible().toValue()
        }

        @ECMAImpl("20.1.2.15")
        @JvmStatic
        fun isFrozen(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return JSTrue
            return obj.isFrozen.toValue()
        }

        @ECMAImpl("20.1.2.16")
        @JvmStatic
        fun isSealed(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return JSTrue
            return obj.isSealed.toValue()
        }

        @ECMAImpl("20.1.2.17")
        @JvmStatic
        fun keys(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).toObject()
            val nameList = AOs.enumerableOwnPropertyNames(obj, PropertyKind.Key)
            return AOs.createArrayFromList(nameList)
        }

        @ECMAImpl("20.1.2.18")
        @JvmStatic
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

        @ECMAImpl("20.1.2.20")
        @JvmStatic
        fun seal(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return obj
            if (!AOs.setIntegrityLevel(obj, AOs.IntegrityLevel.Sealed)) {
                // TODO: spidermonkey throws this error in the Proxy preventExtensions handler
                Errors.TODO("Object.seal").throwTypeError()
            }
            return obj
        }

        @ECMAImpl("20.1.2.21")
        @JvmStatic
        fun setPrototypeOf(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).requireObjectCoercible()
            val proto = arguments.argument(1)
            if (proto !is JSObject && proto != JSNull)
                Errors.Object.SetPrototypeOfBadArgType.throwTypeError()
            if (obj !is JSObject)
                return obj
            if (!obj.setPrototype(proto))
                Errors.TODO("Object.setPrototypeOf").throwTypeError()
            return obj
        }

        @ECMAImpl("20.1.2.22")
        @JvmStatic
        fun values(arguments: JSArguments): JSValue {
            val obj = arguments.argument(0).toObject()
            val nameList = AOs.enumerableOwnPropertyNames(obj, PropertyKind.Value)
            return AOs.createArrayFromList(nameList)
        }

        private fun getOwnPropertyKeys(target: JSValue, isSymbols: Boolean): JSValue {
            val obj = target.toObject()
            val keyList = mutableListOf<JSValue>()
            obj.ownPropertyKeys().forEach { key ->
                if (!key.isSymbol xor isSymbols)
                    keyList.add(key.asValue)
            }
            return AOs.createArrayFromList(keyList)
        }

        @ECMAImpl("19.1.2.3.1")
        private fun objectDefineProperties(target: JSObject, properties: JSValue): JSObject {
            val props = properties.toObject()
            val descriptors = mutableListOf<Pair<PropertyKey, Descriptor>>()
            props.ownPropertyKeys().forEach { key ->
                val propDesc = props.getOwnPropertyDescriptor(key)!!
                if (propDesc.isEnumerable) {
                    val descObj = props.get(key)
                    descriptors.add(key to Descriptor.fromObject(descObj))
                }
            }
            descriptors.forEach { (key, descriptor) ->
                AOs.definePropertyOrThrow(target, key.asValue, descriptor)
            }
            return target
        }
    }
}
