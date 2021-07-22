package com.reevajs.reeva.runtime.intl

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject

class JSIntlObject(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("Locale", realm.localeCtor)
    }

    companion object {
        fun create(realm: Realm) = JSIntlObject(realm).initialize()
    }
}