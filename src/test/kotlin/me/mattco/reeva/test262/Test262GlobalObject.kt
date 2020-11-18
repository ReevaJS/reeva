package me.mattco.reeva.test262

import me.mattco.reeva.Reeva
import me.mattco.reeva.runtime.JSGlobalObject
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.utils.Error
import me.mattco.reeva.utils.JSArguments

class Test262GlobalObject private constructor(realm: Realm) : JSGlobalObject(realm) {
    override fun init() {
        super.init()

        defineOwnProperty("$262", JS262Object(realm).initialize(), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    }

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        return super.getOwnPropertyDescriptor(property)
    }

    inner class JS262Object(realm: Realm) : JSObject(realm, realm.objectProto) {
        override fun init() {
            super.init()

            defineOwnProperty("global", this@Test262GlobalObject, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
            defineOwnProperty("agent", JS262AgentObject.create(realm), Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        }

        @JSMethod("createRealm", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        fun createRealm(thisValue: JSValue, arguments: JSArguments): JSValue {
            val newRealm = Reeva.makeRealm()
            newRealm.initObjects()
            val newGlobal = Test262GlobalObject.create(newRealm)
            newRealm.setGlobalObject(newGlobal)
            return newGlobal.get("$262")
        }

        @JSMethod("gc", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        fun gc(thisValue: JSValue, arguments: JSArguments): JSValue {
            Error("unable to force JVM garbage collection").throwTypeError()
        }
    }

    // TODO
    class JS262AgentObject(realm: Realm) : JSObject(realm, realm.objectProto) {
//        @JSMethod("start", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
//        fun start(thisValue: JSValue, arguments: JSArguments): JSValue {
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
