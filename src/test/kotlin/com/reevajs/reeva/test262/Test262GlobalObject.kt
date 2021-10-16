package com.reevajs.reeva.test262

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Error

class Test262GlobalObject private constructor(realm: Realm) : JSGlobalObject(realm) {
    override fun init() {
        super.init()

        defineOwnProperty("$262", JS262Object.create(this, realm), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    class JS262Object private constructor(
        private val globalObject: JSObject,
        realm: Realm,
    ) : JSObject(realm, realm.objectProto) {
        override fun init() {
            super.init()

            defineOwnProperty("global", globalObject, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
            defineOwnProperty("agent", JS262AgentObject.create(realm), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
            defineBuiltin("createRealm", 0, Builtin.forClass(this::class.java, "createRealm"))
            defineBuiltin("detachArrayBuffer", 1, Builtin.forClass(this::class.java, "detachArrayBuffer"))
            defineBuiltin("gc", 0, Builtin.forClass(this::class.java, "gc"))
        }

        companion object {
            fun create(globalObject: JSObject, realm: Realm) = JS262Object(globalObject, realm).initialize()

            @JvmStatic
            fun createRealm(realm: Realm, arguments: JSArguments): JSValue {
                val newRealm = Reeva.makeRealm()
                return newRealm.globalObject.get("$262")
            }

            @JvmStatic
            fun detachArrayBuffer(realm: Realm, arguments: JSArguments): JSValue {
                Operations.detachArrayBuffer(realm, arguments.argument(0))
                return JSUndefined
            }

            @JvmStatic
            fun gc(realm: Realm, arguments: JSArguments): JSValue {
                Error("unable to force JVM garbage collection").throwTypeError(realm)
            }
        }
    }

    class JS262AgentObject(realm: Realm) : JSObject(realm, realm.objectProto) {
        companion object {
            fun create(realm: Realm) = JS262AgentObject(realm).initialize()
        }
    }

    companion object {
        fun create(realm: Realm) = Test262GlobalObject(realm).initialize()
    }
}
