package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.collections.MapData
import com.reevajs.reeva.runtime.objects.JSObject

class JSMapIterator private constructor(
    realm: Realm,
    data: MapData,
    val iterationKind: PropertyKind,
) : JSObject(realm, realm.mapIteratorProto) {
    var iteratedMap: MapData? = data
    var nextIndex = 0

    init {
        data.iterationCount++
    }

    companion object {
        fun create(data: MapData, kind: PropertyKind, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSMapIterator(realm, data, kind).initialize()
    }
}
