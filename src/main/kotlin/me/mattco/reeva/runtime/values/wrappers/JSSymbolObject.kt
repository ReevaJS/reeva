package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSSymbol

class JSSymbolObject private constructor(realm: Realm, val symbol: JSSymbol) : JSObject(realm, realm.symbolProto) {
    companion object {
        fun create(realm: Realm, symbol: JSSymbol) = JSSymbolObject(realm, symbol).also { it.init() }
    }
}
