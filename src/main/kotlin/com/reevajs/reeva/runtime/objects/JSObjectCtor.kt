package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
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

        defineBuiltin("assign", 2, ReevaBuiltin.ObjectCtorAssign)
        defineBuiltin("create", 2, ReevaBuiltin.ObjectCtorCreate)
        defineBuiltin("defineProperties", 2, ReevaBuiltin.ObjectCtorDefineProperties)
        defineBuiltin("defineProperty", 3, ReevaBuiltin.ObjectCtorDefineProperty)
        defineBuiltin("entries", 1, ReevaBuiltin.ObjectCtorEntries)
        defineBuiltin("freeze", 1, ReevaBuiltin.ObjectCtorFreeze)
        defineBuiltin("fromEntries", 1, ReevaBuiltin.ObjectCtorFromEntries)
        defineBuiltin("getOwnPropertyDescriptor", 2, ReevaBuiltin.ObjectCtorGetOwnPropertyDescriptor)
        defineBuiltin("getOwnPropertyDescriptors", 1, ReevaBuiltin.ObjectCtorGetOwnPropertyDescriptors)
        defineBuiltin("getOwnPropertyNames", 1, ReevaBuiltin.ObjectCtorGetOwnPropertyNames)
        defineBuiltin("getOwnPropertySymbols", 1, ReevaBuiltin.ObjectCtorGetOwnPropertySymbols)
        defineBuiltin("getPrototypeOf", 1, ReevaBuiltin.ObjectCtorGetPrototypeOf)
        defineBuiltin("is", 2, ReevaBuiltin.ObjectCtorIs)
        defineBuiltin("isExtensible", 1, ReevaBuiltin.ObjectCtorIsExtensible)
        defineBuiltin("isFrozen", 1, ReevaBuiltin.ObjectCtorIsFrozen)
        defineBuiltin("isSealed", 1, ReevaBuiltin.ObjectCtorIsSealed)
        defineBuiltin("keys", 1, ReevaBuiltin.ObjectCtorKeys)
        defineBuiltin("preventExtensions", 1, ReevaBuiltin.ObjectCtorPreventExtensions)
        defineBuiltin("seal", 1, ReevaBuiltin.ObjectCtorSeal)
        defineBuiltin("setPrototypeOf", 2, ReevaBuiltin.ObjectCtorSetPrototypeOf)
        defineBuiltin("values", 1, ReevaBuiltin.ObjectCtorValues)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val value = arguments.argument(0)
        val newTarget = arguments.newTarget
        // TODO: "If NewTarget is neither undefined nor the active function, then..."
        if (newTarget != JSUndefined && newTarget != Reeva.activeAgent.activeFunction)
            return Operations.ordinaryCreateFromConstructor(realm, newTarget, realm.objectProto)
        if (value.isUndefined || value.isNull)
            return JSObject.create(realm)
        return Operations.toObject(realm, value)
    }

    companion object {
        fun create(realm: Realm) = JSObjectCtor(realm).initialize()

        @ECMAImpl("20.1.2.1")
        @JvmStatic
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

        @ECMAImpl("20.1.2.2")
        @JvmStatic
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

        @ECMAImpl("20.1.2.3")
        @JvmStatic
        fun defineProperties(realm: Realm, arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                Errors.Object.DefinePropertiesBadArgType.throwTypeError(realm)
            return objectDefineProperties(realm, obj, arguments.argument(1))
        }

        @ECMAImpl("20.1.2.4")
        @JvmStatic
        fun defineProperty(realm: Realm, arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                Errors.Object.DefinePropertyBadArgType.throwTypeError(realm)
            val key = Operations.toPropertyKey(realm, arguments.argument(1))
            val desc = Descriptor.fromObject(realm, arguments.argument(2))
            Operations.definePropertyOrThrow(realm, obj, key.asValue, desc)
            return obj
        }

        @ECMAImpl("20.1.2.5")
        @JvmStatic
        fun entries(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.argument(0))
            val names = Operations.enumerableOwnPropertyNames(realm, obj, PropertyKind.KeyValue)
            return Operations.createArrayFromList(realm, names)
        }

        @ECMAImpl("20.1.2.6")
        @JvmStatic
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

        @ECMAImpl("20.1.2.7")
        @JvmStatic
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

        @ECMAImpl("20.1.2.8")
        @JvmStatic
        fun getOwnPropertyDescriptor(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.argument(0))
            val key = Operations.toPropertyKey(realm, arguments.argument(1))
            val desc = obj.getOwnPropertyDescriptor(key) ?: return JSUndefined
            return desc.toObject(realm, obj)
        }

        @ECMAImpl("20.1.2.9")
        @JvmStatic
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

        @ECMAImpl("20.1.2.10")
        @JvmStatic
        fun getOwnPropertyNames(realm: Realm, arguments: JSArguments): JSValue {
            return getOwnPropertyKeys(realm, arguments.argument(0), false)
        }

        @ECMAImpl("20.1.2.11")
        @JvmStatic
        fun getOwnPropertySymbols(realm: Realm, arguments: JSArguments): JSValue {
            return getOwnPropertyKeys(realm, arguments.argument(0), true)
        }

        @ECMAImpl("20.1.2.12")
        @JvmStatic
        fun getPrototypeOf(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.argument(0))
            return obj.getPrototype()
        }

        @ECMAImpl("20.1.2.13")
        @JvmStatic
        fun is_(realm: Realm, arguments: JSArguments): JSValue {
            return arguments.argument(0).sameValue(arguments.argument(1)).toValue()
        }

        @ECMAImpl("20.1.2.14")
        @JvmStatic
        fun isExtensible(realm: Realm, arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return JSFalse
            return obj.isExtensible().toValue()
        }

        @ECMAImpl("20.1.2.15")
        @JvmStatic
        fun isFrozen(realm: Realm, arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return JSTrue
            return obj.isFrozen.toValue()
        }

        @ECMAImpl("20.1.2.16")
        @JvmStatic
        fun isSealed(realm: Realm, arguments: JSArguments): JSValue {
            val obj = arguments.argument(0)
            if (obj !is JSObject)
                return JSTrue
            return obj.isSealed.toValue()
        }

        @ECMAImpl("20.1.2.17")
        @JvmStatic
        fun keys(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.argument(0))
            val nameList = Operations.enumerableOwnPropertyNames(realm, obj, PropertyKind.Key)
            return Operations.createArrayFromList(realm, nameList)
        }

        @ECMAImpl("20.1.2.18")
        @JvmStatic
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

        @ECMAImpl("20.1.2.20")
        @JvmStatic
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

        @ECMAImpl("20.1.2.21")
        @JvmStatic
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

        @ECMAImpl("20.1.2.22")
        @JvmStatic
        fun values(realm: Realm, arguments: JSArguments): JSValue {
            val obj = Operations.toObject(realm, arguments.argument(0))
            val nameList = Operations.enumerableOwnPropertyNames(realm, obj, PropertyKind.Value)
            return Operations.createArrayFromList(realm, nameList)
        }

        private fun getOwnPropertyKeys(realm: Realm, target: JSValue, isSymbols: Boolean): JSValue {
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
    }
}
