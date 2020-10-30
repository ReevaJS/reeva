package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.throwTypeError
import me.mattco.reeva.utils.toValue

class JSArrayIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineOwnProperty(Realm.`@@toStringTag`, "Array Iterator".toValue(), Descriptor.CONFIGURABLE)
    }

    @JSMethod("next", 0)
    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        if (thisValue !is JSArrayIterator)
            throwTypeError("message: TODO")

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

        val elementValue = array.get(index)

        return if (kind == PropertyKind.Value) {
            Operations.createIterResultObject(elementValue, false)
        } else {
            val listArray = Operations.createArrayFromList(listOf(index.toValue(), elementValue))
            Operations.createIterResultObject(listArray, false)
        }
    }

    companion object {
        fun create(realm: Realm) = JSArrayIteratorProto(realm).also { it.init() }
    }
}
