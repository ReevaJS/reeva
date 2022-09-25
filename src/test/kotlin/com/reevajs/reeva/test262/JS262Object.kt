package com.reevajs.reeva.test262

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Error

class JS262Object private constructor(
    private val globalObject: JSObject,
    realm: Realm,
) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("global", globalObject, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty("agent",
            JS262AgentObject.create(realm), Descriptor.CONFIGURABLE or Descriptor.WRITABLE
        )
        defineBuiltin("createRealm", 0, ::createRealm)
        defineBuiltin("detachArrayBuffer", 1, ::detachArrayBuffer)
        defineBuiltin("gc", 0, ::gc)
    }

    class JS262AgentObject(realm: Realm) : JSObject(realm, realm.objectProto) {
        companion object {
            fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JS262AgentObject(realm).initialize()
        }
    }

    companion object {
        fun create(globalObject: JSObject, realm: Realm) = JS262Object(globalObject, realm).initialize()

        @JvmStatic
        fun createRealm(arguments: JSArguments): JSValue {
            val newRealm = Agent.activeAgent.makeRealmAndInitializeExecutionEnvironment()
            return newRealm.globalObject.get("$262")
        }

        @JvmStatic
        fun detachArrayBuffer(arguments: JSArguments): JSValue {
            AOs.detachArrayBuffer(arguments.argument(0))
            return JSUndefined
        }

        @JvmStatic
        fun gc(arguments: JSArguments): JSValue {
            Error("unable to force JVM garbage collection").throwTypeError()
        }
    }
}
