package me.mattco.renva.runtime.values.global

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.annotations.JSMethod
import me.mattco.renva.runtime.values.objects.JSObject

class JSConsole(realm: Realm) : JSObject(realm, realm.consoleProto) {
    companion object {
        fun create(realm: Realm) = JSConsole(realm).also { it.init() }
    }
}
