package me.mattco.reeva.runtime.values.iterators

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNull
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert

class JSObjectPropertyIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    @JSMethod("next", 0)
    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        ecmaAssert(thisValue is JSObjectPropertyIterator)
        while (true) {
            val obj = thisValue.obj
            ecmaAssert(obj is JSObject)

            if (!thisValue.objWasVisited) {
                obj.ownPropertyKeys().forEach { key ->
                    if (!key.isSymbol)
                        thisValue.remainingKeys.add(key)
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
        fun create(realm: Realm) = JSObjectPropertyIteratorProto(realm).also { it.init() }
    }
}
