package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toBoolean
import com.reevajs.reeva.utils.toValue

class JSBooleanCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Boolean", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val bool = arguments.argument(0).toBoolean().toValue()
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            return bool
        if (newTarget == realm.booleanCtor)
            return JSBooleanObject.create(arguments.argument(0).toBoolean().toValue())
        return AOs.ordinaryCreateFromConstructor(
            newTarget,
            listOf(Slot.BooleanData),
            defaultProto = Realm::booleanProto,
        ).also {
            it[Slot.BooleanData] = bool
        }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBooleanCtor(realm).initialize()
    }
}
