package com.reevajs.reeva.runtime.wrappers.strings

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSStringIteratorProto private constructor(realm: Realm) : JSObject(realm.iteratorProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "String Iterator".toValue(), attrs { +conf; -enum; -writ })
        defineBuiltin(realm, "next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm) = JSStringIteratorProto(realm).initialize(realm)

        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSStringIteratorObject)
                Errors.IncompatibleMethodCall("%StringIterator%.prototype.next").throwTypeError()

            val string = thisValue.string
                ?: return Operations.createIterResultObject(JSUndefined, true)

            val index = thisValue.nextIndex
            if (index >= string.length) {
                thisValue.string = null
                return Operations.createIterResultObject(JSUndefined, true)
            }

            return Operations.createIterResultObject(string[thisValue.nextIndex++].toValue(), false)
        }
    }
}
