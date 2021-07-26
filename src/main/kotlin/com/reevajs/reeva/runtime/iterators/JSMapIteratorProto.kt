package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSMapIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Map Iterator".toValue(), Descriptor.CONFIGURABLE)
        defineNativeFunction("next", 0, ::next)
    }

    fun next(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (thisValue !is JSMapIterator)
            Errors.IncompatibleMethodCall("%MapIteratorPrototype%.next").throwTypeError(realm)

        val data = thisValue.iteratedMap ?: return Operations.createIterResultObject(realm, JSUndefined, true)

        while (thisValue.nextIndex < data.keyInsertionOrder.size) {
            val key = data.keyInsertionOrder[thisValue.nextIndex]
            thisValue.nextIndex++
            if (key != JSEmpty) {
                val result = when (thisValue.iterationKind) {
                    PropertyKind.Key -> key
                    PropertyKind.Value -> data.map[key]!!
                    PropertyKind.KeyValue -> Operations.createArrayFromList(realm, listOf(key, data.map[key]!!))
                }
                return Operations.createIterResultObject(realm, result, false)
            }
        }

        data.iterationCount--
        thisValue.iteratedMap = null
        return Operations.createIterResultObject(realm, JSUndefined, true)
    }

    companion object {
        fun create(realm: Realm) = JSMapIteratorProto(realm).initialize()
    }
}
