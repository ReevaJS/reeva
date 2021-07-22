package com.reevajs.reeva.runtime.intl

import com.ibm.icu.util.IllformedLocaleException
import com.ibm.icu.util.ULocale
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors

class JSLocaleCtor(realm: Realm) : JSNativeFunction(realm, "Locale", 1) {
    @ECMAImpl("14.1.3")
    override fun evaluate(arguments: JSArguments): JSValue {
        if (arguments.newTarget == JSUndefined)
            Errors.CtorCallWithoutNew("Locale").throwTypeError(realm)

        val builder = ULocale.Builder()

        val tagValue = arguments.argument(0)
        val options = IntlAOs.coerceOptionsToObject(realm, arguments.argument(1))

        if (tagValue !is JSString && tagValue !is JSObject)
            Errors.Intl.Locale.InvalidLocaleTagType.throwTypeError(realm)

        val tag = if (tagValue is JSObject && tagValue.hasSlot(SlotName.InitializedLocale)) {
            tagValue.getSlotAs(SlotName.Locale)
        } else tagValue.toJSString(realm).string

        if (tag.isEmpty())
            Errors.Intl.Locale.InvalidLocaleTagType.throwTypeError(realm)

        applyOptionsToTag(realm, tag, options, builder)

        for ((name, key, possibleValues, isBool) in optionsData) {
            val value = IntlAOs.getOption(realm, options, name, isBool, possibleValues) ?: continue
            if (ULocale.toLegacyType(ULocale.toLegacyKey(key), value) == null)
                Errors.Intl.Locale.InvalidLocaleOption(name, value).throwRangeError(realm)
            builder.setUnicodeLocaleKeyword(key, value)
        }

        val ulocale = ULocale.createCanonical(builder.build())
        val localeObject = Operations.ordinaryCreateFromConstructor(
            realm,
            arguments.newTarget,
            realm.localeProto,
            listOf(SlotName.InitializedLocale, SlotName.ULocale),
        )
        localeObject.setSlot(SlotName.ULocale, ulocale)

        return localeObject
    }

    private fun applyOptionsToTag(realm: Realm, tag: String, options: JSObject, builder: ULocale.Builder) {
        if (!IntlAOs.isStructurallyValidLanguageTag(tag))
            Errors.Intl.Locale.InvalidLanguageTag(tag).throwRangeError(realm)

        builder.setLanguageTag(tag)

        val ulocale = ULocale.createCanonical(builder.build())
        builder.setLocale(ulocale)

        val language = IntlAOs.getOption(realm, options, "language", false, emptySet())
        if (language != null) {
            try {
                builder.setLanguage(language)
            } catch (e: IllformedLocaleException) {
                Errors.Intl.Locale.InvalidLocaleLanguage(language).throwRangeError(realm)
            }
        }

        val script = IntlAOs.getOption(realm, options, "script", false, emptySet())
        if (script != null) {
            try {
                builder.setScript(script)
            } catch (e: IllformedLocaleException) {
                Errors.Intl.Locale.InvalidLocaleScript(script).throwRangeError(realm)
            }
        }

        val region = IntlAOs.getOption(realm, options, "region", false, emptySet())
        if (region != null) {
            try {
                builder.setRegion(region)
            } catch (e: IllformedLocaleException) {
                Errors.Intl.Locale.InvalidLocaleRegion(region).throwRangeError(realm)
            }
        }
    }

    companion object {
        data class OptionData(val name: String, val key: String, val possibleValues: Set<String>, val isBool: Boolean)

        private val optionsData = listOf(
            OptionData("calendar", "ca", emptySet(), false),
            OptionData("collation", "co", emptySet(), false),
            OptionData("hourCycle", "hc", setOf("h11", "h12", "h23", "h24"), false),
            OptionData("caseFirst", "kf", setOf("upper", "lower", "false"), false),
            OptionData("numeric", "kn", emptySet(), true),
            OptionData("numberingSystem", "nu", emptySet(), false)
        )

        fun create(realm: Realm) = JSLocaleCtor(realm).initialize()
    }
}