package com.reevajs.reeva.runtime.intl

import com.ibm.icu.text.Collator
import com.ibm.icu.text.NumberingSystem
import com.ibm.icu.util.Calendar
import com.ibm.icu.util.IllformedLocaleException
import com.ibm.icu.util.LocaleMatcher
import com.ibm.icu.util.ULocale
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import sun.util.locale.LanguageTag

object IntlAOs {
    private val deprecatedOrLegacyLanguages = setOf("in", "iw", "ji", "jw", "mo", "sh", "tl", "no")

    // Implements that [[AvailableLocales]] slot that is in every Intl object. Any function which
    // accepts a list of availableLocales will instead refer to this field
    val availableLocales = ULocale.getAvailableLocales().map { it.toLanguageTag() }.toSet()

    private val localeMatcher = let {
        val builder = LocaleMatcher.builder()
        for (locale in ULocale.getAvailableLocales())
            builder.addSupportedULocale(locale)
        builder.build()
    }

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

    @ECMAImpl("9.2.1")
    fun canonicalizeLocaleList(realm: Realm, locales: JSValue): Set<String> {
        if (locales == JSUndefined)
            return emptySet()

        val seen = mutableSetOf<String>()

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
    fun bestAvailableLocale(locale: String): String? {
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
    fun lookupMatcher(requestedLocales: List<String>): String {
        for (locale in requestedLocales) {
            val (noExtensionsLocale, possibleExtension) = removeLocaleExtensionSequences(locale)
            val availableLocale = bestAvailableLocale(noExtensionsLocale)
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
    fun bestFitMatcher(requestedLocales: List<String>): String {
        return localeMatcher.getBestMatchResult(requestedLocales.map(::ULocale)).supportedULocale.toLanguageTag()
    }

    data class ResolvedLocale(val locale: String, val ulocale: ULocale, val extensions: Map<String, String>)

    @ECMAImpl("9.2.7")
    fun resolveLocale(
        requestedLocales: List<String>,
        useBestFitMatcher: Boolean,
        relevantExtensionKeys: Set<String>,
        localeDate: String,
    ): ResolvedLocale? {
        val locale = if (useBestFitMatcher) {
            bestFitMatcher(requestedLocales)
        } else lookupMatcher(requestedLocales)

        val ulocale = createULocale(locale) ?: return null
        val (newULocale, extensions) = getUnicodeExtensions(ulocale, relevantExtensionKeys)

        val canonicalizedLocale = newULocale.toLanguageTag()
        return ResolvedLocale(canonicalizedLocale, newULocale, extensions)
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

    data class UnicodeExtensions(val ulocale: ULocale, val extensions: Map<String, String>)

    private fun getUnicodeExtensions(ulocale: ULocale, relevantKeys: Set<String>): UnicodeExtensions {
        val extensions = mutableMapOf<String, String>()
        val builder = ULocale.Builder().setLocale(ulocale).clearExtensions()
        val keywords = ulocale.keywords
        if (!keywords.hasNext())
            return UnicodeExtensions(ulocale, extensions)

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

    private fun isValidNumberingSystem(value: String): Boolean {
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
}