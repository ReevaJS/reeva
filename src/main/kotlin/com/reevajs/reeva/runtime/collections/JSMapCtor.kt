package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs

class JSMapCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Map", 0) {
    override fun init() {
        super.init()

        defineBuiltinGetter(Realm.WellKnownSymbols.species, ::getSymbolSpecies, attrs { +conf; -enum })
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Map").throwTypeError()

        val map = AOs.ordinaryCreateFromConstructor(
            arguments.newTarget,
            listOf(Slot.MapData),
            defaultProto = Realm::mapProto,
        )
        map[Slot.MapData] = MapData()
        val iterable = arguments.argument(0)
        if (iterable == JSUndefined || iterable == JSNull)
            return map

        val adder = map.get("set")
        return AOs.addEntriesFromIterable(map, iterable, adder)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSMapCtor(realm).initialize()

        @ECMAImpl("24.1.2.2")
        @JvmStatic
        fun getSymbolSpecies(arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
