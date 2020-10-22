package me.mattco.reeva.runtime.values.functions

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.NativeFunctionSignature
import me.mattco.reeva.runtime.annotations.NativeGetterSignature
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSString
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.toValue

abstract class JSNativeFunction protected constructor(
    realm: Realm,
    private val name: String,
    private val length: Int,
) : JSFunction(realm, ThisMode.Global) {
    override fun init() {
        defineOwnProperty("length", Descriptor(length.toValue(), Attributes(Attributes.CONFIGURABLE)))
        defineOwnProperty("name", Descriptor(name.toValue(), Attributes(Attributes.CONFIGURABLE)))
    }

    override fun name() = name

    companion object {
        fun fromLambda(realm: Realm, name: String, length: Int, lambda: NativeFunctionSignature) =
            object : JSNativeFunction(realm, name, length) {
                override fun call(thisValue: JSValue, arguments: List<JSValue>) = lambda(thisValue, arguments)

                override fun construct(arguments: List<JSValue>, newTarget: JSValue): JSValue {
                    shouldThrowError()
                }
            }.also { it.init() }
    }
}
