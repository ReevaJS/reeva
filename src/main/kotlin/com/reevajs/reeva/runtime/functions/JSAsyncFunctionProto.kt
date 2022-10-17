package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObject.Companion.initialize
import com.reevajs.reeva.utils.toValue

class JSAsyncFunctionProto private constructor(realm: Realm) : JSObject(realm, realm.functionProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "AsyncFunction".toValue(), Descriptor.CONFIGURABLE)
        defineOwnProperty("constructor", realm.asyncFunctionCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSAsyncFunctionProto(realm).initialize()
    }
}
