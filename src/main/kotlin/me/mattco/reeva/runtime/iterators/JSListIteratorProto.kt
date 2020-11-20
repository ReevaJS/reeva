package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.toValue

class JSListIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    // TODO: Spec doesn't say this is an actual method
    @JSMethod("next", 0)
    fun next(thisValue: JSValue, arguments: JSArguments): JSValue {
        ecmaAssert(thisValue is JSListIterator)
        if (thisValue.nextIndex >= thisValue.iteratorList.size)
            return Operations.createIterResultObject(JSUndefined, true)
        return Operations.createIterResultObject(thisValue.iteratorList[thisValue.nextIndex], false).also {
            thisValue.nextIndex++
        }
    }

    companion object {
        fun create(realm: Realm) = JSListIteratorProto(realm).initialize()
    }
}
