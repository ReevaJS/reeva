package me.mattco.reeva.runtime.values.global

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject

class JSConsole(realm: Realm) : JSObject(realm, realm.consoleProto) {
    companion object {
        fun create(realm: Realm) = JSConsole(realm).also { it.init() }
    }
}
