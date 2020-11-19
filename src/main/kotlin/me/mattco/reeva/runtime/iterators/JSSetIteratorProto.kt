package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.toValue

class JSSetIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Set Iterator".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("next", 0)
    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSSetIterator)
            Errors.IncompatibleMethodCall("%MapIteratorPrototype%.next").throwTypeError()

        val set = thisValue.iteratedSet ?: return Operations.createIterResultObject(JSUndefined, true)

        while (thisValue.nextIndex < set.insertionOrder.size) {
            val value = set.insertionOrder[thisValue.nextIndex]
            thisValue.nextIndex++
            if (value != JSEmpty) {
                if (thisValue.iterationKind == PropertyKind.KeyValue)
                    return Operations.createIterResultObject(Operations.createArrayFromList(listOf(value, value)), false)
                return Operations.createIterResultObject(value, false)
            }
        }

        set.iterationCount--
        thisValue.iteratedSet = null
        return Operations.createIterResultObject(JSUndefined, true)
    }

    companion object {
        fun create(realm: Realm) = JSSetIteratorProto(realm).initialize()
    }
}
