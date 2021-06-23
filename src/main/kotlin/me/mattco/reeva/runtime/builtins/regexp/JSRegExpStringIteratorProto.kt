package me.mattco.reeva.runtime.builtins.regexp

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue

class JSRegExpStringIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.iteratorProto) {
    override fun init() {
        super.init()

        defineOwnProperty(Realm.`@@toStringTag`, "RegExp String Iterator".toValue(), Descriptor.CONFIGURABLE or Descriptor.HAS_BASIC)
        defineNativeFunction("next", 0, ::next)
    }

    fun next(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (thisValue !is JSRegExpStringIterator)
            Errors.IncompatibleMethodCall("%RegExpStringIterator%.prototype.next").throwTypeError(realm)
        if (thisValue.done)
            return Operations.createIterResultObject(realm, JSUndefined, true)

        val match = Operations.regExpExec(realm, thisValue, thisValue.iteratedString, ".next")
        if (match == JSNull) {
            thisValue.done = true
            return Operations.createIterResultObject(realm, JSUndefined, true)
        }

        expect(match is JSObject)

        return if (thisValue.global) {
            val matchStr = Operations.toString(realm, match.get(0)).string
            if (matchStr == "") {
                val thisIndex = Operations.toLength(realm, thisValue.get("lastIndex")).asInt
                // TODO: AdvanceStringIndex
                val nextIndex = thisIndex + 1
                Operations.set(realm, thisValue, "lastIndex".key(), nextIndex.toValue(), true)
            }
            Operations.createIterResultObject(realm, match, false)
        } else {
            thisValue.done = true
            Operations.createIterResultObject(realm, match, false)
        }
    }

    companion object {
        fun create(realm: Realm) = JSRegExpStringIteratorProto(realm).initialize()
    }
}
