package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSString

class JSRegExpStringIterator private constructor(
    realm: Realm,
    var iteratingRegExp: JSRegExpObject,
    val iteratedString: JSString,
    val global: Boolean,
    val unicode: Boolean,
) : JSObject(realm, realm.regExpStringIteratorProto) {
    var done = false

    companion object {
        fun create(
            regexp: JSRegExpObject,
            string: JSString,
            global: Boolean,
            unicode: Boolean,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = JSRegExpStringIterator(realm, regexp, string, global, unicode).initialize()
    }
}
