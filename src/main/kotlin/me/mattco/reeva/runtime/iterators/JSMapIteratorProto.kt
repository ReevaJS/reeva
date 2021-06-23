package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

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
