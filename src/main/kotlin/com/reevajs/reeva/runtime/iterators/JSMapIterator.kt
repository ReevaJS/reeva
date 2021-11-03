package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.collections.JSMapObject
import com.reevajs.reeva.runtime.objects.JSObject

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
        fun create(realm: Realm, data: JSMapObject.MapData, kind: PropertyKind) =
            JSMapIterator(realm, data, kind).initialize()
    }
}
