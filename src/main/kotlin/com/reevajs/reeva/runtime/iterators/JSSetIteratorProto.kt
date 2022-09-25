package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSSetIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Set Iterator".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSetIteratorProto(realm).initialize()

        @ECMAImpl("24.2.5.2.1")
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSSetIterator)
                Errors.IncompatibleMethodCall("%MapIteratorPrototype%.next").throwTypeError()

            val set = thisValue.iteratedSet ?: return AOs.createIterResultObject(JSUndefined, true)

            while (thisValue.nextIndex < set.insertionOrder.size) {
                val value = set.insertionOrder[thisValue.nextIndex]
                thisValue.nextIndex++
                if (value != JSEmpty) {
                    if (thisValue.iterationKind == PropertyKind.KeyValue) {
                        return AOs.createIterResultObject(
                            AOs.createArrayFromList(listOf(value, value)),
                            false
                        )
                    }
                    return AOs.createIterResultObject(value, false)
                }
            }

            set.iterationCount--
            thisValue.iteratedSet = null
            return AOs.createIterResultObject(JSUndefined, true)
        }
    }
}
