package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject

class JSListIterator private constructor(
    realm: Realm,
    val iteratorList: List<JSValue>,
) : JSObject(realm, realm.listIteratorProto) {
    var nextIndex: Int = 0

    companion object {
        fun create(realm: Realm, iteratorList: List<JSValue>) = JSListIterator(realm, iteratorList)
    }
}
