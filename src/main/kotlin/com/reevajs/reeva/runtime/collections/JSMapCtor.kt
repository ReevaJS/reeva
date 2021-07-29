package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key

class JSMapCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Map", 0) {
    override fun init() {
        super.init()
        defineBuiltinAccessor(Realm.`@@species`.key(), attrs { +conf -enum }, Builtin.MapCtorGetSymbolSpecies, null)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Map").throwTypeError(realm)

        val map = Operations.ordinaryCreateFromConstructor(realm, arguments.newTarget, realm.mapProto, listOf(SlotName.MapData))
        map.setSlot(SlotName.MapData, JSMapObject.MapData())
        val iterable = arguments.argument(0)
        if (iterable == JSUndefined || iterable == JSNull)
            return map

        val adder = map.get("set")
        return Operations.addEntriesFromIterable(realm, map, iterable, adder)
    }

    companion object {
        fun create(realm: Realm) = JSMapCtor(realm).initialize()

        @ECMAImpl("24.1.2.2")
        @JvmStatic
        fun `get@@species`(realm: Realm, thisValue: JSValue): JSValue {
            return thisValue
        }
    }
}