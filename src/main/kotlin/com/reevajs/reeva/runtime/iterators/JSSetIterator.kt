package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.collections.SetData
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.expect

class JSSetIterator private constructor(
    realm: Realm,
    data: SetData,
    val iterationKind: PropertyKind
) : JSObject(realm, realm.setIteratorProto) {
    var iteratedSet: SetData? = data
    var nextIndex = 0

    init {
        expect(iterationKind != PropertyKind.Key)
        data.iterationCount++
    }

    companion object {
        fun create(data: SetData, kind: PropertyKind, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSSetIterator(realm, data, kind).initialize()
    }
}
