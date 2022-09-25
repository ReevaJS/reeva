package com.reevajs.reeva.runtime.promises

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSPromiseAllResolver private constructor(
    realm: Realm,
    private val index: Int,
    private val values: MutableList<JSValue>,
    private val capability: AOs.PromiseCapability,
    private val remainingElements: AOs.Wrapper<Int>,
) : JSNativeFunction(realm, "", 1) {
    private var alreadyCalled: Boolean = false

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget != JSUndefined)
            throw IllegalStateException("Unexpected construction of JSPromiseAllResolver")
        if (alreadyCalled)
            return JSUndefined
        alreadyCalled = true

        values[index] = arguments.argument(0)
        remainingElements.value--
        if (remainingElements.value == 0) {
            val valuesArray = AOs.createArrayFromList(values)
            return AOs.call(capability.resolve!!, JSUndefined, listOf(valuesArray))
        }
        return JSUndefined
    }

    companion object {
        fun create(
            index: Int,
            values: MutableList<JSValue>,
            capability: AOs.PromiseCapability,
            remainingElements: AOs.Wrapper<Int>,
            realm: Realm = Agent.activeAgent.getActiveRealm(),
        ) = JSPromiseAllResolver(realm, index, values, capability, remainingElements).initialize()
    }
}
