package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.JSArguments

class JSIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSMethod("@@iterator", 0)
    fun `@@iterator`(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisValue
    }

    companion object {
        fun create(realm: Realm) = JSIteratorProto(realm).initialize()
    }
}
