package com.reevajs.reeva.runtime.intl

import com.ibm.icu.number.*
import com.ibm.icu.text.NumberingSystem
import com.ibm.icu.util.Currency
import com.ibm.icu.util.MeasureUnit
import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.unreachable
import java.math.RoundingMode

typealias ICUNotation = Notation

class JSNumberFormatCtor(realm: Realm) : JSNativeFunction(realm, "NumberFormat", 0) {
    override fun init() {
        super.init()

        defineBuiltin("supportedLocalesOf", 1, ReevaBuiltin.NumberFormatCtorSupportedLocalesOf)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = if (arguments.newTarget == JSUndefined) {
            Agent.activeFunction
        } else arguments.newTarget

        val locales = arguments.argument(0)
        val options = IntlAOs.coerceOptionsToObject(realm, arguments.argument(1))

        val requestedLocales = IntlAOs.canonicalizeLocaleList(realm, locales)
        val matcher =
            IntlAOs.getOption(realm, options, "localeMatcher", false, setOf("lookup", "best fit")) ?: "best fit"
        val useBestFit = matcher == "best fit"

        val numberingSystemStr = IntlAOs.getOption(realm, options, "numberingSystem", false, emptySet())
        if (numberingSystemStr != null && NumberingSystem.getInstanceByName(numberingSystemStr) == null)
            Errors.Intl.NumberFormat.InvalidNumberingSystem(numberingSystemStr).throwRangeError(realm)

        val (locale, ulocale, extensions) = IntlAOs.resolveLocale(
            IntlAOs.numberFormatAvailableLocales,
            requestedLocales,
            useBestFit,
            setOf("nu"),
        )!!

        if (numberingSystemStr != null) {
            if (IntlAOs.isValidNumberingSystem(numberingSystemStr)) {
                ulocale.setKeywordValue("nu", numberingSystemStr)
            } else {
                ulocale.setKeywordValue("nu", null)
            }
        }

        val numberingSystem = IntlAOs.getNumberingSystem(ulocale)
        var numberFormatter = NumberFormatter.withLocale(ulocale).roundingMode(RoundingMode.HALF_UP)

        if (numberingSystem.isNotEmpty() && numberingSystem != "latn")
            numberFormatter = numberFormatter.symbols(NumberingSystem.getInstanceByName(numberingSystem))

        val styleStr = IntlAOs.getOption(
            realm,
            options,
            "style",
            false,
            setOf("decimal", "percent", "currency", "unit"),
        ) ?: "decimal"
        val style = IntlAOs.NumberStyle.values().first { it.name.lowercase() == styleStr }

        var currency = IntlAOs.getOption(realm, options, "currency", false, emptySet())

        if (currency != null) {
            if (!IntlAOs.isWellFormedCurrencyCode(currency))
                Errors.Intl.NumberFormat.InvalidCurrency(currency).throwRangeError(realm)
        } else if (style == IntlAOs.NumberStyle.Currency) {
            Errors.Intl.NumberFormat.MissingStyle("currency").throwTypeError(realm)
        }

        val currencyDisplayStr = IntlAOs.getOption(
            realm,
            options,
            "currencyDisplay",
            false,
            setOf("code", "symbol", "name", "narrowSymbol"),
        ) ?: "symbol"
        val currencyDisplay = CurrencyDisplay.values().first { it.name.lowercase() == currencyDisplayStr }

        val currencySignStr = IntlAOs.getOption(
            realm,
            options,
            "currencySign",
            false,
            setOf("standard", "accounting"),
        ) ?: "standard"
        val currencySign = CurrencySign.values().first { it.name.lowercase() == currencySignStr }

        val unit = IntlAOs.getOption(realm, options, "unit", false, emptySet())
        var unitNumerator: MeasureUnit? = null
        var unitDenominator: MeasureUnit? = null
        if (unit != null) {
            val pair = IntlAOs.getWellFormedUnitIdentifierParts(unit)
                ?: Errors.Intl.NumberFormat.InvalidUnit(unit).throwRangeError(realm)
            unitNumerator = pair.numerator
            unitDenominator = pair.denominator
        } else if (style == IntlAOs.NumberStyle.Unit) {
            Errors.Intl.NumberFormat.MissingStyle("unit").throwTypeError(realm)
        }

        val unitDisplayStr = IntlAOs.getOption(
            realm,
            options,
            "unitDisplay",
            false,
            setOf("short", "narrow", "long"),
        ) ?: "short"
        val unitDisplay = UnitDisplay.values().first { it.name.lowercase() == unitDisplayStr }

        when (style) {
            IntlAOs.NumberStyle.Currency -> {
                if (currency == null)
                    Errors.Intl.NumberFormat.MissingStyle("currency").throwTypeError(realm)

                numberFormatter = numberFormatter
                    .unit(Currency.getInstance(currency.uppercase()))
                    .unitWidth(currencyDisplay.width)
            }
            IntlAOs.NumberStyle.Unit -> {
                if (unitNumerator != null)
                    numberFormatter = numberFormatter.unit(unitNumerator)
                if (unitDenominator != null)
                    numberFormatter = numberFormatter.perUnit(unitDenominator)

                numberFormatter = numberFormatter.unitWidth(unitDisplay.width)
            }
            IntlAOs.NumberStyle.Percent -> {
                numberFormatter = numberFormatter.unit(MeasureUnit.PERCENT).scale(Scale.powerOfTen(2))
            }
            else -> {}
        }

        val mnfdDefault: Int
        val mxfdDefault: Int

        if (style == IntlAOs.NumberStyle.Currency) {
            val cDigits = IntlAOs.currencyDigits(currency!!)
            mnfdDefault = cDigits
            mxfdDefault = cDigits
        } else {
            mnfdDefault = 0
            mxfdDefault = if (style == IntlAOs.NumberStyle.Percent) 0 else 3
        }

        val notationStr = IntlAOs.getOption(
            realm,
            options,
            "notation",
            false,
            setOf("standard", "scientific", "engineering", "compact"),
        ) ?: "standard"
        val notation = Notation.values().first { it.name.lowercase() == notationStr }

        val digitOptions = IntlAOs.setNumberFormatDigitOptions(
            realm,
            options,
            mnfdDefault,
            mxfdDefault,
            notation == Notation.Compact,
        )

        if (digitOptions.minimumIntegerDigits > 1)
            numberFormatter = numberFormatter.integerWidth(IntegerWidth.zeroFillTo(digitOptions.minimumIntegerDigits))

        if (digitOptions.roundingType != IntlAOs.RoundingType.CompactRounding) {
            val precision = if (digitOptions.minimumSignificantDigits > 0) {
                Precision.minMaxSignificantDigits(
                    digitOptions.minimumSignificantDigits,
                    digitOptions.maximumSignificantDigits
                )
            } else {
                Precision.minMaxFraction(digitOptions.minimumFractionDigits, digitOptions.maximumFractionDigits)
            }
            numberFormatter = numberFormatter.precision(precision)
        }

        val compactDisplayStr = IntlAOs.getOption(
            realm,
            options,
            "compactDisplay",
            false,
            setOf("short", "long"),
        ) ?: "short"
        val compactDisplay = CompactDisplay.values().first { it.name.lowercase() == compactDisplayStr }

        val icuNotation = when (notation) {
            Notation.Standard -> ICUNotation.simple()
            Notation.Scientific -> ICUNotation.scientific()
            Notation.Engineering -> ICUNotation.engineering()
            Notation.Compact -> if (compactDisplay == CompactDisplay.Short) {
                ICUNotation.compactShort()
            } else ICUNotation.compactLong()
        }

        numberFormatter = numberFormatter.notation(icuNotation)

        val useGrouping = IntlAOs.getOption(realm, options, "useGrouping", true, emptySet()) ?: "true"
        val strategy = if (useGrouping == "true") {
            NumberFormatter.GroupingStrategy.ON_ALIGNED
        } else NumberFormatter.GroupingStrategy.OFF
        numberFormatter = numberFormatter.grouping(strategy)

        val signDisplayStr = IntlAOs.getOption(
            realm,
            options,
            "signDisplay",
            false,
            setOf("auto", "never", "always", "exceptZero"),
        ) ?: "auto"
        val signDisplay = when (signDisplayStr) {
            "auto" -> if (currencySign == CurrencySign.Accounting) {
                NumberFormatter.SignDisplay.ACCOUNTING
            } else NumberFormatter.SignDisplay.AUTO
            "never" -> NumberFormatter.SignDisplay.NEVER
            "always" -> if (currencySign == CurrencySign.Accounting) {
                NumberFormatter.SignDisplay.ACCOUNTING_ALWAYS
            } else NumberFormatter.SignDisplay.ALWAYS
            "exceptZero" -> if (currencySign == CurrencySign.Accounting) {
                NumberFormatter.SignDisplay.ACCOUNTING_EXCEPT_ZERO
            } else NumberFormatter.SignDisplay.EXCEPT_ZERO
            else -> unreachable()
        }
        numberFormatter = numberFormatter.sign(signDisplay)

        val obj = Operations.ordinaryCreateFromConstructor(
            realm,
            newTarget,
            realm.numberFormatProto,
            listOf(SlotName.InitializedNumberFormat, SlotName.NumberFormatter, SlotName.Locale),
        )
        obj.setSlot(SlotName.NumberFormatter, numberFormatter)
        obj.setSlot(SlotName.Locale, locale)
        return obj
    }

    private enum class CurrencyDisplay(val width: NumberFormatter.UnitWidth) {
        Code(NumberFormatter.UnitWidth.ISO_CODE),
        Symbol(NumberFormatter.UnitWidth.SHORT),
        Name(NumberFormatter.UnitWidth.FULL_NAME),
        NarrowSymbol(NumberFormatter.UnitWidth.NARROW),
    }

    private enum class CurrencySign {
        Standard,
        Accounting,
    }

    private enum class UnitDisplay(val width: NumberFormatter.UnitWidth) {
        Short(NumberFormatter.UnitWidth.SHORT),
        Narrow(NumberFormatter.UnitWidth.NARROW),
        Long(NumberFormatter.UnitWidth.FULL_NAME),
    }

    private enum class Notation {
        Standard,
        Scientific,
        Engineering,
        Compact,
    }

    private enum class CompactDisplay {
        Short,
        Long,
    }

    companion object {
        fun create(realm: Realm) = JSNumberFormatCtor(realm).initialize()

        @JvmStatic
        fun supportedLocalesOf(realm: Realm, arguments: JSArguments): JSValue {
            val availableLocales = IntlAOs.numberFormatAvailableLocales
            val requestedLocales = IntlAOs.canonicalizeLocaleList(realm, arguments.argument(0))
            return IntlAOs.supportedLocales(realm, availableLocales, requestedLocales, arguments.argument(1))
        }
    }
}
