package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject

class JSIteratorProto private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineBuiltin(realm, Realm.WellKnownSymbols.iterator, 0, ::symbolIterator)
    }

    companion object {
        fun create(realm: Realm) = JSIteratorProto(realm).initialize(realm)

        @ECMAImpl("27.1.2.1")
        @JvmStatic
        fun symbolIterator(arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
