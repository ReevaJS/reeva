package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSFunctionCtor private constructor(realm: Realm) : JSNativeFunction(realm, "FunctionConstructor", 1) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty("length", 1.toValue(), attrs { +conf; -enum; -writ })
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        return Operations.createDynamicFunction(
            this,
            arguments.newTarget,
            Operations.FunctionKind.Normal,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm) = JSFunctionCtor(realm).initialize()
    }
}
