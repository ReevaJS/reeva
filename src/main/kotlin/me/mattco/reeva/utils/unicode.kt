package me.mattco.reeva.utils

/* general categories */
fun Char.isCombiningSpacingMark() = Character.getType(this) == Character.COMBINING_SPACING_MARK.toInt()
fun Char.isConnectorPunctuation() = Character.getType(this) == Character.CONNECTOR_PUNCTUATION.toInt()
fun Char.isControl() = Character.getType(this) == Character.CONTROL.toInt()
fun Char.isCurrencySymbol() = Character.getType(this) == Character.CURRENCY_SYMBOL.toInt()
fun Char.isDashPunctuation() = Character.getType(this) == Character.DASH_PUNCTUATION.toInt()
fun Char.isDecimalDigitNumber() = Character.getType(this) == Character.DECIMAL_DIGIT_NUMBER.toInt()
fun Char.isEnclosingMark() = Character.getType(this) == Character.ENCLOSING_MARK.toInt()
fun Char.isEndPunctuation() = Character.getType(this) == Character.END_PUNCTUATION.toInt()
fun Char.isFinalQuotePunctuation() = Character.getType(this) == Character.FINAL_QUOTE_PUNCTUATION.toInt()
fun Char.isFormat() = Character.getType(this) == Character.FORMAT.toInt()
fun Char.isInitialQuotePunctuation() = Character.getType(this) == Character.INITIAL_QUOTE_PUNCTUATION.toInt()
fun Char.isLetterNumber() = Character.getType(this) == Character.LETTER_NUMBER.toInt()
fun Char.isLineSeparator() = Character.getType(this) == Character.LINE_SEPARATOR.toInt()
fun Char.isLowercaseLetter() = Character.getType(this) == Character.LOWERCASE_LETTER.toInt()
fun Char.isMathSymbol() = Character.getType(this) == Character.MATH_SYMBOL.toInt()
fun Char.isModifierLetter() = Character.getType(this) == Character.MODIFIER_LETTER.toInt()
fun Char.isModifierSymbol() = Character.getType(this) == Character.MODIFIER_SYMBOL.toInt()
fun Char.isNonSpacingMark() = Character.getType(this) == Character.NON_SPACING_MARK.toInt()
fun Char.isOtherLetter() = Character.getType(this) == Character.OTHER_LETTER.toInt()
fun Char.isOtherNumber() = Character.getType(this) == Character.OTHER_NUMBER.toInt()
fun Char.isOtherPunctuation() = Character.getType(this) == Character.OTHER_PUNCTUATION.toInt()
fun Char.isOtherSymbol() = Character.getType(this) == Character.OTHER_SYMBOL.toInt()
fun Char.isParagraphSeparator() = Character.getType(this) == Character.PARAGRAPH_SEPARATOR.toInt()
fun Char.isPrivateUse() = Character.getType(this) == Character.PRIVATE_USE.toInt()
// TODO: Use an updated unicode standard for all of this, so we don't have to add special cases
// such as this mongolian vowel separator
fun Char.isSpaceSeparator() = Character.getType(this) == Character.SPACE_SEPARATOR.toInt() && this != '\u180e'
fun Char.isStartPunctuation() = Character.getType(this) == Character.START_PUNCTUATION.toInt()
fun Char.isSurrogate() = Character.getType(this) == Character.SURROGATE.toInt()
fun Char.isTitlecaseLetter() = Character.getType(this) == Character.TITLECASE_LETTER.toInt()
fun Char.isUnassigned() = Character.getType(this) == Character.UNASSIGNED.toInt()
fun Char.isUppercaseLetter() = Character.getType(this) == Character.UPPERCASE_LETTER.toInt()

/* general category shorthand */
fun Char.isCategoryLu() = this.isUppercaseLetter()
fun Char.isCategoryLl() = this.isLowercaseLetter()
fun Char.isCategoryLt() = this.isTitlecaseLetter()
fun Char.isCategoryLm() = this.isModifierLetter()
fun Char.isCategoryLo() = this.isOtherLetter()

fun Char.isCategoryMn() = this.isNonSpacingMark()
fun Char.isCategoryMc() = this.isCombiningSpacingMark()
fun Char.isCategoryMe() = this.isEnclosingMark()

fun Char.isCategoryNd() = this.isDecimalDigitNumber()
fun Char.isCategoryNl() = this.isLetterNumber()
fun Char.isCategoryNo() = this.isOtherNumber()

fun Char.isCategoryPc() = this.isConnectorPunctuation()
fun Char.isCategoryPd() = this.isDashPunctuation()
fun Char.isCategoryPs() = this.isStartPunctuation()
fun Char.isCategoryPe() = this.isEndPunctuation()
fun Char.isCategoryPi() = this.isInitialQuotePunctuation()
fun Char.isCategoryPf() = this.isFinalQuotePunctuation()
fun Char.isCategoryPo() = this.isOtherPunctuation()

fun Char.isCategorySm() = this.isMathSymbol()
fun Char.isCategorySc() = this.isCurrencySymbol()
fun Char.isCategorySk() = this.isModifierSymbol()
fun Char.isCategorySo() = this.isOtherSymbol()

fun Char.isCategoryZs() = this.isSpaceSeparator()
fun Char.isCategoryZl() = this.isLineSeparator()
fun Char.isCategoryZp() = this.isParagraphSeparator()

fun Char.isCategoryCc() = this.isControl()
fun Char.isCategoryCf() = this.isFormat()
fun Char.isCategoryCs() = this.isSurrogate()
fun Char.isCategoryCo() = this.isPrivateUse()
fun Char.isCategoryCn() = this.isUnassigned()

/* specific categories */
val isWhitespaceCache = mutableMapOf<Char, Boolean>()
fun Char.isWhiteSpace() = isWhitespaceCache.getOrPut(this) {
    this.toInt().let {
        it in 0x9..0xd || it == 0x20 || it == 0x85 || it == 0xa0 || it == 0x1680 ||
            it in 0x2000..0x200a || it == 0x2028 || it == 0x2029 || it == 0x202f ||
            it == 0x205f || it == 0x3000
    }
}

val isBidiControlCache = mutableMapOf<Char, Boolean>()
fun Char.isBidiControl() = isBidiControlCache.getOrPut(this) {
    this.toInt().let {
        it == 0x61c || it in 0x200e..0x200f || it in 0x202a..0x202e || it in 0x2066..0x2069
    }
}

fun Char.isJoinControl() = this.toInt().let { it == 0x200c || it == 0x200d }

val isDashCache = mutableMapOf<Char, Boolean>()
fun Char.isDash() = isDashCache.getOrPut(this) {
    this.toInt().let {
        it == 0x002D ||
            it == 0x058A ||
            it == 0x05BE ||
            it == 0x1400 ||
            it == 0x1806 ||
            it in 0x2010..0x2015 ||
            it == 0x2053 ||
            it == 0x207B ||
            it == 0x208B ||
            it == 0x2212 ||
            it == 0x2E17 ||
            it == 0x2E1A ||
            it in 0x2E3A..0x2E3B ||
            it == 0x2E40 ||
            it == 0x301C ||
            it == 0x3030 ||
            it == 0x30A0 ||
            it in 0xFE31..0xFE32 ||
            it == 0xFE58 ||
            it == 0xFE63 ||
            it == 0xFF0D ||
            it == 0x10EAD
    }
}

val isHyphenCache = mutableMapOf<Char, Boolean>()
fun Char.isHyphen() = isHyphenCache.getOrPut(this) {
    this.toInt().let {
        it == 0x002D ||
            it == 0x00AD ||
            it == 0x058A ||
            it == 0x1806 ||
            it in 0x2010..0x2011 ||
            it == 0x2E17 ||
            it == 0x30FB ||
            it == 0xFE63 ||
            it == 0xFF0D ||
            it == 0xFF65
    }
}

val isPatternSyntaxCache = mutableMapOf<Char, Boolean>()
fun Char.isPatternSyntax() = isPatternSyntaxCache.getOrPut(this) {
    this.toInt().let {
        it in 0x0021..0x0023 ||
            it == 0x0024 ||
            it in 0x0025..0x0027 ||
            it == 0x0028 ||
            it == 0x0029 ||
            it == 0x002A ||
            it == 0x002B ||
            it == 0x002C ||
            it == 0x002D ||
            it in 0x002E..0x002F ||
            it in 0x003A..0x003B ||
            it in 0x003C..0x003E ||
            it in 0x003F..0x0040 ||
            it == 0x005B ||
            it == 0x005C ||
            it == 0x005D ||
            it == 0x005E ||
            it == 0x0060 ||
            it == 0x007B ||
            it == 0x007C ||
            it == 0x007D ||
            it == 0x007E ||
            it == 0x00A1 ||
            it in 0x00A2..0x00A5 ||
            it == 0x00A6 ||
            it == 0x00A7 ||
            it == 0x00A9 ||
            it == 0x00AB ||
            it == 0x00AC ||
            it == 0x00AE ||
            it == 0x00B0 ||
            it == 0x00B1 ||
            it == 0x00B6 ||
            it == 0x00BB ||
            it == 0x00BF ||
            it == 0x00D7 ||
            it == 0x00F7 ||
            it in 0x2010..0x2015 ||
            it in 0x2016..0x2017 ||
            it == 0x2018 ||
            it == 0x2019 ||
            it == 0x201A ||
            it in 0x201B..0x201C ||
            it == 0x201D ||
            it == 0x201E ||
            it == 0x201F ||
            it in 0x2020..0x2027 ||
            it in 0x2030..0x2038 ||
            it == 0x2039 ||
            it == 0x203A ||
            it in 0x203B..0x203E ||
            it in 0x2041..0x2043 ||
            it == 0x2044 ||
            it == 0x2045 ||
            it == 0x2046 ||
            it in 0x2047..0x2051 ||
            it == 0x2052 ||
            it == 0x2053 ||
            it in 0x2055..0x205E ||
            it in 0x2190..0x2194 ||
            it in 0x2195..0x2199 ||
            it in 0x219A..0x219B ||
            it in 0x219C..0x219F ||
            it == 0x21A0 ||
            it in 0x21A1..0x21A2 ||
            it == 0x21A3 ||
            it in 0x21A4..0x21A5 ||
            it == 0x21A6 ||
            it in 0x21A7..0x21AD ||
            it == 0x21AE ||
            it in 0x21AF..0x21CD ||
            it in 0x21CE..0x21CF ||
            it in 0x21D0..0x21D1 ||
            it == 0x21D2 ||
            it == 0x21D3 ||
            it == 0x21D4 ||
            it in 0x21D5..0x21F3 ||
            it in 0x21F4..0x22FF ||
            it in 0x2300..0x2307 ||
            it == 0x2308 ||
            it == 0x2309 ||
            it == 0x230A ||
            it == 0x230B ||
            it in 0x230C..0x231F ||
            it in 0x2320..0x2321 ||
            it in 0x2322..0x2328 ||
            it == 0x2329 ||
            it == 0x232A ||
            it in 0x232B..0x237B ||
            it == 0x237C ||
            it in 0x237D..0x239A ||
            it in 0x239B..0x23B3 ||
            it in 0x23B4..0x23DB ||
            it in 0x23DC..0x23E1 ||
            it in 0x23E2..0x2426 ||
            it in 0x2427..0x243F ||
            it in 0x2440..0x244A ||
            it in 0x244B..0x245F ||
            it in 0x2500..0x25B6 ||
            it == 0x25B7 ||
            it in 0x25B8..0x25C0 ||
            it == 0x25C1 ||
            it in 0x25C2..0x25F7 ||
            it in 0x25F8..0x25FF ||
            it in 0x2600..0x266E ||
            it == 0x266F ||
            it in 0x2670..0x2767 ||
            it == 0x2768 ||
            it == 0x2769 ||
            it == 0x276A ||
            it == 0x276B ||
            it == 0x276C ||
            it == 0x276D ||
            it == 0x276E ||
            it == 0x276F ||
            it == 0x2770 ||
            it == 0x2771 ||
            it == 0x2772 ||
            it == 0x2773 ||
            it == 0x2774 ||
            it == 0x2775 ||
            it in 0x2794..0x27BF ||
            it in 0x27C0..0x27C4 ||
            it == 0x27C5 ||
            it == 0x27C6 ||
            it in 0x27C7..0x27E5 ||
            it == 0x27E6 ||
            it == 0x27E7 ||
            it == 0x27E8 ||
            it == 0x27E9 ||
            it == 0x27EA ||
            it == 0x27EB ||
            it == 0x27EC ||
            it == 0x27ED ||
            it == 0x27EE ||
            it == 0x27EF ||
            it in 0x27F0..0x27FF ||
            it in 0x2800..0x28FF ||
            it in 0x2900..0x2982 ||
            it == 0x2983 ||
            it == 0x2984 ||
            it == 0x2985 ||
            it == 0x2986 ||
            it == 0x2987 ||
            it == 0x2988 ||
            it == 0x2989 ||
            it == 0x298A ||
            it == 0x298B ||
            it == 0x298C ||
            it == 0x298D ||
            it == 0x298E ||
            it == 0x298F ||
            it == 0x2990 ||
            it == 0x2991 ||
            it == 0x2992 ||
            it == 0x2993 ||
            it == 0x2994 ||
            it == 0x2995 ||
            it == 0x2996 ||
            it == 0x2997 ||
            it == 0x2998 ||
            it in 0x2999..0x29D7 ||
            it == 0x29D8 ||
            it == 0x29D9 ||
            it == 0x29DA ||
            it == 0x29DB ||
            it in 0x29DC..0x29FB ||
            it == 0x29FC ||
            it == 0x29FD ||
            it in 0x29FE..0x2AFF ||
            it in 0x2B00..0x2B2F ||
            it in 0x2B30..0x2B44 ||
            it in 0x2B45..0x2B46 ||
            it in 0x2B47..0x2B4C ||
            it in 0x2B4D..0x2B73 ||
            it in 0x2B74..0x2B75 ||
            it in 0x2B76..0x2B95 ||
            it == 0x2B96 ||
            it in 0x2B97..0x2BFF ||
            it in 0x2E00..0x2E01 ||
            it == 0x2E02 ||
            it == 0x2E03 ||
            it == 0x2E04 ||
            it == 0x2E05 ||
            it in 0x2E06..0x2E08 ||
            it == 0x2E09 ||
            it == 0x2E0A ||
            it == 0x2E0B ||
            it == 0x2E0C ||
            it == 0x2E0D ||
            it in 0x2E0E..0x2E16 ||
            it == 0x2E17 ||
            it in 0x2E18..0x2E19 ||
            it == 0x2E1A ||
            it == 0x2E1B ||
            it == 0x2E1C ||
            it == 0x2E1D ||
            it in 0x2E1E..0x2E1F ||
            it == 0x2E20 ||
            it == 0x2E21 ||
            it == 0x2E22 ||
            it == 0x2E23 ||
            it == 0x2E24 ||
            it == 0x2E25 ||
            it == 0x2E26 ||
            it == 0x2E27 ||
            it == 0x2E28 ||
            it == 0x2E29 ||
            it in 0x2E2A..0x2E2E ||
            it == 0x2E2F ||
            it in 0x2E30..0x2E39 ||
            it in 0x2E3A..0x2E3B ||
            it in 0x2E3C..0x2E3F ||
            it == 0x2E40 ||
            it == 0x2E41 ||
            it == 0x2E42 ||
            it in 0x2E43..0x2E4F ||
            it in 0x2E50..0x2E51 ||
            it == 0x2E52 ||
            it in 0x2E53..0x2E7F ||
            it in 0x3001..0x3003 ||
            it == 0x3008 ||
            it == 0x3009 ||
            it == 0x300A ||
            it == 0x300B ||
            it == 0x300C ||
            it == 0x300D ||
            it == 0x300E ||
            it == 0x300F ||
            it == 0x3010 ||
            it == 0x3011 ||
            it in 0x3012..0x3013 ||
            it == 0x3014 ||
            it == 0x3015 ||
            it == 0x3016 ||
            it == 0x3017 ||
            it == 0x3018 ||
            it == 0x3019 ||
            it == 0x301A ||
            it == 0x301B ||
            it == 0x301C ||
            it == 0x301D ||
            it in 0x301E..0x301F ||
            it == 0x3020 ||
            it == 0x3030 ||
            it == 0xFD3E ||
            it == 0xFD3F ||
            it in 0xFE45..0xFE46
    }
}

val isPatternWhitespaceCache = mutableMapOf<Char, Boolean>()
fun Char.isPatternWhitespace() = isPatternWhitespaceCache.getOrPut(this) {
    this.toInt().let {
        it in 0x0009..0x000d ||
            it == 0x0020 ||
            it == 0x0085 ||
            it in 0x200e..0x200f ||
            it == 0x2028 ||
            it == 0x2029
    }
}

val isOtherIdStartCache = mutableMapOf<Char, Boolean>()
fun Char.isOtherIdStart() = isOtherIdStartCache.getOrPut(this) {
    this.toInt().let {
        it in 0x1885..0x1886 || it == 0x2118 || it == 0x212e || it in 0x309b..0x309c
    }
}

val isOtherIdContinueCache = mutableMapOf<Char, Boolean>()
fun Char.isOtherIdContinue() = isOtherIdContinueCache.getOrPut(this) {
    this.toInt().let {
        it == 0x00b7 || it == 0x0387 || it in 0x1369..0x1371 || it == 0x19da
    }
}

val isIdStartCache = mutableMapOf<Char, Boolean>()
fun Char.isIdStart() = isIdStartCache.getOrPut(this) {
    (isUppercaseLetter() || isLowercaseLetter() || isTitlecaseLetter() || isModifierLetter() ||
        isOtherLetter() || isLetterNumber() || isOtherIdStart()) &&
        !(isPatternSyntax() || isPatternWhitespace())
}

val isIdContinueCache = mutableMapOf<Char, Boolean>()
fun Char.isIdContinue() = isIdContinueCache.getOrPut(this) {
    (isIdStart() || isCategoryMn() || isCategoryMc() || isCategoryNd() || isCategoryPc() ||
        isOtherIdContinue()) && !(isPatternSyntax() || isPatternWhitespace())
}
