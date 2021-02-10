package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.ecmaAssert

class JSListIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()
        defineNativeFunction("next", 0, ::next)
    }

    // TODO: Spec doesn't say this is an actual method
    fun next(arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
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
