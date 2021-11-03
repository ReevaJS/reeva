package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey

class JSObjectPropertyIterator private constructor(
    realm: Realm,
    var obj: JSValue,
    var objWasVisited: Boolean,
    var visitedKeys: MutableList<PropertyKey>,
    var remainingKeys: MutableList<PropertyKey>,
) : JSObject(realm, realm.objectPropertyIteratorProto) {
    companion object {
        @JvmStatic @JvmOverloads
        fun create(
            realm: Realm,
            obj: JSObject,
            objWasVisited: Boolean = false,
            visitedKeys: MutableList<PropertyKey> = ArrayList(),
            remainingKeys: MutableList<PropertyKey> = ArrayList(),
        ) = JSObjectPropertyIterator(realm, obj, objWasVisited, visitedKeys, remainingKeys).initialize()
    }
}
