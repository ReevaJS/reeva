package com.reevajs.reeva.runtime.intl

import com.ibm.icu.text.ListFormatter
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

class JSListFormatCtor private constructor(realm: Realm) : JSNativeFunction(realm, "ListFormat", 0) {
    override fun init() {
        super.init()

        defineBuiltin("supportedLocalesOf", 1, ReevaBuiltin.ListFormatCtorSupportedLocalesOf)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("ListFormat").throwTypeError(realm)

        val localesValue = arguments.argument(0)
        val optionsValue = arguments.argument(1)

        val requestedLocales = IntlAOs.canonicalizeLocaleList(realm, localesValue)
        val options = IntlAOs.getOptionsObject(realm, optionsValue)

        val matcher = IntlAOs.getOption(realm, options, "localeMatcher", false, setOf("lookup", "best fit")) ?: "best fit"

        val (locale, ulocale) = IntlAOs.resolveLocale(
            IntlAOs.pluralRulesAvailableLocales,
            requestedLocales,
            matcher == "best fit",
            emptySet(),
        )!!

        val typeStr = IntlAOs.getOption(
            realm,
            options,
            "type",
            false,
            setOf("conjunction", "disjunction", "unit"),
        ) ?: "conjunction"

        val styleStr = IntlAOs.getOption(
            realm,
            options,
            "style",
            false,
            setOf("long", "short", "narrow"),
        ) ?: "long"

        val type = when (typeStr) {
            "conjunction" -> ListFormatter.Type.AND
            "disjunction" -> ListFormatter.Type.OR
            "unit" -> ListFormatter.Type.UNITS
            else -> unreachable()
        }

        val width = when (styleStr) {
            "long" -> ListFormatter.Width.WIDE
            "short" -> ListFormatter.Width.SHORT
            "narrow" -> ListFormatter.Width.NARROW
            else -> unreachable()
        }

        val listFormatter = ListFormatter.getInstance(ulocale, type, width)

        val obj = Operations.ordinaryCreateFromConstructor(
            realm,
            arguments.newTarget,
            realm.listFormatProto,
            listOf(SlotName.InitializedListFormat),
        )
        obj.setSlot(SlotName.Locale, locale)
        obj.setSlot(SlotName.ListFormatter, listFormatter)
        obj.setSlot(SlotName.ListFormatterType, typeStr)
        obj.setSlot(SlotName.ListFormatterStyle, styleStr)
        return obj
    }

    companion object {
        fun create(realm: Realm) = JSListFormatCtor(realm).initialize()

        @JvmStatic
        fun supportedLocalesOf(realm: Realm, arguments: JSArguments): JSValue {
            val availableLocales = IntlAOs.pluralRulesAvailableLocales
            val requestedLocales = IntlAOs.canonicalizeLocaleList(realm, arguments.argument(0))
            return IntlAOs.supportedLocales(realm, availableLocales, requestedLocales, arguments.argument(1))
        }
    }
}