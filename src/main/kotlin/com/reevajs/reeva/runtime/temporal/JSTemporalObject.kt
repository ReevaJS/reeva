package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject

class JSTemporalObject(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("Now", JSTemporalNowObject.create(realm))
        defineOwnProperty("Instant", realm.instantCtor)
    }

    companion object {
        fun create(realm: Realm) = JSTemporalObject(realm).initialize()
    }
}
