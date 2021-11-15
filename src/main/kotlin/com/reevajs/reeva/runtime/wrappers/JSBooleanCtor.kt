package com.reevajs.reeva.runtime.wrappers

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.toValue

class JSBooleanCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Boolean", 1) {
    override fun evaluate(arguments: JSArguments): JSValue {
        val bool = Operations.toBoolean(arguments.argument(0)).toValue()
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            return bool
        if (newTarget == realm.booleanCtor)
            return JSBooleanObject.create(Operations.toBoolean(arguments.argument(0)).toValue())
        return Operations.ordinaryCreateFromConstructor(
            newTarget,
            listOf(SlotName.BooleanData),
            defaultProto = Realm::booleanProto,
        ).also {
            it.setSlot(SlotName.BooleanData, bool)
        }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBooleanCtor(realm).initialize()
    }
}
