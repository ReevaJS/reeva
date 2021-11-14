package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction

class JSGeneratorFunctionCtor(realm: Realm) : JSNativeFunction(realm, "GeneratorFunction", 1, realm.functionCtor) {
    override fun evaluate(arguments: JSArguments): JSValue {
        return Operations.createDynamicFunction(
            this,
            arguments.newTarget,
            Operations.FunctionKind.Generator,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSGeneratorFunctionCtor(realm).initialize()
    }
}
