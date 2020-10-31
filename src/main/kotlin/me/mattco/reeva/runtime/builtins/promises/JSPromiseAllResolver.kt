package me.mattco.reeva.runtime.builtins.promises

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument

class JSPromiseAllResolver private constructor(
    realm: Realm,
    private val index: Int,
    private val values: MutableList<JSValue>,
    private val capability: Operations.PromiseCapability,
    private val remainingElements: Operations.Wrapper<Int>,
) : JSNativeFunction(realm, "", 1) {
    private var alreadyCalled: Boolean = false

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (alreadyCalled)
            return JSUndefined
        alreadyCalled = true

        values[index] = arguments.argument(0)
        remainingElements.value--
        if (remainingElements.value == 0) {
            val valuesArray = Operations.createArrayFromList(values)
            return Operations.call(capability.resolve!!, JSUndefined, listOf(valuesArray))
        }
        return JSUndefined
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        throw IllegalStateException("Unexpected construction of JSPromiseAllResolver")
    }

    companion object {
        fun create(
            realm: Realm,
            index: Int,
            values: MutableList<JSValue>,
            capability: Operations.PromiseCapability,
            remainingElements: Operations.Wrapper<Int>,
        ) = JSPromiseAllResolver(realm, index, values, capability, remainingElements).also { it.init() }
    }
}
