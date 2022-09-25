package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSFunctionCtor private constructor(realm: Realm) : JSNativeFunction(realm, "FunctionConstructor", 1) {
    override fun init() {
        super.init()

        defineOwnProperty("length", 1.toValue(), attrs { +conf; -enum; -writ })
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        return AOs.createDynamicFunction(
            this,
            arguments.newTarget,
            AOs.FunctionKind.Normal,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFunctionCtor(realm).initialize()
    }
}
