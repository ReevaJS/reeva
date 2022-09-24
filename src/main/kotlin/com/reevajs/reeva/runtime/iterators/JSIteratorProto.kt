package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject

class JSIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin(Realm.WellKnownSymbols.iterator, 0, ::symbolIterator)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSIteratorProto(realm).initialize()

        @ECMAImpl("27.1.2.1")
        @JvmStatic
        fun symbolIterator(arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
