package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.key

class JSIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineNativeFunction(Realm.`@@iterator`.key(), 0, function = ::`@@iterator`)
    }

    fun `@@iterator`(realm: Realm, arguments: JSArguments): JSValue {
        return arguments.thisValue
    }

    companion object {
        fun create(realm: Realm) = JSIteratorProto(realm).initialize()
    }
}
