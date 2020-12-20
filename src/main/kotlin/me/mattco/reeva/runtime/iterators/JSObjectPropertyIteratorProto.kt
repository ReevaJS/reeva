package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.key

class JSObjectPropertyIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineNativeFunction("next", 0, ::next)
    }

    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        ecmaAssert(thisValue is JSObjectPropertyIterator)
        while (true) {
            val obj = thisValue.obj
            ecmaAssert(obj is JSObject)

            if (!thisValue.objWasVisited) {
                obj.ownPropertyKeys().forEach { key ->
                    if (key.isString)
                        thisValue.remainingKeys.add(key)
                    if (key.isInt)
                        thisValue.remainingKeys.add(PropertyKey(key.asInt.toString()))
                    if (key.isDouble)
                        thisValue.remainingKeys.add(PropertyKey(key.asDouble.toString()))
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
                            return Operations.createIterResultObject(key.asValue, false)
                        }
                    }
                }
            }
            thisValue.obj = obj.getPrototype()
            thisValue.objWasVisited = false
            if (thisValue.obj == JSNull)
                return Operations.createIterResultObject(JSUndefined, true)
        }
    }

    companion object {
        fun create(realm: Realm) = JSObjectPropertyIteratorProto(realm).initialize()
    }
}
