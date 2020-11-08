package me.mattco.reeva.runtime.iterators

import com.sun.xml.internal.bind.v2.model.core.PropertyKind
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.builtins.JSMapObject
import me.mattco.reeva.runtime.objects.JSObject

class JSMapIterator private constructor(
    realm: Realm,
    map: JSMapObject,
    val iterationKind: PropertyKind,
) : JSObject(realm, realm.mapIteratorProto) {
    var iteratedMap: JSMapObject? = map
    var nextIndex = 0

    init {
        map.iterationCount++
    }

    companion object {
        fun create(realm: Realm, map: JSMapObject, kind: PropertyKind) = JSMapIterator(realm, map, kind).also { it.init() }
    }
}
