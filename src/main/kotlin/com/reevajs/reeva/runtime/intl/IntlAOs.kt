package com.reevajs.reeva.runtime.intl

import com.ibm.icu.number.FormattedNumber
import com.ibm.icu.number.LocalizedNumberFormatter
import com.ibm.icu.text.Collator
import com.ibm.icu.text.ConstrainedFieldPosition
import com.ibm.icu.text.NumberFormat
import com.ibm.icu.text.NumberingSystem
import com.ibm.icu.util.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.toValue
import com.reevajs.reeva.utils.unreachable
import sun.util.locale.LanguageTag
import java.text.Format
import kotlin.math.max
import kotlin.math.min

object IntlAOs {
    private val deprecatedOrLegacyLanguages = setOf("in", "iw", "ji", "jw", "mo", "sh", "tl", "no")
    private val currencyRegex = """^[A-Z]{3}$""".toRegex()
    private val simpleSanctionedUnits = setOf(
        "acre", "bit", "byte", "celsius", "centimeter",
        "day", "degree", "fahrenheit", "fluid-ounce", "foot",
        "gallon", "gigabit", "gigabyte", "gram", "hectare",
        "hour", "inch", "kilobit", "kilobyte", "kilogram",
        "kilometer", "liter", "megabit", "megabyte", "meter",
        "mile", "mile-scandinavian", "milliliter", "millimeter", "millisecond",
        "minute", "month", "ounce", "percent", "petabyte",
        "pound", "second", "stone", "terabit", "terabyte",
        "week", "yard", "year",
    )

    val numberFormatAvailableLocales = NumberFormat.getAvailableULocales().map(ULocale::toLanguageTag).toSet()

    @ECMAImpl("6.2.2")
    fun isStructurallyValidLanguageTag(locale: String): Boolean {
        return try {
            ULocale.Builder().setLanguageTag(locale).build()
            true
        } catch (e: IllformedLocaleException) {
            false
        }
    }

    fun isStructurallyValidLanguageTag(locale: ULocale): Boolean {
        return try {
            ULocale.Builder().setLocale(locale).build()
            true
        } catch (e: IllformedLocaleException) {
            false
        }
    }

    @ECMAImpl("6.2.3")
    fun canonicalizeUnicodeLocaleId(realm: Realm, locale: String): String {
        if (locale.isEmpty() || !locale.all { it.code <= 127 })
            Errors.Intl.Locale.InvalidLanguageTag(locale).throwRangeError(realm)

        // V8 optimization: Check for non-deprecated 2-letter codes
        if (locale.length == 2 && locale.all { it.isLowerCase() } && locale !in deprecatedOrLegacyLanguages)
            return locale

        val lowercaseLocale = locale.lowercase()
        val ulocale = ULocale.forLanguageTag(lowercaseLocale)

        if (!isStructurallyValidLanguageTag(ulocale))
            Errors.Intl.Locale.InvalidLanguageTag(locale).throwRangeError(realm)

        val tag = ULocale.createCanonical(ulocale).toLanguageTag()
        if (!isStructurallyValidLanguageTag(tag))
            Errors.Intl.Locale.InvalidLanguageTag(locale).throwRangeError(realm)

        return tag
    }

    @ECMAImpl("6.2.4")
    fun defaultLocale(): String {
        return ULocale.getDefault().toLanguageTag()
    }

    @ECMAImpl("6.3.1")
    fun isWellFormedCurrencyCode(currency: String) = currencyRegex.matches(currency)

    data class MeasureUnitPair(val numerator: MeasureUnit, val denominator: MeasureUnit?)

    // This is an implementation of isWellFormedUnitIdentifier which, in addition to
    // performing validation (it returns null if the unit is invalid), will also
    // perform the conversion to ICU MeasureUnits.
    @ECMAImpl("6.5.1")
    fun getWellFormedUnitIdentifierParts(unit: String): MeasureUnitPair? {
        if (isSanctionedSimpleUnitIdentifier(unit))
            return MeasureUnitPair(MeasureUnit.forIdentifier(unit), null)

        val perIndex = unit.indexOf("-per-")
        if (perIndex == -1)
            return null

        val secondPerIndex = unit.indexOf("-per-", perIndex + 5)
        if (secondPerIndex != -1)
            return null

        val numerator = getWellFormedUnitIdentifierParts(unit.substring(0, perIndex))?.let {
            expect(it.denominator == null)
            it.numerator
        } ?: return null

        val denominator = getWellFormedUnitIdentifierParts(unit.substring(perIndex + 5))?.let {
            expect(it.denominator == null)
            it.numerator
        } ?: return null

        return MeasureUnitPair(numerator, denominator)
    }

    @ECMAImpl("6.5.2")
    fun isSanctionedSimpleUnitIdentifier(unit: String): Boolean {
        return unit in simpleSanctionedUnits
    }

    @ECMAImpl("9.2.1")
    fun canonicalizeLocaleList(realm: Realm, locales: JSValue): List<String> {
        if (locales == JSUndefined)
            return emptyList()

        val seen = mutableListOf<String>()

        val obj = if (locales is JSString || (locales is JSObject && locales.hasSlot(SlotName.InitializedLocale))) {
            Operations.createArrayFromList(realm, listOf(locales))
        } else Operations.toObject(realm, locales)

        for (index in Operations.objectIndices(obj)) {
            val value = obj.get(index)
            if (value !is JSString && value !is JSObject)
                Errors.Intl.Locale.InvalidLocaleType.throwTypeError(realm)

            val tag = if (value is JSObject && value.hasSlot(SlotName.InitializedLocale)) {
                value.getSlotAs(SlotName.Locale)
            } else value.toJSString(realm).string

            if (!isStructurallyValidLanguageTag(tag))
                Errors.Intl.Locale.InvalidLanguageTag(tag).throwRangeError(realm)

            val canonicalizedTag = canonicalizeUnicodeLocaleId(realm, tag)
            if (canonicalizedTag !in seen) {
                seen.add(canonicalizedTag)
            }
        }

        return seen
    }

    private fun createULocale(locale: String): ULocale? {
        if (!isStructurallyValidLanguageTag(locale))
            return null

        return ULocale.forLanguageTag(locale)
    }

    @ECMAImpl("9.2.3")
    fun bestAvailableLocale(availableLocales: Set<String>, locale: String): String? {
        var candidate = locale
        while (true) {
            if (candidate in availableLocales)
                return candidate
            var pos = candidate.indexOfLast { it == '-' }
            if (pos == -1)
                return null
            if (pos >= 2 && candidate[pos - 2] == '-')
                pos -= 2
            candidate = candidate.substring(0, pos)
        }
    }

    @ECMAImpl("9.2.3")
    fun lookupMatcher(availableLocales: Set<String>, requestedLocales: List<String>): String {
        for (locale in requestedLocales) {
            val (noExtensionsLocale, possibleExtension) = removeLocaleExtensionSequences(locale)
            val availableLocale = bestAvailableLocale(availableLocales, noExtensionsLocale)
            if (availableLocale != null)
                return noExtensionsLocale + possibleExtension
        }

        return defaultLocale()
    }

    data class RemovedLocaleExtension(val locale: String, val extension: String?)

    private fun removeLocaleExtensionSequences(locale: String): RemovedLocaleExtension {
        val tag = LanguageTag.parse(locale, null)
        val builder = ULocale.Builder()
            .setLanguage(tag.language)
            .setScript(tag.script)
            .setRegion(tag.region)

        if (tag.privateuse.isNotEmpty())
            builder.setExtension(ULocale.PRIVATE_USE_EXTENSION, tag.privateuse.drop(2))

        return RemovedLocaleExtension(builder.build().toLanguageTag(), tag.extensions.firstOrNull())
    }

    @ECMAImpl("9.2.4")
    fun bestFitMatcher(availableLocales: Set<String>, requestedLocales: List<String>): String {
        return buildLocaleMatcher(availableLocales)
            .getBestMatchResult(requestedLocales.map(::ULocale))
            .makeResolvedULocale()
            .toLanguageTag()
    }

    data class ResolvedLocale(val locale: String, val ulocale: ULocale, val extensions: Map<String, String>)

    @ECMAImpl("9.2.7")
    fun resolveLocale(
        availableLocales: Set<String>,
        requestedLocales: List<String>,
        useBestFitMatcher: Boolean,
        relevantExtensionKeys: Set<String>,
    ): ResolvedLocale? {
        val locale = if (useBestFitMatcher) {
            bestFitMatcher(availableLocales, requestedLocales)
        } else lookupMatcher(availableLocales, requestedLocales)

        val ulocale = createULocale(locale) ?: return null
        val (newULocale, extensions) = getUnicodeExtensions(ulocale, relevantExtensionKeys)

        val canonicalizedLocale = newULocale.toLanguageTag()
        return ResolvedLocale(canonicalizedLocale, newULocale, extensions)
    }

    @ECMAImpl("9.2.8")
    fun lookupSupportedLocales(availableLocales: Set<String>, requestedLocales: List<String>): List<String> {
        val subset = mutableListOf<String>()

        for (locale in requestedLocales) {
            val (noExtensionsLocale, possibleExtension) = removeLocaleExtensionSequences(locale)
            val availableLocale = bestAvailableLocale(availableLocales, noExtensionsLocale)
            if (availableLocale != null)
                subset.add(noExtensionsLocale + (possibleExtension ?: ""))
        }

        return subset
    }

    @ECMAImpl("9.2.9")
    fun bestFitSupportedLocales(availableLocales: Set<String>, requestedLocales: List<String>): List<String> {
        val localeMatcher = buildLocaleMatcher(availableLocales)
        val result = mutableListOf<String>()

        for (requestedLocale in requestedLocales) {
            val desired = ULocale.forLanguageTag(requestedLocale)
            val matched = localeMatcher.getBestMatchResult(desired)
            if (matched.supportedIndex < 0)
                continue

            result.add(desired.toLanguageTag())
        }

        return result
    }

    @ECMAImpl("9.2.10")
    fun supportedLocales(
        realm: Realm,
        availableLocales: Set<String>,
        requestedLocales: List<String>,
        optionsValue: JSValue,
    ): JSValue {
        val options = coerceOptionsToObject(realm, optionsValue)
        val matcher = getOption(realm, options, "localeMatcher", false, setOf("lookup", "best fit")) ?: "best fit"
        val supportedLocales = if (matcher == "best fit") {
            bestFitSupportedLocales(availableLocales, requestedLocales)
        } else lookupSupportedLocales(availableLocales, requestedLocales)

        return Operations.createArrayFromList(realm, supportedLocales.map(String::toValue))
    }

    @ECMAImpl("9.2.12")
    fun coerceOptionsToObject(realm: Realm, options: JSValue): JSObject {
        return if (options == JSUndefined) {
            JSObject.create(realm, JSNull)
        } else options.toObject(realm)
    }

    @ECMAImpl("9.2.13")
    fun getOption(
        realm: Realm,
        options: JSObject,
        property: String,
        isBoolean: Boolean,
        values: Set<String>,
    ): String? {
        val value = options.get(property)
        if (value == JSUndefined)
            return null

        val str = if (isBoolean) {
            if (value.toBoolean()) "true" else "false"
        } else value.toJSString(realm).string

        if (values.isNotEmpty() && str !in values)
            Errors.Intl.ValueOutOfRange(str, property).throwRangeError(realm)

        return str
    }

    // RangeError is left to the caller
    @ECMAImpl("9.2.14")
    fun defaultNumberOption(realm: Realm, value: JSValue, minimum: Int, maximum: Int, fallback: Int?): Int? {
        if (value == JSUndefined)
            return fallback
        val number = value.toNumber(realm)
        if (number.isNaN || number.asInt < minimum || number.asInt > maximum)
            return null
        return number.asInt
    }

    @ECMAImpl("9.2.15")
    fun getNumberOption(
        realm: Realm,
        options: JSObject,
        property: String,
        minimum: Int,
        maximum: Int,
        fallback: Int,
    ): Int {
        val value = options.get(property)
        val result = defaultNumberOption(realm, value, minimum, maximum, fallback)
        if (result == null)
            Errors.Intl.NumberFormat.NumberOptionRange(property, result, minimum, maximum).throwRangeError(realm)

        return result
    }

    data class NumberFormatDigitOptions(
        val minimumIntegerDigits: Int,
        val minimumFractionDigits: Int,
        val maximumFractionDigits: Int,
        val minimumSignificantDigits: Int,
        val maximumSignificantDigits: Int,
        val roundingType: RoundingType,
    )

    enum class RoundingType {
        SignificantDigits,
        FractionDigits,
        CompactRounding,
    }

    @ECMAImpl("15.1.1")
    fun setNumberFormatDigitOptions(
        realm: Realm,
        options: JSObject,
        mnfdDefault: Int,
        mxfdDefault: Int,
        notationIsCompact: Boolean
    ): NumberFormatDigitOptions {
        var minimumIntegerDigits = 0
        var minimumFractionDigits = 0
        var maximumFractionDigits = 0
        var minimumSignificantDigits = 0
        var maximumSignificantDigits = 0
        val roundingType: RoundingType

        val mnid = getNumberOption(realm, options, "minimumIntegerDigits", 1, 21, 1)
        val mnfd = options.get("minimumFractionDigits")
        val mxfd = options.get("maximumFractionDigits")
        val mnsd = options.get("minimumSignificantDigits")
        val mxsd = options.get("maximumSignificantDigits")

        minimumIntegerDigits = mnid

        if (mnsd != JSUndefined || mxsd != JSUndefined) {
            roundingType = RoundingType.SignificantDigits
            val mnsd2 = defaultNumberOption(realm, mnsd, 1, 21, 1)
            if (mnsd2 == null)
                Errors.Intl.NumberFormat.NumberOptionRange("minimumSignificantDigits", mnsd2, 1, 21).throwRangeError(realm)
            val mxsd2 = defaultNumberOption(realm, mxsd, mnsd2, 21, 1)
            if (mxsd2 == null)
                Errors.Intl.NumberFormat.NumberOptionRange("minimumSignificantDigits", mxsd2, 1, 21).throwRangeError(realm)

            minimumSignificantDigits = mnsd2
            maximumSignificantDigits = mxsd2
        } else if (mnfd != JSUndefined || mxfd != JSUndefined) {
            roundingType = RoundingType.FractionDigits
            var mnfd2 = defaultNumberOption(realm, mnfd, 0, 20, null)
            var mxfd2 = defaultNumberOption(realm, mxfd, 0, 20, null)

            if (mnfd2 == null) {
                mnfd2 = min(mnfdDefault, mxfd2!!)
            } else if (mxfd2 == null) {
                mxfd2 = max(mxfdDefault, mnfd2)
            } else if (mnfd2 > mxfd2) {
                Errors.Intl.NumberFormat.PropertyValueOutOfRange("maximumFractionDigits")
            }

            minimumFractionDigits = mnfd2
            maximumFractionDigits = mxfd2
        } else if (notationIsCompact) {
            roundingType = RoundingType.CompactRounding
        } else {
            roundingType = RoundingType.FractionDigits
            minimumFractionDigits = mnfdDefault
            maximumFractionDigits = mxfdDefault
        }

        return NumberFormatDigitOptions(
            minimumIntegerDigits,
            minimumFractionDigits,
            maximumFractionDigits,
            minimumSignificantDigits,
            maximumSignificantDigits,
            roundingType,
        )
    }

    @ECMAImpl("15.1.3")
    fun currencyDigits(currency: String): Int {
        expect(currency.all(Char::isUpperCase))
        return Currency.getInstance(currency).defaultFractionDigits
    }

    @ECMAImpl("15.1.8")
    fun formatNumeric(numberFormatter: LocalizedNumberFormatter, x: JSValue): JSValue {
        return icuFormat(numberFormatter, x).toString().toValue()
    }

    @ECMAImpl("15.1.9")
    fun formatNumericToParts(realm: Realm, numberFormatter: LocalizedNumberFormatter, x: JSValue): JSValue {
        val formatted = icuFormat(numberFormatter, x)
        val formattedText = formatted.toString()
        val styleIsUnit = styleFromNumberFormatter(numberFormatter) == NumberStyle.Unit

        val result = JSArrayObject.create(realm)

        if (formatted.isEmpty())
            return result

        val regions = mutableListOf(NumberFormatSpan(null, 0, formatted.length))

        val cfp = ConstrainedFieldPosition()
        cfp.constrainClass(NumberFormat.Field::class.java)
        while (formatted.nextPosition(cfp))
            regions.add(NumberFormatSpan(cfp.field, cfp.start, cfp.limit))

        val parts = flattenRegionsToParts(regions)

        for ((index, part) in parts.withIndex()) {
            val fieldType = if (part.field != null) {
                if (styleIsUnit && part.field == NumberFormat.Field.PERCENT) {
                    "unit"
                } else numberFieldToType(part.field, x)
            } else "literal"

            val substring = formattedText.substring(part.beginPos, part.endPos)
            val obj = JSObject.create(realm)
            obj.set("type", fieldType.toValue())
            obj.set("value", substring.toValue())
            result.set(index, obj)
        }

        return result
    }

    private fun flattenRegionsToParts(regions: List<NumberFormatSpan>): List<NumberFormatSpan> {
        val sorted = regions.sortedWith { a, b ->
            when {
                a.field == null -> -1
                b.field == null -> 1
                a.beginPos < b.beginPos -> -1
                a.beginPos > b.beginPos -> 1
                a.endPos < b.endPos -> -1
                a.endPos > b.endPos -> 1
                else -> 1
            }
        }

        val overlappingRegionIndexStack = mutableListOf(0)
        var topRegion = sorted[0]
        var regionIterator = 1
        val entireSize = topRegion.endPos
        val outParts = mutableListOf<NumberFormatSpan>()

        var climber = 0
        while (climber < entireSize) {
            val nextRegionBeginPos = if (regionIterator < regions.size) {
                sorted[regionIterator].beginPos
            } else entireSize

            if (climber < nextRegionBeginPos) {
                while (topRegion.endPos < nextRegionBeginPos) {
                    if (climber < topRegion.endPos) {
                        outParts.add(NumberFormatSpan(topRegion.field, climber, topRegion.endPos))
                        climber = topRegion.endPos
                    }
                    overlappingRegionIndexStack.removeLast()
                    topRegion = sorted[overlappingRegionIndexStack.last()]
                }
                if (climber < nextRegionBeginPos) {
                    outParts.add(NumberFormatSpan(topRegion.field, climber, nextRegionBeginPos))
                    climber = nextRegionBeginPos
                }
            }
            if (regionIterator < sorted.size) {
                overlappingRegionIndexStack.add(regionIterator++)
                topRegion = sorted[overlappingRegionIndexStack.last()]
            }
        }

        return outParts
    }

    private fun numberFieldToType(field: Format.Field, numericObj: JSValue): String = when (field) {
        NumberFormat.Field.INTEGER -> when {
            numericObj.isBigInt || numericObj.isFinite -> "integer"
            numericObj.isNaN -> "NaN"
            numericObj.isInfinite -> "Infinity"
            else -> unreachable()
        }
        NumberFormat.Field.FRACTION -> "fraction"
        NumberFormat.Field.DECIMAL_SEPARATOR -> "decimal"
        NumberFormat.Field.GROUPING_SEPARATOR -> "group"
        NumberFormat.Field.CURRENCY -> "currency"
        NumberFormat.Field.PERCENT -> "percentSign"
        NumberFormat.Field.SIGN -> if (numericObj.isBigInt) {
            if ((numericObj as JSBigInt).number.signum() == -1) "minusSign" else "plusSign"
        } else if (numericObj.asDouble < 0) "minusSign" else "plusSign"
        NumberFormat.Field.EXPONENT_SYMBOL -> "exponentSeparator"
        NumberFormat.Field.EXPONENT_SIGN -> "exponentMinusSign"
        NumberFormat.Field.EXPONENT -> "exponentInteger"
        NumberFormat.Field.PERMILLE -> unreachable()
        NumberFormat.Field.COMPACT -> "compact"
        NumberFormat.Field.MEASURE_UNIT -> "unit"
        else -> unreachable()
    }

    data class NumberFormatSpan(val field: Format.Field?, val beginPos: Int, val endPos: Int)

    private fun styleFromNumberFormatter(numberFormatter: LocalizedNumberFormatter): NumberStyle {
        val skeleton = numberFormatter.toSkeleton()
        return when {
            "currency/" in skeleton -> NumberStyle.Currency
            "percent" in skeleton -> if ("scale/100" in skeleton) {
                NumberStyle.Percent
            } else NumberStyle.Unit
            "unit/" in skeleton -> NumberStyle.Unit
            else -> NumberStyle.Decimal
        }
    }

    enum class NumberStyle {
        Decimal,
        Percent,
        Currency,
        Unit,
    }

    private fun icuFormat(numberFormatter: LocalizedNumberFormatter, x: JSValue): FormattedNumber {
        return if (x.isBigInt) {
            val bigint = (x as JSBigInt).number
            numberFormatter.format(bigint)
        } else {
            expect(x is JSNumber)
            numberFormatter.format(x.asDouble)
        }
    }

    data class UnicodeExtensions(val ulocale: ULocale, val extensions: Map<String, String>)

    private fun getUnicodeExtensions(ulocale: ULocale, relevantKeys: Set<String>): UnicodeExtensions {
        val extensions = mutableMapOf<String, String>()
        val builder = ULocale.Builder().setLocale(ulocale).clearExtensions()
        val keywords = ulocale.keywords ?: return UnicodeExtensions(ulocale, extensions)

        for (keyword in keywords) {
            val value = ulocale.getKeywordValue(keyword)
            val key = ULocale.toUnicodeLocaleKey(value)

            if (key != null && key in relevantKeys) {
                val value = ULocale.toUnicodeLocaleType(key, value)
                val isValidValue = when (key) {
                    "ca" -> isValidCalendar(ulocale, value)
                    "co" -> isValidCollation(ulocale, value)
                    "hc" -> value in setOf("h11", "h12", "h23", "h24")
                    "lb" -> value in setOf("strict", "normal", "loose")
                    "kn" -> value in setOf("true", "false")
                    "kf" -> value in setOf("upper", "lower", "false")
                    "nu" -> isValidNumberingSystem(value)
                    else -> false
                }
                if (isValidValue) {
                    extensions[key] = value
                    builder.setUnicodeLocaleKeyword(key, value)
                }
            }
        }

        return UnicodeExtensions(builder.build(), extensions)
    }

    private fun isValidCalendar(locale: ULocale, value: String): Boolean {
        return isValidExtension(locale, "calendar", value, Calendar::getKeywordValuesForLocale)
    }

    private fun isValidCollation(locale: ULocale, value: String): Boolean {
        if (value == "standard" || value == "search")
            return false
        return isValidExtension(locale, "collation", value, Collator::getKeywordValuesForLocale)
    }

    fun isValidNumberingSystem(value: String): Boolean {
        if (value in setOf("native", "traditio", "finance"))
            return false
        return NumberingSystem.getInstanceByName(value) != null
    }

    private fun isValidExtension(
        locale: ULocale,
        key: String,
        value: String,
        keywordValuesProducer: (String, ULocale, Boolean) -> Array<String>,
    ): Boolean {
        val legacyType = ULocale.toLegacyType(key, value) ?: return false
        val keywordValues = keywordValuesProducer(key, ULocale(locale.baseName), false)
        return legacyType in keywordValues
    }

    fun getNumberingSystem(locale: ULocale): String {
        return NumberingSystem.getInstance(locale)?.name ?: "latn"
    }

    private fun buildLocaleMatcher(availableLocales: Set<String>): LocaleMatcher {
        val builder = LocaleMatcher.builder()
        builder.setDefaultULocale(ULocale.forLanguageTag(defaultLocale()))
        for (availableLocale in availableLocales)
            builder.addSupportedULocale(ULocale.forLanguageTag(availableLocale))
        return builder.build()
    }
}