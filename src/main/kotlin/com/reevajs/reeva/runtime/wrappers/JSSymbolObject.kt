package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSSymbol

class JSSymbolObject private constructor(realm: Realm, symbol: JSSymbol) : JSObject(realm, realm.symbolProto) {
    val symbol by slot(Slot.SymbolData, symbol)

    companion object {
        fun create(symbol: JSSymbol, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSSymbolObject(realm, symbol).initialize()
    }
}
