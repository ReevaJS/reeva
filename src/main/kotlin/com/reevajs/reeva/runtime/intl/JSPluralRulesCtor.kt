package com.reevajs.reeva.runtime.intl

import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.text.PluralRules
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import java.math.RoundingMode

class JSPluralRulesCtor private constructor(realm: Realm) : JSNativeFunction(realm, "PluralRules", 0) {
    override fun init() {
        super.init()

        defineBuiltin("supportedLocalesOf", 1, ReevaBuiltin.PluralRulesCtorSupportedLocalesOf)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("PluralRules").throwTypeError(realm)

        val localesValue = arguments.argument(0)
        val optionsValue = arguments.argument(1)

        val requestedLocales = IntlAOs.canonicalizeLocaleList(realm, localesValue)
        val options = IntlAOs.coerceOptionsToObject(realm, optionsValue)

        val matcher = IntlAOs.getOption(realm, options, "localeMatcher", false, setOf("lookup", "best fit")) ?: "best fit"
        val typeStr = IntlAOs.getOption(realm, options, "type", false, setOf("cardinal", "ordinal")) ?: "cardinal"
        val type = if (typeStr == "cardinal") PluralRules.PluralType.CARDINAL else PluralRules.PluralType.ORDINAL

        val (locale, ulocale, extensions) = IntlAOs.resolveLocale(
            IntlAOs.pluralRulesAvailableLocales,
            requestedLocales,
            matcher == "best fit",
            emptySet(),
        )!!

        var numberFormatter = NumberFormatter.withLocale(ulocale).roundingMode(RoundingMode.HALF_UP)
        val pluralRules = PluralRules.forLocale(ulocale, type)

        val digitOptions = IntlAOs.setNumberFormatDigitOptions(realm, options, 0, 3, notationIsCompact = false)
        numberFormatter = IntlAOs.setDigitOptionsToFormatter(numberFormatter, digitOptions)

        val obj = Operations.ordinaryCreateFromConstructor(
            realm,
            arguments.newTarget,
            realm.pluralRulesProto,
            listOf(SlotName.InitializedPluralRules),
        )
        obj.setSlot(SlotName.PluralType, type)
        obj.setSlot(SlotName.Locale, locale)
        obj.setSlot(SlotName.PluralRules, pluralRules)
        obj.setSlot(SlotName.NumberFormatter, numberFormatter)

        return obj
    }

    companion object {
        fun create(realm: Realm) = JSPluralRulesCtor(realm).initialize()

        @JvmStatic
        fun supportedLocalesOf(realm: Realm, arguments: JSArguments): JSValue {
            val availableLocales = IntlAOs.pluralRulesAvailableLocales
            val requestedLocales = IntlAOs.canonicalizeLocaleList(realm, arguments.argument(0))
            return IntlAOs.supportedLocales(realm, availableLocales, requestedLocales, arguments.argument(1))
        }
    }
}