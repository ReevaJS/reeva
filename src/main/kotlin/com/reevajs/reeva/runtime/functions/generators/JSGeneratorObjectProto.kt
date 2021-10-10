package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSGeneratorObjectProto(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "Generator".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("next", 1, ReevaBuiltin.GeneratorObjectProtoNext)
        defineBuiltin("return", 1, ReevaBuiltin.GeneratorObjectProtoReturn)
        defineBuiltin("throw", 1, ReevaBuiltin.GeneratorObjectProtoThrow)
    }

    companion object {
        fun create(realm: Realm) = JSGeneratorObjectProto(realm).initialize()

        private fun thisGeneratorObject(realm: Realm, value: JSValue, method: String): JSGeneratorObject {
            val obj = Operations.toObject(realm, value)
            if (obj !is JSGeneratorObject)
                Errors.IncompatibleMethodCall("Generator.prototype.$method").throwTypeError(realm)
            return obj
        }

        @ECMAImpl("27.5.1.2")
        @JvmStatic
        fun next(realm: Realm, arguments: JSArguments): JSValue {
            TODO("Generators")
            // val generator = thisGeneratorObject(realm, arguments.thisValue, "next")
            // return generator.next(realm, Interpreter.SuspendedEntryMode.Next, arguments.argument(0))
        }

        @ECMAImpl("27.5.1.3")
        @JvmStatic
        fun `return`(realm: Realm, arguments: JSArguments): JSValue {
            TODO("Generators")
            // val generator = thisGeneratorObject(realm, arguments.thisValue, "return")
            // return generator.next(realm, Interpreter.SuspendedEntryMode.Return, arguments.argument(0))
        }

        @ECMAImpl("27.5.1.4")
        @JvmStatic
        fun `throw`(realm: Realm, arguments: JSArguments): JSValue {
            TODO("Generators")
            // val generator = thisGeneratorObject(realm, arguments.thisValue, "throw")
            // return generator.next(realm, Interpreter.SuspendedEntryMode.Throw, arguments.argument(0))
        }
    }
}
