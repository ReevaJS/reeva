package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.NativeFunctionSignature
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

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
                override fun call(thisValue: JSValue, arguments: JSArguments) = lambda(thisValue, arguments)

                override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
                    shouldThrowError()
                }
            }.also { it.init() }
    }
}
