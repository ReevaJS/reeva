package me.mattco.reeva.runtime.values.iterators

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.objects.PropertyKey

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
