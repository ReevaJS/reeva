package me.mattco.reeva.runtime.values.iterators

import me.mattco.reeva.runtime.Agent.Companion.ifError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.throwError
import me.mattco.reeva.utils.toValue

class JSArrayIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineOwnProperty(realm.`@@toStringTag`, "Array Iterator".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("next", 0)
    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSArrayIterator) {
            throwError<JSTypeErrorObject>("message: TODO")
            return INVALID_VALUE
        }

        val array = thisValue.iteratedArrayLike ?: return Operations.createIterResultObject(JSUndefined, true)
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

        val elementKey = index.toValue()
        ifError { return INVALID_VALUE }
        val elementValue = array.get(index)
        ifError { return INVALID_VALUE }

        if (kind == PropertyKind.Value) {
            return Operations.createIterResultObject(elementValue, false)
        } else {
            val listArray = Operations.createArrayFromList(listOf(index.toValue(), elementValue))
            ifError { return INVALID_VALUE }
            return Operations.createIterResultObject(listArray, false)
        }
    }

    companion object {
        fun create(realm: Realm) = JSArrayIteratorProto(realm).also { it.init() }
    }
}
