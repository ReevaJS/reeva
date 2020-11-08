package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty

class JSMapObject private constructor(realm: Realm) : JSObject(realm, realm.mapProto) {
    val mapData = mutableMapOf<JSValue, JSValue>()
    val keyInsertionOrder = mutableListOf<JSValue>()
    var iterationCount = 0
        set(value) {
            if (value == 0)
                keyInsertionOrder.removeIf { it == JSEmpty }
            field = value
        }


    companion object {
        fun create(realm: Realm) = JSMapObject(realm).also { it.init() }
    }
}
