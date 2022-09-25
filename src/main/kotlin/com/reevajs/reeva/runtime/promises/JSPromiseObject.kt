package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName

class JSPromiseObject private constructor(
    state: AOs.PromiseState,
    result: JSValue,
    realm: Realm
) : JSObject(realm, realm.promiseProto) {
    var state by slot(SlotName.PromiseState, state)
    var result by slot(SlotName.PromiseResult, result)

    val fulfillReactions by slot(SlotName.PromiseFulfillReactions, mutableListOf())
    val rejectReactions by slot(SlotName.PromiseRejectReactions, mutableListOf())
    var isHandled by slot(SlotName.PromiseIsHandled, false)

    companion object {
        fun create(state: AOs.PromiseState, result: JSValue, realm: Realm) =
            JSPromiseObject(state, result, realm).initialize()
    }
}
