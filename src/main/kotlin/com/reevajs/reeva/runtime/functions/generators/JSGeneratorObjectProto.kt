package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.toObject
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSGeneratorObjectProto(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Generator".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("next", 1, ::next)
        defineBuiltin("return", 1, ::return_)
        defineBuiltin("throw", 1, ::throw_)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSGeneratorObjectProto(realm).initialize()

        private fun thisGeneratorObject(value: JSValue, method: String): JSGeneratorObject {
            val obj = value.toObject()
            if (obj !is JSGeneratorObject)
                Errors.IncompatibleMethodCall("Generator.prototype.$method").throwTypeError()
            return obj
        }

        @ECMAImpl("27.5.1.2")
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val generator = thisGeneratorObject(arguments.thisValue, "next")
            return generator.next(arguments.argument(0))
        }

        @ECMAImpl("27.5.1.3")
        @JvmStatic
        fun return_(arguments: JSArguments): JSValue {
            val generator = thisGeneratorObject(arguments.thisValue, "return")
            return generator.return_(arguments.argument(0))
        }

        @ECMAImpl("27.5.1.4")
        @JvmStatic
        fun throw_(arguments: JSArguments): JSValue {
            val generator = thisGeneratorObject(arguments.thisValue, "throw")
            return generator.throw_(arguments.argument(0))
        }
    }
}
