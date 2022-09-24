package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs

class JSSetCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Set", 0) {
    override fun init() {
        super.init()
        defineBuiltinGetter(Realm.WellKnownSymbols.species, ::getSymbolSpecies, attrs { +conf; -enum })
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Set").throwTypeError()

        val set = Operations.ordinaryCreateFromConstructor(
            arguments.newTarget,
            listOf(SlotName.SetData),
            defaultProto = Realm::setProto,
        )
        set.setSlot(SlotName.SetData, JSSetObject.SetData())
        val iterator = arguments.argument(0)
        if (iterator == JSUndefined || iterator == JSNull)
            return set

        val adder = set.get("add")
        if (!Operations.isCallable(adder))
            Errors.Set.ThisMissingAdd.throwTypeError()

        val iteratorRecord = Operations.getIterator(iterator)
        while (true) {
            val next = Operations.iteratorStep(iteratorRecord)
            if (next == JSFalse)
                return set
            val nextValue = Operations.iteratorValue(next)
            try {
                Operations.call(adder, set, listOf(nextValue))
            } catch (e: ThrowException) {
                Operations.iteratorClose(iteratorRecord, e.value)
                throw e
            }
        }
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSSetCtor(realm).initialize()

        @ECMAImpl("24.2.2.2")
        @JvmStatic
        fun getSymbolSpecies(arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
