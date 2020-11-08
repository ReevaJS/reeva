package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.throwTypeError
import me.mattco.reeva.utils.toValue

class JSMapIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Map Iterator".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("next", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSMapIterator)
            throwTypeError("%MapIterator%.prototype.next invoked on incompatible object")

        val map = thisValue.iteratedMap ?: return Operations.createIterResultObject(JSUndefined, true)

        while (thisValue.nextIndex < map.keyInsertionOrder.size) {
            val key = map.keyInsertionOrder[thisValue.nextIndex]
            thisValue.nextIndex++
            if (key != JSEmpty) {
                val result = when (thisValue.iterationKind) {
                    PropertyKind.Key -> key
                    PropertyKind.Value -> map.mapData[key]!!
                    PropertyKind.KeyValue -> Operations.createArrayFromList(listOf(key, map.mapData[key]!!))
                }
                return Operations.createIterResultObject(result, false)
            }
        }

        map.iterationCount--
        thisValue.iteratedMap = null
        return Operations.createIterResultObject(JSUndefined, true)
    }

    companion object {
        fun create(realm: Realm) = JSMapIteratorProto(realm).also { it.init() }
    }
}
