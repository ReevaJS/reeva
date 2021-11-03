package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

abstract class JSBuiltinFunction protected constructor(
    val builtin: Builtin,
    realm: Realm,
    name: String,
    length: Int,
    prototype: JSValue = realm.functionProto,
    isConstructor: Boolean = true
) : JSNativeFunction(realm, name, length, prototype, builtin.debugName, isConstructor) {
    companion object {
        fun forBuiltin(realm: Realm, name: String, length: Int, builtin: Builtin): JSFunction {
            return object : JSBuiltinFunction(builtin, realm, name, length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(name).throwTypeError(realm)
                    return builtin.handle.invokeExact(realm, arguments) as JSValue
                }
            }.initialize()
        }
    }
}