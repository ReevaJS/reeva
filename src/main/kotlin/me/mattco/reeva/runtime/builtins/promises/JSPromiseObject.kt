package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject

class JSPromiseObject private constructor(
    var state: Operations.PromiseState,
    var result: JSValue,
    realm: Realm
) : JSObject(realm, realm.promiseProto) {
    val fulfillReactions = mutableListOf<Operations.PromiseReaction>()
    val rejectReactions = mutableListOf<Operations.PromiseReaction>()
    var isHandled = false

    companion object {
        fun create(state: Operations.PromiseState, result: JSValue, realm: Realm) = JSPromiseObject(state, result, realm).initialize()
    }
}
