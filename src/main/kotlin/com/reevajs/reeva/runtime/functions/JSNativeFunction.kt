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

        internal fun forBuiltin(realm: Realm, name: String, length: Int, builtin: Builtin): JSFunction {
            return when (builtin.type) {
                Builtin.Type.Normal -> forNormalBuiltin(realm, name, length, builtin.handle)
                Builtin.Type.Getter -> forGetter(realm, name, length, builtin.handle)
                Builtin.Type.Setter -> forSetter(realm, name, length, builtin.handle)
            }
        }

        private fun forNormalBuiltin(realm: Realm, name: String, length: Int, handle: MethodHandle): JSFunction {
            return object : JSNativeFunction(realm, name, length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(name).throwTypeError(realm)
                    return handle.invokeExact(realm, arguments) as JSValue
                }
            }.initialize()
        }

        private fun forGetter(realm: Realm, name: String, length: Int, handle: MethodHandle): JSFunction {
            return object : JSNativeFunction(realm, "get $name", length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(name).throwTypeError(realm)
                    return handle.invokeExact(realm, arguments.thisValue) as JSValue
                }
            }.initialize()
        }

        private fun forSetter(realm: Realm, name: String, length: Int, handle: MethodHandle): JSFunction {
            return object : JSNativeFunction(realm, "set $name", length, isConstructor = false) {
                override fun evaluate(arguments: JSArguments): JSValue {
                    if (arguments.newTarget != JSUndefined)
                        Errors.NotACtor(name).throwTypeError(realm)
                    handle.invokeExact(realm, arguments.thisValue, arguments.argument(0))
                    return JSUndefined
                }
            }.initialize()
        }
    }
}
