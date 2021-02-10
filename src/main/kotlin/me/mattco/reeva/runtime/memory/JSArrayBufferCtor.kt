package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.attrs
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue

class JSArrayBufferCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ArrayBuffer", 1) {
    init {
        isConstructable = true
    }

    override fun init() {
        super.init()

        defineNativeAccessor(Realm.`@@species`.key(), attrs { +conf -enum }, ::`get@@species`, name = "[Symbol.species]")
        defineNativeFunction("isView", 1, ::isView)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("ArrayBuffer").throwTypeError()
        return Operations.allocateArrayBuffer((newTarget as JSObject).realm, newTarget, arguments.argument(0).toIndex())
    }

    fun isView(arguments: JSArguments): JSValue {
        return arguments.argument(0).let { it is JSObject && it.hasSlot(SlotName.ViewedArrayBuffer) }.toValue()
    }

    fun `get@@species`(thisValue: JSValue): JSValue {
        return thisValue
    }

    companion object {
        fun create(realm: Realm) = JSArrayBufferCtor(realm).initialize()
    }
}
