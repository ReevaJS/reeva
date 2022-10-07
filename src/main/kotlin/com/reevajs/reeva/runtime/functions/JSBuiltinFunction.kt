package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

typealias BuiltinFunction = (JSArguments) -> JSValue

class JSBuiltinFunction private constructor(
    realm: Realm,
    name: String,
    length: Int,
    private val builtin: BuiltinFunction,
    prototype: JSValue = realm.functionProto,
) : JSNativeFunction(realm, name, length, prototype, name) {
    override fun isConstructor() = false

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            Errors.NotACtor(name).throwTypeError()
        return builtin(arguments)
    }

    companion object {
        @JvmOverloads
        fun create(
            name: String,
            length: Int,
            builtin: BuiltinFunction,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            prototype: JSValue = realm.functionProto,
        ) = JSBuiltinFunction(realm, name, length, builtin, prototype).initialize()

        fun create(builtin: BuiltinFunction): JSFunction {
            val realm = Agent.activeAgent.getActiveRealm()

            return JSBuiltinFunction(
                realm,
                "<native function>",
                0,
                builtin,
                realm.functionProto
            ).initialize()
        }
    }
}
