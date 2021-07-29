package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert

class JSObjectPropertyIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineBuiltin("next", 0, Builtin.ObjectPropertyIteratorProtoNext)
    }

    companion object {
        fun create(realm: Realm) = JSObjectPropertyIteratorProto(realm).initialize()

        @ECMAImpl("14.7.5.10.2.1")
        @JvmStatic
        fun next(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            ecmaAssert(thisValue is JSObjectPropertyIterator)
            while (true) {
                val obj = thisValue.obj
                ecmaAssert(obj is JSObject)

                if (!thisValue.objWasVisited) {
                    obj.ownPropertyKeys().forEach { key ->
                        when {
                            key.isString -> thisValue.remainingKeys.add(key)
                            key.isInt -> thisValue.remainingKeys.add(PropertyKey.from(key.asInt.toString()))
                            key.isLong -> thisValue.remainingKeys.add(PropertyKey.from(key.asLong.toString()))
                        }
                    }
                    thisValue.objWasVisited = true
                }
                while (thisValue.remainingKeys.isNotEmpty()) {
                    val key = thisValue.remainingKeys.removeFirst()
                    if (key !in thisValue.visitedKeys) {
                        val desc = obj.getOwnPropertyDescriptor(key)
                        if (desc != null) {
                            thisValue.visitedKeys.add(key)
                            if (desc.isEnumerable) {
                                return Operations.createIterResultObject(realm, key.asValue, false)
                            }
                        }
                    }
                }
                thisValue.obj = obj.getPrototype()
                thisValue.objWasVisited = false
                if (thisValue.obj == JSNull)
                    return Operations.createIterResultObject(realm, JSUndefined, true)
            }
        }
    }
}
