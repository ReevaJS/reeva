package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.NativeFunctionSignature
import me.mattco.reeva.utils.toValue
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
    }
}
