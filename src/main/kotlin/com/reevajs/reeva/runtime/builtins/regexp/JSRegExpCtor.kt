package com.reevajs.reeva.runtime.builtins.regexp

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key
import com.reevajs.reeva.utils.toValue

class JSRegExpCtor private constructor(realm: Realm) : JSNativeFunction(realm, "RegExp", 2) {
    override fun init() {
        super.init()

        defineNativeAccessor(Realm.`@@species`.key(), attrs { +conf -enum }, ::`get@@species`, null)
    }

    fun `get@@species`(realm: Realm, thisValue: JSValue): JSValue {
        return thisValue
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val pattern = arguments.argument(0)
        val flags = arguments.argument(1)
        val patternIsRegExp = Operations.isRegExp(pattern)

        val currentNewTarget = arguments.newTarget
        val newTarget = if (currentNewTarget == JSUndefined) {
            // TODO: "Let newTarget be the active function object."
            val temp = JSUndefined
            if (patternIsRegExp && flags == JSUndefined) {
                val patternCtor = (pattern as JSObject).get("constructor")
                if (temp.sameValue(patternCtor))
                    return pattern
            }
            temp
        } else currentNewTarget

        val (patternSource, flagSource) = when {
            pattern is JSRegExpObject -> pattern.originalSource.toValue() to flags.ifUndefined { pattern.originalFlags.toValue() }
            patternIsRegExp -> (pattern as JSObject).let {
                it.get("source") to flags.ifUndefined { it.get("flags") }
            }
            else -> pattern to flags
        }

        val obj = Operations.regExpAlloc(realm, newTarget)
        return Operations.regExpInitialize(realm, obj, patternSource, flagSource)
    }

    companion object {
        fun create(realm: Realm) = JSRegExpCtor(realm).initialize()
    }
}
