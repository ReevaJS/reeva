package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Slot
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

        val set = AOs.ordinaryCreateFromConstructor(
            arguments.newTarget,
            listOf(Slot.SetData),
            defaultProto = Realm::setProto,
        )
        set[Slot.SetData] = JSSetObject.SetData()
        val iterator = arguments.argument(0)
        if (iterator == JSUndefined || iterator == JSNull)
            return set

        val adder = set.get("add")
        if (!AOs.isCallable(adder))
            Errors.Set.ThisMissingAdd.throwTypeError()

        val iteratorRecord = AOs.getIterator(iterator)
        while (true) {
            val next = AOs.iteratorStep(iteratorRecord)
            if (next == JSFalse)
                return set
            val nextValue = AOs.iteratorValue(next)
            try {
                AOs.call(adder, set, listOf(nextValue))
            } catch (e: ThrowException) {
                AOs.iteratorClose(iteratorRecord, e.value)
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
