package me.mattco.reeva.runtime.functions.generators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

class JSGeneratorObjectProto(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineNativeFunction("next", 1, ::next)
        defineNativeFunction("return", 1, ::`return`)
        defineNativeFunction("throw", 1, ::`throw`)
        defineOwnProperty(Realm.`@@toStringTag`, "Generator".toValue(), Descriptor.CONFIGURABLE)
    }

    private fun next(realm: Realm, arguments: JSArguments): JSValue {
        val generator = thisGeneratorObject(realm, arguments.thisValue, "next")
        return generator.next(realm, Interpreter.SuspendedEntryMode.Next, arguments.argument(0))
    }

    private fun `return`(realm: Realm, arguments: JSArguments): JSValue {
        val generator = thisGeneratorObject(realm, arguments.thisValue, "return")
        return generator.next(realm, Interpreter.SuspendedEntryMode.Return, arguments.argument(0))
    }

    private fun `throw`(realm: Realm, arguments: JSArguments): JSValue {
        val generator = thisGeneratorObject(realm, arguments.thisValue, "throw")
        return generator.next(realm, Interpreter.SuspendedEntryMode.Throw, arguments.argument(0))
    }

    companion object {
        private fun thisGeneratorObject(realm: Realm, value: JSValue, method: String): JSGeneratorObject {
            val obj = Operations.toObject(realm, value)
            if (obj !is JSGeneratorObject)
                Errors.IncompatibleMethodCall("Generator.prototype.$method").throwTypeError(realm)
            return obj
        }

        fun create(realm: Realm) = JSGeneratorObjectProto(realm).initialize()
    }
}
