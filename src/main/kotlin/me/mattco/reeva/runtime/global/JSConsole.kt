package me.mattco.reeva.runtime.global

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

class JSConsole(realm: Realm) : JSObject(realm, realm.consoleProto) {
    companion object {
        fun create(realm: Realm) = JSConsole(realm).also { it.init() }
    }
}
