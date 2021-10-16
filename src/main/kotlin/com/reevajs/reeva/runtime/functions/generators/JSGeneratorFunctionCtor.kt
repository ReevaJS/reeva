package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction

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
