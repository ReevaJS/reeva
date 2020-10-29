package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey

class JSObjectPropertyIterator private constructor(
    realm: Realm,
    var obj: JSValue,
    var objWasVisited: Boolean,
    var visitedKeys: MutableList<PropertyKey>,
    var remainingKeys: MutableList<PropertyKey>,
) : JSObject(realm, realm.objectPropertyIteratorProto) {
    companion object {
        fun create(
            realm: Realm,
            obj: JSObject,
            objWasVisited: Boolean = false,
            visitedKeys: MutableList<PropertyKey> = ArrayList(),
            remainingKeys: MutableList<PropertyKey> = ArrayList(),
        ) = JSObjectPropertyIterator(realm, obj, objWasVisited, visitedKeys, remainingKeys).also { it.init() }
    }
}
