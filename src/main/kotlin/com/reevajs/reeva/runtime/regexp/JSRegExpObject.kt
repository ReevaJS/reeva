package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.utils.Errors
import com.reevajs.regexp.RegExp

class JSRegExpObject private constructor(
    realm: Realm,
    source: String,
    flags: String,
    regex: RegExp,
) : JSObject(realm.regExpProto) {
    val source by slot(SlotName.OriginalSource, source)
    val flags by slot(SlotName.OriginalFlags, flags)
    var regex by slot(SlotName.RegExpMatcher, regex)

    init {
        val invalidFlag = flags.firstOrNull { Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null)
            Errors.RegExp.InvalidFlag(invalidFlag).throwSyntaxError(realm)
        if (flags.toCharArray().distinct().size != flags.length)
            Errors.RegExp.DuplicateFlag.throwSyntaxError(realm)
    }

    fun hasFlag(flag: Flag) = flag.char in flags

    enum class Flag(val char: Char) {
        IgnoreCase('i'),
        Global('g'),
        Multiline('m'),
        DotAll('s'),
        Unicode('u'),
        Sticky('y'),
    }

    companion object {
        @JvmStatic
        fun create(realm: Realm, source: String, flags: String, regexp: RegExp) =
            JSRegExpObject(realm, source, flags, regexp).initialize(realm)
    }
}
