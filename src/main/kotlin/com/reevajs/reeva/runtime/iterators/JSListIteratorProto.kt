package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert

class JSListIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineNativeFunction("next", 0, ::next)
    }

    // TODO: Spec doesn't say this is an actual method
    fun next(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        ecmaAssert(thisValue is JSListIterator)
        if (thisValue.nextIndex >= thisValue.iteratorList.size)
            return Operations.createIterResultObject(realm, JSUndefined, true)
        return Operations.createIterResultObject(realm, thisValue.iteratorList[thisValue.nextIndex], false).also {
            thisValue.nextIndex++
        }
    }

    companion object {
        fun create(realm: Realm) = JSListIteratorProto(realm).initialize()
    }
}
