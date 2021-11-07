package com.reevajs.reeva.runtime.wrappers.strings

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject

class JSStringIteratorObject private constructor(
    realm: Realm,
    internal var string: String?,
    internal var nextIndex: Int,
) : JSObject(realm, realm.stringIteratorProto) {
    companion object {
        fun create(
            realm: Realm,
            string: String,
        ) = JSStringIteratorObject(realm, string, 0).initialize()
    }
}