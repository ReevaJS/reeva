package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert

class JSListIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineBuiltin("next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSListIteratorProto(realm).initialize()

        // TODO: Spec doesn't say this is an actual method
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            ecmaAssert(thisValue is JSListIterator)
            if (thisValue.nextIndex >= thisValue.iteratorList.size)
                return AOs.createIterResultObject(JSUndefined, true)
            return AOs.createIterResultObject(thisValue.iteratorList[thisValue.nextIndex], false).also {
                thisValue.nextIndex++
            }
        }
    }
}
