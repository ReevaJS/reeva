package me.mattco.reeva.test262

import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.runtime.Realm

class Test262GlobalObject private constructor(realm: Realm) : JSGlobalObject(realm) {
    companion object {
        fun create(realm: Realm) = Test262GlobalObject(realm).also { it.init() }
    }
}
