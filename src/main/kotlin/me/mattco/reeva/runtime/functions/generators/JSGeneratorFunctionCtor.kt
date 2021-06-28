package me.mattco.reeva.runtime.functions.generators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction

class JSGeneratorFunctionCtor(realm: Realm) : JSNativeFunction(realm, "GeneratorFunction", 1, realm.functionCtor) {
    override fun evaluate(arguments: JSArguments): JSValue {
        // TODO: Figure out correct realms
        return Operations.createDynamicFunction(
            realm,
            this,
            arguments.newTarget,
            Operations.FunctionKind.Generator,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm) = JSGeneratorFunctionCtor(realm).initialize()
    }
}
