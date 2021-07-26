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

class JSSetIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Set Iterator".toValue(), Descriptor.CONFIGURABLE)
        defineNativeFunction("next", 0, ::next)
    }

    fun next(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (thisValue !is JSSetIterator)
            Errors.IncompatibleMethodCall("%MapIteratorPrototype%.next").throwTypeError(realm)

        val set = thisValue.iteratedSet ?: return Operations.createIterResultObject(realm, JSUndefined, true)

        while (thisValue.nextIndex < set.insertionOrder.size) {
            val value = set.insertionOrder[thisValue.nextIndex]
            thisValue.nextIndex++
            if (value != JSEmpty) {
                if (thisValue.iterationKind == PropertyKind.KeyValue)
                    return Operations.createIterResultObject(realm, Operations.createArrayFromList(realm, listOf(value, value)), false)
                return Operations.createIterResultObject(realm, value, false)
            }
        }

        set.iterationCount--
        thisValue.iteratedSet = null
        return Operations.createIterResultObject(realm, JSUndefined, true)
    }

    companion object {
        fun create(realm: Realm) = JSSetIteratorProto(realm).initialize()
    }
}
