package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.collections.JSSetObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect

class JSSetIterator private constructor(
    realm: Realm,
    data: JSSetObject.SetData,
    val iterationKind: PropertyKind
) : JSObject(realm, realm.setIteratorProto) {
    var iteratedSet: JSSetObject.SetData? = data
    var nextIndex = 0

    init {
        expect(iterationKind != PropertyKind.Key)
        data.iterationCount++
    }

    companion object {
        fun create(realm: Realm, data: JSSetObject.SetData, kind: PropertyKind) =
            JSSetIterator(realm, data, kind).initialize()
    }
}
