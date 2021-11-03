package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject

class JSListIterator private constructor(
    realm: Realm,
    val iteratorList: List<JSValue>,
) : JSObject(realm, realm.listIteratorProto) {
    var nextIndex: Int = 0

    companion object {
        fun create(realm: Realm, iteratorList: List<JSValue>) = JSListIterator(realm, iteratorList)
    }
}
