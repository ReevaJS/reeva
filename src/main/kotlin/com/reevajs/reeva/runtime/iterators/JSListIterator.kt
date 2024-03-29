package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject

class JSListIterator private constructor(
    realm: Realm,
    val iteratorList: List<JSValue>,
) : JSObject(realm, realm.listIteratorProto) {
    var nextIndex: Int = 0

    companion object {
        fun create(iteratorList: List<JSValue>, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSListIterator(realm, iteratorList)
    }
}
