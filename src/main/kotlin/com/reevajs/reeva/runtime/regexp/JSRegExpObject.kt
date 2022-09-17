package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.utils.Errors
import com.reevajs.regexp.RegExp
import com.reevajs.regexp.RegexSyntaxError

class JSRegExpObject private constructor(
    realm: Realm,
    source: String,
    flags: String,
) : JSObject(realm, realm.regExpProto) {
    val source by slot(SlotName.OriginalSource, source)
    val flags by slot(SlotName.OriginalFlags, flags)
    var regex by lateinitSlot<RegExp>(SlotName.RegExpMatcher)

    init {
        val invalidFlag = flags.firstOrNull { Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null)
            Errors.RegExp.InvalidFlag(invalidFlag).throwSyntaxError(realm)
        if (flags.toCharArray().distinct().size != flags.length)
            Errors.RegExp.DuplicateFlag.throwSyntaxError(realm)

        regex = makeClosure(source, flags, realm)
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
        fun makeClosure(source: String, flags: String, realm: Realm = Agent.activeAgent.getActiveRealm()): RegExp {
            val options = mutableSetOf<RegExp.Flag>()

            if (Flag.IgnoreCase.char in flags)
                options.add(RegExp.Flag.Insensitive)
            if (Flag.Multiline.char in flags)
                options.add(RegExp.Flag.MultiLine)
            if (Flag.DotAll.char in flags)
                options.add(RegExp.Flag.DotMatchesNewlines)
            if (Flag.Unicode.char in flags)
                options.add(RegExp.Flag.Unicode)

            try {
                return RegExp(source, *options.toTypedArray())
            } catch (e: RegexSyntaxError) {
                Errors.Custom("Bad RegExp pattern at offset ${e.offset}: ${e.message}").throwSyntaxError(realm)
            }
        }

        @JvmStatic
        fun create(source: String, flags: String, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSRegExpObject(realm, source, flags).initialize()
    }
}
