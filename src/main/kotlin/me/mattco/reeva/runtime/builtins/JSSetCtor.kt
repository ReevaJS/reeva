package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.AbruptCompletion
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSNativeAccessorGetter
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument

class JSSetCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Set", 0) {
    init {
        isConstructable = true
    }

    @JSNativeAccessorGetter("@@species", Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun `get@@species`(thisValue: JSValue): JSValue {
        return thisValue
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        Errors.CtorCallWithoutNew("Set").throwTypeError()
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        // TODO: Handle newTarget properly

        val set = JSSetObject.create(realm)
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
        fun create(realm: Realm) = JSSetCtor(realm).also { it.init() }
    }
}
