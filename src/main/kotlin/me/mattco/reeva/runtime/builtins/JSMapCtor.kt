package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSMapCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Map", 0) {
    init {
        isConstructable = true
    }

    override fun init() {
        super.init()
        defineNativeAccessor(Realm.`@@species`.key(), attrs { +conf -enum }, ::`get@@species`, null)
    }

    fun `get@@species`(thisValue: JSValue): JSValue {
        return thisValue
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Map").throwTypeError()
        // TODO: Handle newTarget properly
        val map = JSMapObject.create(realm)
        val iterable = arguments.argument(0)
        if (iterable == JSUndefined || iterable == JSNull)
            return map

        val adder = map.get("set")
        return Operations.addEntriesFromIterable(map, iterable, adder)
    }

    companion object {
        fun create(realm: Realm) = JSMapCtor(realm).initialize()
    }
}
