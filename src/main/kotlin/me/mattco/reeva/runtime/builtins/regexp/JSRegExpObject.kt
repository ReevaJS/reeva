package me.mattco.reeva.runtime.builtins.regexp

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.Errors
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
    val originalSource: String,
    val originalFlags: String,
) : JSObject(realm, realm.regExpProto) {
    val flags: List<Flag>

    lateinit var regex: Regex

    init {
        val invalidFlag = originalFlags.firstOrNull { Flag.values().none { flag -> flag.char == it } }
        if (invalidFlag != null)
            Errors.RegExp.InvalidFlag(invalidFlag).throwSyntaxError()
        if (originalFlags.toCharArray().distinct().size != originalFlags.length)
            Errors.RegExp.DuplicateFlag.throwSyntaxError()

        flags = originalFlags.map { flag -> Flag.values().first { it.char == flag } }

        var options = 0
        if (Flag.IgnoreCase in flags)
            options = options or Option.IGNORECASE
        if (Flag.Unicode in flags)
            TODO()

        options = if (Flag.Multiline in flags) {
            options or Option.NEGATE_SINGLELINE
        } else {
            options or Option.SINGLELINE
        }

        try {
            regex = Regex(originalSource.toByteArray(), 0, originalSource.length, options, UTF8Encoding.INSTANCE, makeSyntax())
        } catch (e: ValueException) {
            when (e.message) {
                ErrorMessages.ERR_EMPTY_RANGE_IN_CHAR_CLASS -> Errors.RegExp.InvalidRange
                ErrorMessages.ERR_UPPER_SMALLER_THAN_LOWER_IN_REPEAT_RANGE -> Errors.RegExp.BackwardsBraceQuantifier
                else -> throw e
            }.throwSyntaxError()
        } catch (e: SyntaxException) {
            when (e.message) {
                ErrorMessages.ERR_END_PATTERN_AT_ESCAPE -> Errors.RegExp.BadEscape
                else -> throw e
            }.throwSyntaxError()
        }
    }

    enum class Flag(val char: Char) {
        IgnoreCase('i'),
        Global('g'),
        Multiline('m'),
        DotAll('s'),
        Unicode('u'),
        Sticky('y'),
    }

    private fun makeSyntax(): Syntax {
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

        if (Flag.DotAll in flags)
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

        return Syntax(op, op2, behavior, 0, metaCharTable)
    }

    companion object {
        private val metaCharTable = Syntax.MetaCharTable(
            '\\'.toInt(),  /* esc */
            MetaChar.INEFFECTIVE_META_CHAR,  /* anychar '.' */
            MetaChar.INEFFECTIVE_META_CHAR,  /* anytime '*' */
            MetaChar.INEFFECTIVE_META_CHAR,  /* zero or one time '?' */
            MetaChar.INEFFECTIVE_META_CHAR,  /* one or more time '+' */
            MetaChar.INEFFECTIVE_META_CHAR /* anychar anytime */
        )

        @JvmStatic
        fun create(realm: Realm, source: String, flags: String) = JSRegExpObject(realm, source, flags).initialize()
    }
}
