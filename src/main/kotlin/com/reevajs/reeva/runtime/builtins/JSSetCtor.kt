package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.ThrowException
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.SlotName
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key

class JSSetCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Set", 0) {
    override fun init() {
        super.init()
        defineNativeAccessor(Realm.`@@species`.key(), attrs { +conf -enum }, ::`get@@species`, null)
    }

    fun `get@@species`(realm: Realm, thisValue: JSValue): JSValue {
        return thisValue
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Set").throwTypeError(realm)

        val set = Operations.ordinaryCreateFromConstructor(realm, arguments.newTarget, realm.setProto, listOf(SlotName.SetData))
        set.setSlot(SlotName.SetData, JSSetObject.SetData())
        val iterator = arguments.argument(0)
        if (iterator == JSUndefined || iterator == JSNull)
            return set

        val adder = set.get("add")
        if (!Operations.isCallable(adder))
            Errors.Set.ThisMissingAdd.throwTypeError(realm)

        val iteratorRecord = Operations.getIterator(realm, iterator)
        while (true) {
            val next = Operations.iteratorStep(iteratorRecord)
            if (next == JSFalse)
                return set
            val nextValue = Operations.iteratorValue(next)
            try {
                Operations.call(realm, adder, set, listOf(nextValue))
            } catch (e: ThrowException) {
                Operations.iteratorClose(iteratorRecord, e.value)
                throw e
            }
        }
    }

    companion object {
        fun create(realm: Realm) = JSSetCtor(realm).initialize()
    }
}
