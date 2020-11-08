package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSEmpty

class JSSetObject private constructor(realm: Realm) : JSObject(realm, realm.setProto) {
    val setData = mutableSetOf<JSValue>()
    val insertionOrder = mutableListOf<JSValue>()
    var iterationCount = 0
        set(value) {
            if (value == 0)
                insertionOrder.removeIf { it == JSEmpty }
            field = value
        }

    companion object {
        fun create(realm: Realm) = JSSetObject(realm).also { it.init() }
    }
}
