package me.mattco.reeva.runtime.builtins.regexp

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.attrs
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue

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
