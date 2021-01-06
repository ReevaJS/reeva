package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.builtins.JSMapObject
import me.mattco.reeva.runtime.objects.JSObject

class JSMapIterator private constructor(
    realm: Realm,
    data: JSMapObject.MapData,
    val iterationKind: PropertyKind,
) : JSObject(realm, realm.mapIteratorProto) {
    var iteratedMap: JSMapObject.MapData? = data
    var nextIndex = 0

    init {
        data.iterationCount++
    }

    companion object {
        fun create(realm: Realm, data: JSMapObject.MapData, kind: PropertyKind) = JSMapIterator(realm, data, kind).initialize()
    }
}
