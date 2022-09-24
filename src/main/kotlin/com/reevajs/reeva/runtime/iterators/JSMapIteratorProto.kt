package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSMapIteratorProto private constructor(realm: Realm) : JSObject(realm.iteratorProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Map Iterator".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin(realm, "next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm) = JSMapIteratorProto(realm).initialize(realm)

        @ECMAImpl("24.1.5.2")
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSMapIterator)
                Errors.IncompatibleMethodCall("%MapIteratorPrototype%.next").throwTypeError()

            val data = thisValue.iteratedMap ?: return Operations.createIterResultObject(JSUndefined, true)

            while (thisValue.nextIndex < data.keyInsertionOrder.size) {
                val key = data.keyInsertionOrder[thisValue.nextIndex]
                thisValue.nextIndex++
                if (key != JSEmpty) {
                    val result = when (thisValue.iterationKind) {
                        PropertyKind.Key -> key
                        PropertyKind.Value -> data.map[key]!!
                        PropertyKind.KeyValue -> Operations.createArrayFromList(listOf(key, data.map[key]!!))
                    }
                    return Operations.createIterResultObject(result, false)
                }
            }

            data.iterationCount--
            thisValue.iteratedMap = null
            return Operations.createIterResultObject(JSUndefined, true)
        }
    }
}
