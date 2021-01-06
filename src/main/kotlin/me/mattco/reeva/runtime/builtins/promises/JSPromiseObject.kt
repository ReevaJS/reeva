package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject

class JSPromiseObject private constructor(
    state: Operations.PromiseState,
    result: JSValue,
    realm: Realm
) : JSObject(realm, realm.promiseProto) {
    var state by slot(SlotName.PromiseState, state)
    var result by slot(SlotName.PromiseResult, result)

    val fulfillReactions by slot(SlotName.PromiseFulfillReactions, mutableListOf<Operations.PromiseReaction>())
    val rejectReactions by slot(SlotName.PromiseRejectReactions, mutableListOf<Operations.PromiseReaction>())
    var isHandled by slot(SlotName.PromiseIsHandled, false)

    companion object {
        fun create(state: Operations.PromiseState, result: JSValue, realm: Realm) = JSPromiseObject(state, result, realm).initialize()
    }
}
