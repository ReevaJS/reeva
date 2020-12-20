package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.key

class JSIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineNativeFunction(Realm.`@@iterator`.key(), 0, function = ::`@@iterator`)
    }

    fun `@@iterator`(thisValue: JSValue, arguments: JSArguments): JSValue {
        return thisValue
    }

    companion object {
        fun create(realm: Realm) = JSIteratorProto(realm).initialize()
    }
}
