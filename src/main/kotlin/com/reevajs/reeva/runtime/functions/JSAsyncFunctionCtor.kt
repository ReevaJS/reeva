package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments

class JSAsyncFunctionCtor(realm: Realm) : JSNativeFunction(realm, "AsyncFunction", 1, realm.functionCtor) {
    override fun evaluate(arguments: JSArguments): JSValue {
        return AOs.createDynamicFunction(
            this,
            arguments.newTarget,
            AOs.FunctionKind.Async,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSAsyncFunctionCtor(realm).initialize()
    }
}
