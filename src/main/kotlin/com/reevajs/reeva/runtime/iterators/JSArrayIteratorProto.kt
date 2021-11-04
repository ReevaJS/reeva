package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
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
        defineBuiltin("next", 0, ReevaBuiltin.ArrayIteratorProtoNext)
    }

    companion object {
        fun create(realm: Realm) = JSArrayIteratorProto(realm).initialize()

        @ECMAImpl("23.1.5.2.1")
        @JvmStatic
        fun next(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSArrayIterator)
                Errors.IncompatibleMethodCall("%ArrayIteratorPrototype%.next").throwTypeError(realm)

            val array = thisValue.iteratedArrayLike
                ?: return Operations.createIterResultObject(realm, JSUndefined, true)
            val index = thisValue.arrayLikeNextIndex
            val kind = thisValue.arrayLikeIterationKind
            val len = Operations.lengthOfArrayLike(realm, array)
            if (index >= len) {
                thisValue.iteratedArrayLike = null
                return Operations.createIterResultObject(realm, JSUndefined, true)
            }

            thisValue.arrayLikeNextIndex++
            if (kind == PropertyKind.Key) {
                return Operations.createIterResultObject(realm, index.toValue(), false)
            }

            val elementValue = array.get(index)

            return if (kind == PropertyKind.Value) {
                Operations.createIterResultObject(realm, elementValue, false)
            } else {
                val listArray = Operations.createArrayFromList(realm, listOf(index.toValue(), elementValue))
                Operations.createIterResultObject(realm, listArray, false)
            }
        }
    }
}
