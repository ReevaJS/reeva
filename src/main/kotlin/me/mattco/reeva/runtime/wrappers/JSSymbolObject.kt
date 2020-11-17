package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSSymbol

class JSSymbolObject private constructor(realm: Realm, val symbol: JSSymbol) : JSObject(realm, realm.symbolProto) {
    companion object {
        fun create(realm: Realm, symbol: JSSymbol) = JSSymbolObject(realm, symbol).initialize()
    }
}
