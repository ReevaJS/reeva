package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
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
        fun create(data: JSSetObject.SetData, kind: PropertyKind, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSSetIterator(realm, data, kind).initialize()
    }
}
