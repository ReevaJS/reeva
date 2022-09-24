package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSArrayIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "Array Iterator".toValue(), Descriptor.CONFIGURABLE)
        defineBuiltin("next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayIteratorProto(realm).initialize()

        @ECMAImpl("23.1.5.2.1")
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSArrayIterator)
                Errors.IncompatibleMethodCall("%ArrayIteratorPrototype%.next").throwTypeError()

            val array = thisValue.iteratedArrayLike
                ?: return Operations.createIterResultObject(JSUndefined, true)
            val index = thisValue.arrayLikeNextIndex
            val kind = thisValue.arrayLikeIterationKind
            val len = Operations.lengthOfArrayLike(array)
            if (index >= len) {
                thisValue.iteratedArrayLike = null
                return Operations.createIterResultObject(JSUndefined, true)
            }

            thisValue.arrayLikeNextIndex++
            if (kind == PropertyKind.Key) {
                return Operations.createIterResultObject(index.toValue(), false)
            }

            val elementValue = array.get(index)

            return if (kind == PropertyKind.Value) {
                Operations.createIterResultObject(elementValue, false)
            } else {
                val listArray = Operations.createArrayFromList(listOf(index.toValue(), elementValue))
                Operations.createIterResultObject(listArray, false)
            }
        }
    }
}
