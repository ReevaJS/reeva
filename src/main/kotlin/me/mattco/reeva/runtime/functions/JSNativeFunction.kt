package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.NativeFunctionSignature
import me.mattco.reeva.utils.toValue
import java.lang.reflect.InvocationTargetException

abstract class JSNativeFunction protected constructor(
    realm: Realm,
    private val name: String,
    private val length: Int,
) : JSFunction(realm, ThisMode.Global) {
    override fun init() {
        super.init()

        defineOwnProperty("length", length.toValue(), Descriptor.CONFIGURABLE)
        defineOwnProperty("name", name.toValue(), Descriptor.CONFIGURABLE)
    }

    companion object {
        fun fromLambda(realm: Realm, name: String, length: Int, lambda: NativeFunctionSignature) =
            object : JSNativeFunction(realm, name, length) {
                override fun call(thisValue: JSValue, arguments: JSArguments) = try {
                    lambda(thisValue, arguments)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }

                override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
                    Errors.NotACtor(name).throwTypeError()
                }
            }.also { it.init() }
    }
}
