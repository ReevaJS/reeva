package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.utils.*

class JSFunctionCtor private constructor(realm: Realm) : JSNativeFunction(realm, "FunctionConstructor", 1) {
    init {
        isConstructable = true
    }

    override fun init() {
        super.init()

        defineOwnProperty("length", 1.toValue(), attrs { +conf -enum -writ })
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        return Operations.createDynamicFunction(
            Agent.runningContext.function!!,
            newTarget,
            Operations.FunctionKind.Normal,
            arguments
        )
    }

    companion object {
        fun create(realm: Realm) = JSFunctionCtor(realm).initialize()
    }
}
