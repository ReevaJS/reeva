package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.utils.attrs
import me.mattco.reeva.utils.toValue

class JSFunctionCtor private constructor(realm: Realm) : JSNativeFunction(realm, "FunctionConstructor", 1) {
    override fun init() {
        super.init()

        defineOwnProperty("length", 1.toValue(), attrs { +conf -enum -writ })
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // TODO: Figure out correct realms
        return Operations.createDynamicFunction(
            realm,
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
