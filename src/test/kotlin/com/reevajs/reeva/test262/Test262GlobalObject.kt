package com.reevajs.reeva.test262

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Error

class Test262GlobalObject private constructor(realm: Realm) : JSGlobalObject(realm) {
    override fun init() {
        super.init()

        defineOwnProperty("$262", JS262Object(realm).initialize(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    inner class JS262Object(realm: Realm) : JSObject(realm, realm.objectProto) {
        override fun init() {
            super.init()

            defineOwnProperty("global", this@Test262GlobalObject, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
            defineOwnProperty("agent", JS262AgentObject.create(realm), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
            defineNativeFunction("createRealm", 0, ::createRealm)
            defineNativeFunction("detachArrayBuffer", 1, ::detachArrayBuffer)
            defineNativeFunction("gc", 0, ::gc)
        }

        fun createRealm(realm: Realm, arguments: JSArguments): JSValue {
            val newRealm = Reeva.makeRealm()
            return newRealm.globalObject.get("$262")
        }

        fun detachArrayBuffer(realm: Realm, arguments: JSArguments): JSValue {
            Operations.detachArrayBuffer(realm, arguments.argument(0))
            return JSUndefined
        }

        fun gc(realm: Realm, arguments: JSArguments): JSValue {
            Error("unable to force JVM garbage collection").throwTypeError(realm)
        }
    }

    // TODO
    class JS262AgentObject(realm: Realm) : JSObject(realm, realm.objectProto) {
//        @JSMethod("start", 1)
//        fun start(arguments: JSArguments): JSValue {
//            val script = Operations.toString(arguments.argument(0))
//            val hasStarted = AtomicBoolean(false)
//
//            thread {
//                val agent = Reeva.getAgent()
//                val newRealm = Reeva.makeRealm()
//                newRealm.initObjects()
//                newRealm.setGlobalObject(Test262GlobalObject.create(newRealm))
//
//
//            }
//
//            @Suppress("ControlFlowWithEmptyBody")
//            while (!hasStarted.get());
//
//
//        }

        companion object {
            fun create(realm: Realm) = JS262AgentObject(realm).initialize()
        }
    }

    companion object {
        fun create(realm: Realm) = Test262GlobalObject(realm).initialize()
    }
}
