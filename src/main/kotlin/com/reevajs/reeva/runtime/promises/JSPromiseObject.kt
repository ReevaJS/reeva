package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot

class JSPromiseObject private constructor(
    state: AOs.PromiseState,
    result: JSValue,
    realm: Realm
) : JSObject(realm, realm.promiseProto) {
    var state by slot(Slot.PromiseState, state)
    var result by slot(Slot.PromiseResult, result)

    val fulfillReactions by slot(Slot.PromiseFulfillReactions, mutableListOf())
    val rejectReactions by slot(Slot.PromiseRejectReactions, mutableListOf())
    var isHandled by slot(Slot.PromiseIsHandled, false)

    companion object {
        fun create(state: AOs.PromiseState, result: JSValue, realm: Realm) =
            JSPromiseObject(state, result, realm).initialize()
    }
}
