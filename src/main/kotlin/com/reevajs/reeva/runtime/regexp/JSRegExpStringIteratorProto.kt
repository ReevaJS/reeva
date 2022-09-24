package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.runtime.toLength
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSRegExpStringIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(
            Realm.WellKnownSymbols.toStringTag,
            "RegExp String Iterator".toValue(),
            Descriptor.CONFIGURABLE or Descriptor.HAS_BASIC
        )
        defineBuiltin("next", 0, ::next)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSRegExpStringIteratorProto(realm).initialize()

        @ECMAImpl("22.2.7.2.1")
        @JvmStatic
        fun next(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSRegExpStringIterator)
                Errors.IncompatibleMethodCall("%RegExpStringIterator%.prototype.next").throwTypeError()
            if (thisValue.done)
                return Operations.createIterResultObject(JSUndefined, true)

            val match = Operations.regExpExec(thisValue, thisValue.iteratedString, ".next")
            if (match == JSNull) {
                thisValue.done = true
                return Operations.createIterResultObject(JSUndefined, true)
            }

            expect(match is JSObject)

            return if (thisValue.global) {
                val matchStr = match.get(0).toJSString().string
                if (matchStr == "") {
                    val thisIndex = thisValue.get("lastIndex").toLength().asInt
                    // TODO: AdvanceStringIndex
                    val nextIndex = thisIndex + 1
                    Operations.set(thisValue, "lastIndex".key(), nextIndex.toValue(), true)
                }
                Operations.createIterResultObject(match, false)
            } else {
                thisValue.done = true
                Operations.createIterResultObject(match, false)
            }
        }
    }
}
