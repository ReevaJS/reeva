package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSArrayBufferCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ArrayBuffer", 1) {
    override fun init() {
        super.init()

        defineBuiltinAccessor(
            Realm.`@@species`.key(),
            attrs { +conf -enum },
            Builtin.ArrayBufferCtorGetSymbolSpecies,
            name = "[Symbol.species]"
        )
        defineBuiltin("isView", 1, Builtin.ArrayBufferCtorIsView)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("ArrayBuffer").throwTypeError(realm)

        return Operations.allocateArrayBuffer(
            (newTarget as JSObject).realm,
            newTarget,
            arguments.argument(0).toIndex(realm)
        )
    }

    companion object {
        fun create(realm: Realm) = JSArrayBufferCtor(realm).initialize()

        @ECMAImpl("25.1.4.1")
        @JvmStatic
        fun isView(realm: Realm, arguments: JSArguments): JSValue {
            return arguments.argument(0).let { it is JSObject && it.hasSlot(SlotName.ViewedArrayBuffer) }.toValue()
        }

        @ECMAImpl("25.1.4.3")
        @JvmStatic
        fun `get@@species`(realm: Realm, thisValue: JSValue): JSValue {
            return thisValue
        }
    }
}
