package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.utils.Errors
import org.jcodings.specific.UTF8Encoding
import org.joni.Option
import org.joni.Regex
import org.joni.Syntax
import org.joni.constants.MetaChar
import org.joni.constants.SyntaxProperties.*
import org.joni.exception.ErrorMessages
import org.joni.exception.SyntaxException
import org.joni.exception.ValueException

class JSRegExpObject private constructor(
    realm: Realm,
    source: String,
    flags: String,
) : JSObject(realm, realm.regExpProto) {
    val source by slot(SlotName.OriginalSource, source)
    val flags by slot(SlotName.OriginalFlags, flags)
    var regex by lateinitSlot<Regex>(SlotName.RegExpMatcher)

    init {
        val invalidFlag = flags.firstOrNull { Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null)
            Errors.RegExp.InvalidFlag(invalidFlag).throwSyntaxError(realm)
        if (flags.toCharArray().distinct().size != flags.length)
            Errors.RegExp.DuplicateFlag.throwSyntaxError(realm)

        regex = makeClosure(realm, source, flags)
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
        private val metaCharTable = Syntax.MetaCharTable(
            '\\'.code, /* esc */
            MetaChar.INEFFECTIVE_META_CHAR, /* anychar '.' */
            MetaChar.INEFFECTIVE_META_CHAR, /* anytime '*' */
            MetaChar.INEFFECTIVE_META_CHAR, /* zero or one time '?' */
            MetaChar.INEFFECTIVE_META_CHAR, /* one or more time '+' */
            MetaChar.INEFFECTIVE_META_CHAR /* anychar anytime */
        )

        private fun makeSyntax(flags: String): Syntax {
            var op =
                GNU_REGEX_OP or
                    OP_QMARK_NON_GREEDY or
                    OP_ESC_OCTAL3 or
                    OP_ESC_X_HEX2 or
                    OP_ESC_CONTROL_CHARS or
                    OP_ESC_C_CONTROL or
                    OP_DECIMAL_BACKREF or
                    OP_ESC_D_DIGIT or
                    OP_ESC_S_WHITE_SPACE or
                    OP_ESC_W_WORD and
                    OP_ESC_LTGT_WORD_BEGIN_END.inv()

            if (Flag.DotAll.char in flags)
                op = op or OP_DOT_ANYCHAR

            val op2 =
                CONTEXT_INDEP_ANCHORS or
                    CONTEXT_INDEP_REPEAT_OPS or
                    CONTEXT_INVALID_REPEAT_OPS or
                    ALLOW_INVALID_INTERVAL or
                    BACKSLASH_ESCAPE_IN_CC or
                    ALLOW_DOUBLE_RANGE_OP_IN_CC or
                    DIFFERENT_LEN_ALT_LOOK_BEHIND or
                    OP2_QMARK_LT_NAMED_GROUP

            val behavior = DIFFERENT_LEN_ALT_LOOK_BEHIND

            return Syntax("Reeva RegExp", op, op2, behavior, 0, 0, metaCharTable)
        }

        @ECMAImpl("N/A", name = "The [[RegExpMatcher]] Abstract Closure")
        fun makeClosure(realm: Realm, source: String, flags: String): Regex {
            var options = 0
            if (Flag.IgnoreCase.char in flags)
                options = options or Option.IGNORECASE
            if (Flag.Unicode.char in flags)
                TODO()

            options = if (Flag.Multiline.char in flags) {
                options or Option.NEGATE_SINGLELINE
            } else {
                options or Option.SINGLELINE
            }

            try {
                return Regex(source.toByteArray(), 0, source.length, options, UTF8Encoding.INSTANCE, makeSyntax(flags))
            } catch (e: ValueException) {
                when (e.message) {
                    ErrorMessages.EMPTY_RANGE_IN_CHAR_CLASS -> Errors.RegExp.InvalidRange
                    ErrorMessages.UPPER_SMALLER_THAN_LOWER_IN_REPEAT_RANGE -> Errors.RegExp.BackwardsBraceQuantifier
                    else -> throw e
                }.throwSyntaxError(realm)
            } catch (e: SyntaxException) {
                Errors.Custom("Bad RegExp pattern: ${e.message}").throwSyntaxError(realm)
            }
        }

        @JvmStatic
        fun create(realm: Realm, source: String, flags: String) = JSRegExpObject(realm, source, flags).initialize()
    }
}
