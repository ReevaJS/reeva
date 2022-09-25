package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction

class JSGeneratorFunctionCtor(realm: Realm) : JSNativeFunction(realm, "GeneratorFunction", 1, realm.functionCtor) {
    override fun evaluate(arguments: JSArguments): JSValue {
        return AOs.createDynamicFunction(
            this,
            arguments.newTarget,
            AOs.FunctionKind.Generator,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSGeneratorFunctionCtor(realm).initialize()
    }
}
