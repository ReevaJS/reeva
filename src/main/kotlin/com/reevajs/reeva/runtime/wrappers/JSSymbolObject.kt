package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSSymbol

class JSSymbolObject private constructor(realm: Realm, symbol: JSSymbol) : JSObject(realm, realm.symbolProto) {
    val symbol by slot(SlotName.SymbolData, symbol)

    companion object {
        fun create(realm: Realm, symbol: JSSymbol) = JSSymbolObject(realm, symbol).initialize()
    }
}
