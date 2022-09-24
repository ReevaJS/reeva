package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert

class JSListIteratorProto private constructor(realm: Realm) : JSObject(realm.iteratorProto) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineBuiltin(realm, "next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm) = JSListIteratorProto(realm).initialize(realm)

        // TODO: Spec doesn't say this is an actual method
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            ecmaAssert(thisValue is JSListIterator)
            if (thisValue.nextIndex >= thisValue.iteratorList.size)
                return Operations.createIterResultObject(JSUndefined, true)
            return Operations.createIterResultObject(thisValue.iteratorList[thisValue.nextIndex], false).also {
                thisValue.nextIndex++
            }
        }
    }
}
