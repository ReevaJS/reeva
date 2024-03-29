package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toIndex
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSArrayBufferCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ArrayBuffer", 1) {
    override fun init() {
        super.init()

        defineBuiltinGetter(Realm.WellKnownSymbols.species, ::getSymbolSpecies, attrs { +conf; -enum })
        defineBuiltin("isView", 1, ::isView)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("ArrayBuffer").throwTypeError()

        return AOs.allocateArrayBuffer(
            newTarget,
            arguments.argument(0).toIndex()
        )
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayBufferCtor(realm).initialize()

        @ECMAImpl("25.1.4.1")
        @JvmStatic
        fun isView(arguments: JSArguments): JSValue {
            return arguments.argument(0).let { it is JSObject && Slot.ViewedArrayBuffer in it }.toValue()
        }

        @ECMAImpl("25.1.4.3")
        @JvmStatic
        fun getSymbolSpecies(arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}
