package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.toValue

class JSArrayIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineOwnProperty(Realm.`@@toStringTag`, "Array Iterator".toValue(), Descriptor.CONFIGURABLE)
        defineNativeFunction("next", 0, ::next)
    }

    fun next(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (thisValue !is JSArrayIterator)
            Errors.IncompatibleMethodCall("%ArrayIteratorPrototype%.next").throwTypeError(realm)

        val array = thisValue.iteratedArrayLike ?: return Operations.createIterResultObject(realm, JSUndefined, true)
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

    companion object {
        fun create(realm: Realm) = JSArrayIteratorProto(realm).initialize()
    }
}
