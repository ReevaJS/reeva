package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject

open class JSErrorObject protected constructor(
    realm: Realm,
    errorProto: JSObject = realm.errorProto
) : JSObject(realm, errorProto) {
    companion object {
        fun create(realm: Realm) = JSErrorObject(realm).also { it.init() }
    }
}
