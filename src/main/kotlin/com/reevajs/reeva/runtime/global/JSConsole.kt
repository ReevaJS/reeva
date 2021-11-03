package com.reevajs.reeva.runtime.global

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject

class JSConsole(realm: Realm) : JSObject(realm, realm.consoleProto) {
    companion object {
        fun create(realm: Realm) = JSConsole(realm).initialize()
    }
}
