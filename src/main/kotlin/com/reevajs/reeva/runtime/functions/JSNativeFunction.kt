package com.reevajs.reeva.runtime.functions

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.NativeFunctionSignature
import com.reevajs.reeva.utils.toValue
import java.lang.invoke.MethodHandle
import java.lang.reflect.InvocationTargetException

abstract class JSNativeFunction protected constructor(
    realm: Realm,
    private val name: String,
    private val length: Int,
    prototype: JSValue = realm.functionProto,
    private val isConstructor: Boolean = true
) : JSFunction(realm, prototype = prototype) {
    override fun isConstructor() = isConstructor

    override fun init() {
        super.init()

        defineOwnProperty("length", length.toValue(), Descriptor.CONFIGURABLE)
        defineOwnProperty("name", name.toValue(), Descriptor.CONFIGURABLE)
    }

    companion object {
        fun fromLambda(
            realm: Realm,
            name: String,
            length: Int,
            lambda: NativeFunctionSignature,
        ) = object : JSNativeFunction(realm, name, length, isConstructor = false) {
            override fun evaluate(arguments: JSArguments): JSValue {
                if (arguments.newTarget != JSUndefined)
                    Errors.NotACtor(name).throwTypeError(realm)
                return try {
                    lambda(realm, arguments)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        }.initialize()

        internal fun forBuiltin(realm: Realm, builtin: Builtin): JSFunction {
            return when (builtin.type) {
                Builtin.Type.Normal -> forNormalBuiltin(realm, builtin)
                Builtin.Type.Getter -> forGetter(realm, builtin)
                Builtin.Type.Setter -> forSetter(realm, builtin)
            }
        }

        private fun forNormalBuiltin(realm: Realm, builtin: Builtin): JSFunction {
            return object : JSNativeFunction(realm, builtin.name, builtin.length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(builtin.key.toString()).throwTypeError(realm)
                    return builtin.handle.invokeExact(realm, arguments) as JSValue
                }
            }.initialize()
        }

        private fun forGetter(realm: Realm, builtin: Builtin): JSFunction {
            return object : JSNativeFunction(realm, "get ${builtin.name}", builtin.length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(builtin.key.toString()).throwTypeError(realm)
                    return builtin.handle.invokeExact(realm, arguments.thisValue) as JSValue
                }
            }.initialize()
        }

        private fun forSetter(realm: Realm, builtin: Builtin): JSFunction {
            return object : JSNativeFunction(realm, "set ${builtin.name}", builtin.length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(builtin.key.toString()).throwTypeError(realm)
                    builtin.handle.invokeExact(realm, arguments.thisValue, arguments.argument(0))
                    return JSUndefined
                }
            }.initialize()
        }
    }
}
