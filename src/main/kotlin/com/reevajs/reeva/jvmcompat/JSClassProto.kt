package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject

class JSClassProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    companion object {
        fun create(realm: Realm) = JSClassProto(realm).initialize()
    }
}
