package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.utils.Errors
import java.util.function.Function

class JSRunnableFunction(
    realm: Realm,
    name: String,
    length: Int,
    prototype: JSValue = realm.functionProto,
    private val function: Function<JSArguments, JSValue>,
) : JSNativeFunction(realm, name, length, prototype) {
    override fun isConstructor() = false

    override fun evaluate(arguments: JSArguments) = function.apply(arguments)

    override fun construct(arguments: JSArguments): JSValue {
        Errors.NotACtor(name).throwTypeError()
    }

    companion object {
        @JvmOverloads
        fun create(
            name: String,
            length: Int,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            prototype: JSValue = realm.functionProto,
            function: Function<JSArguments, JSValue>,
        ) = JSRunnableFunction(realm, name, length, prototype, function).initialize()
    }
}
