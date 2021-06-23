package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.key
import me.mattco.reeva.utils.toValue
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

class JSStringProto private constructor(realm: Realm) : JSStringObject(realm, JSString("")) {
    override fun init() {
        // No super call to avoid prototype complications
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.stringCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineNativeFunction("charAt", 1, ::charAt)
        defineNativeFunction("charCodeAt", 1, ::charCodeAt)
        defineNativeFunction("codePointAt", 1, ::codePointAt)
        defineNativeFunction("concat", 1, ::concat)
        defineNativeFunction("endsWith", 1, ::endsWith)
        defineNativeFunction("includes", 1, ::includes)
        defineNativeFunction("indexOf", 1, ::indexOf)
        defineNativeFunction("lastIndexOf", 1, ::lastIndexOf)
        defineNativeFunction("padEnd", 1, ::padEnd)
        defineNativeFunction("padStart", 1, ::padStart)
        defineNativeFunction("repeat", 1, ::repeat)
        defineNativeFunction("replace", 1, ::replace)
        defineNativeFunction("slice", 2, ::slice)
        defineNativeFunction("split", 2, ::split)
        defineNativeFunction("startsWith", 1, ::startsWith)
        defineNativeFunction("substring", 2, ::substring)
        defineNativeFunction("toLowerCase", 0, ::toLowerCase)
        defineNativeFunction("toString", 0, ::toString)
        defineNativeFunction("toUpperCase", 0, ::toUpperCase)
        defineNativeFunction("trim", 0, ::trim)
        defineNativeFunction("trimEnd", 0, ::trimEnd)
        defineNativeFunction("trimStart", 0, ::trimStart)
        defineNativeFunction("valueOf", 0, ::valueOf)
    }

    fun charAt(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val str = Operations.toString(realm, obj)
        val position = Operations.toIntegerOrInfinity(realm, arguments.argument(0)).let {
            if (it.isInfinite)
                return "".toValue()
            it.asInt
        }
        val size = str.string.length
        if (position < 0 || position >= size)
            return "".toValue()
        return str.string[position].toValue()
    }

    fun charCodeAt(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val str = Operations.toString(realm, obj)
        val position = Operations.toIntegerOrInfinity(realm, arguments.argument(0)).let {
            if (it.isInfinite)
                return JSNumber.NaN
            it.asInt
        }
        val size = str.string.length
        if (position < 0 || position >= size)
            return JSNumber.NaN
        return str.string[position].code.toValue()
    }

    fun codePointAt(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        val position = Operations.toIntegerOrInfinity(realm, arguments.argument(0)).let {
            if (it.isInfinite)
                return JSUndefined
            it.asInt
        }
        if (position < 0 || position >= string.length)
            return JSUndefined
        return Operations.codePointAt(string, position).codepoint.toValue()
    }

    fun concat(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        var result = string
        arguments.forEach {
            result += Operations.toString(realm, it)
        }
        return result.toValue()
    }

    fun endsWith(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        // TODO: RegExp check
        val searchString = Operations.toString(realm, arguments.argument(0)).string

        val end = arguments.argument(1).let {
            if (it == JSUndefined) {
                string.length
            } else Operations.toIntegerOrInfinity(realm, it).asInt
        }.coerceIn(0, string.length)

        if (searchString.isEmpty())
            return JSTrue

        val start = end - searchString.length
        if (start < 0)
            return JSFalse

        return (string.substring(start, end) == searchString).toValue()
    }

    fun includes(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        // TODO: RegExp check
        val searchString = Operations.toString(realm, arguments.argument(0)).string
        val pos = Operations.toIntegerOrInfinity(realm, arguments.argument(1)).asInt.coerceIn(0, string.length)
        return string.substring(pos).contains(searchString).toValue()
    }

    fun indexOf(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        // TODO: RegExp check
        val searchString = Operations.toString(realm, arguments.argument(0)).string
        val pos = Operations.toIntegerOrInfinity(realm, arguments.argument(1)).asInt.coerceIn(0, string.length)

        return string.indexOf(searchString, pos).toValue()
    }

    fun lastIndexOf(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        // TODO: RegExp check
        val searchString = Operations.toString(realm, arguments.argument(0)).string
        val numPos = Operations.toNumber(realm, arguments.argument(1))
        val pos = if (numPos.isNaN) {
            string.length
        } else Operations.toIntegerOrInfinity(realm, numPos).asInt.coerceIn(0, string.length)


        return string.lastIndexOf(searchString, pos).toValue()
    }

    fun padEnd(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        return stringPad(realm, obj, arguments.argument(0), arguments.argument(1), false).toValue()
    }

    fun padStart(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        return stringPad(realm, obj, arguments.argument(0), arguments.argument(1), true).toValue()
    }

    fun repeat(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        val count = Operations.toIntegerOrInfinity(realm, arguments.argument(0)).asInt
        if (count < 0 || count == Int.MAX_VALUE)
            Errors.TODO("String.prototype.repeat").throwRangeError(realm)
        return string.repeat(count).toValue()
    }

    fun replace(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        var (searchValue, replaceValue) = arguments.takeArgs(0..1)

        if (!searchValue.isNullish) {
            val replacer = Operations.getMethod(realm, searchValue, Realm.`@@replace`)
            if (replacer != JSUndefined)
                return Operations.call(realm, replacer, searchValue, listOf(obj, replaceValue))
        }

        val string = Operations.toString(realm, obj).string
        val searchString = Operations.toString(realm, searchValue).asString
        val functionalReplace = Operations.isCallable(replaceValue)
        if (!functionalReplace)
            replaceValue = Operations.toString(realm, replaceValue)

        val position = string.indexOf(searchString)
        if (position == -1)
            return string.toValue()

        val preserved = string.substring(0, position)
        val replacement = if (functionalReplace) {
            Operations.toString(realm, Operations.call(
                realm,
                replaceValue,
                JSUndefined,
                listOf(searchString.toValue(), position.toValue(), string.toValue())
            )).asString
        } else {
            getSubstitution(searchString, string, position, emptyList(), emptyMap(), replaceValue.asString)
        }

        return (preserved + replacement + string.substring(position + searchString.length)).toValue()
    }

    fun slice(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        val from = Operations.toIntegerOrInfinity(realm, arguments.argument(0)).let {
            when {
                it.isNegativeInfinity -> 0
                it.asInt < 0 -> max(string.length + it.asInt, 0)
                else -> min(it.asInt, string.length)
            }
        }

        val to = if (arguments.size > 1) {
            Operations.toIntegerOrInfinity(realm, arguments[1])
        } else {
            JSNumber(string.length)
        }.let {
            when {
                it.isNegativeInfinity -> 0
                it.asInt < 0 -> max(string.length + it.asInt, 0)
                else -> min(it.asInt, string.length)
            }
        }

        if (from > to)
            return "".toValue()

        return string.substring(from, to).toValue()
    }

    fun split(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val (separator, limit) = arguments.takeArgs(0..1)

        if (!separator.isNullish) {
            val splitter = Operations.getMethod(realm, separator, Realm.`@@split`)
            if (splitter != JSUndefined)
                return Operations.call(realm, splitter, separator, listOf(obj, limit))
        }

        val string = Operations.toString(realm, obj).string
        val array = Operations.arrayCreate(realm, 0)
        var arrayLength = 0
        val lim = if (limit == JSUndefined) {
            Operations.MAX_32BIT_INT - 1
        } else Operations.toUint32(realm, limit).asInt

        val separatorString = Operations.toString(realm, separator).string
        if (lim == 0)
            return array
        if (separator == JSUndefined) {
            Operations.createDataPropertyOrThrow(realm, array, 0.key(), string.toValue())
            return array
        }

        val stringLength = string.length
        if (stringLength == 0) {
            if (separatorString.isNotEmpty())
                Operations.createDataPropertyOrThrow(realm, array, 0.key(), string.toValue())
            return array
        }

        var splitStart = 0
        var splitEnd = 0

        while (splitEnd != stringLength) {
            val splitMatchIndex = splitMatch(string, splitEnd, separatorString)
            if (splitMatchIndex == null) {
                splitEnd++
                continue
            }

            ecmaAssert(splitMatchIndex in 0..stringLength)
            if (splitMatchIndex == splitStart) {
                splitEnd++
                continue
            }

            Operations.createDataPropertyOrThrow(realm, array, arrayLength.key(), string.substring(splitStart, splitEnd).toValue())
            arrayLength++
            if (arrayLength == lim)
                return array
            splitStart = splitMatchIndex
            splitEnd = splitStart
        }

        Operations.createDataPropertyOrThrow(realm, array, arrayLength.key(), string.substring(splitStart, stringLength).toValue())
        return array
    }

    fun startsWith(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        // TODO: RegExp check
        val searchString = Operations.toString(realm, arguments.argument(0)).string

        val start = arguments.argument(1).let {
            if (it == JSUndefined) {
                0
            } else Operations.toIntegerOrInfinity(realm, it).asInt
        }.coerceIn(0, string.length)

        if (searchString.isEmpty())
            return JSTrue

        val end = start + searchString.length
        if (end > string.length)
            return JSFalse

        return (string.substring(start, end) == searchString).toValue()
    }

    fun substring(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        val intStart = Operations.toIntegerOrInfinity(realm, arguments.argument(0)).asInt
        val intEnd = if (arguments.size < 2) {
            string.length
        } else Operations.toIntegerOrInfinity(realm, arguments[1]).asInt

        val finalStart = intStart.coerceIn(0, string.length)
        val finalEnd = intEnd.coerceIn(0, string.length)

        val from = min(finalStart, finalEnd)
        val to = max(finalStart, finalEnd)

        return string.substring(from, to).toValue()
    }

    fun toLowerCase(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        // TODO: unicode handling
        return string.lowercase().toValue()
    }

    fun toString(realm: Realm, arguments: JSArguments): JSValue {
        return thisStringValue(realm, arguments.thisValue, "toString")
    }

    fun toUpperCase(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string

        // TODO: unicode handling
        return string.uppercase().toValue()
    }

    fun trim(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        return string.trim().toValue()
    }

    fun trimEnd(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        return string.trimEnd().toValue()
    }

    fun trimStart(realm: Realm, arguments: JSArguments): JSValue {
        val obj = Operations.requireObjectCoercible(realm, arguments.thisValue)
        val string = Operations.toString(realm, obj).string
        return string.trimStart().toValue()
    }

    fun valueOf(realm: Realm, arguments: JSArguments): JSValue {
        return thisStringValue(realm, arguments.thisValue, "valueOf")
    }

    private fun stringPad(realm: Realm, obj: JSValue, maxLength: JSValue, fillString: JSValue, isStart: Boolean): String {
        val string = Operations.toString(realm, obj).string
        val intMaxLength = Operations.toLength(realm, maxLength).asInt
        if (intMaxLength < string.length)
            return string
        val filler = if (fillString == JSUndefined) {
            " "
        } else Operations.toString(realm, fillString).string

        if (filler.isEmpty())
            return string

        val fillLen = intMaxLength - string.length
        return buildString {
            var remaining = fillLen
            while (remaining > 0) {
                if (remaining < filler.length) {
                    append(filler.substring(0, remaining))
                    remaining = 0
                } else {
                    append(filler)
                    remaining -= filler.length
                }
            }

            append(string)
        }
    }

    // TODO: Clean this method up
    private fun getSubstitution(
        matched: String,
        str: String,
        position: Int,
        captures: List<String>,
        namedCaptures: Map<String, String>,
        replacement: String
    ): String {
        val matchLength = matched.codePoints().count().toInt()
        val stringLength = str.codePoints().count().toInt()
        val tailPos = position + matchLength

        var i = 0
        val replacementCodePoints = replacement.codePoints().toList()

        return buildString {
            while (i < replacementCodePoints.size) {
                val cp = replacementCodePoints[i]

                if (cp == 0x24) {
                    if (i == replacementCodePoints.lastIndex) {
                        append('$')
                    } else when (val ch = replacementCodePoints[i + 1]) {
                        0x24 -> {
                            append('$')
                            i++
                        }
                        0x26 -> {
                            append(matched)
                            i++
                        }
                        0x60 -> {
                            append(str.substring(0, position))
                            i++
                        }
                        0x27 -> {
                            if (tailPos < stringLength)
                                append(str.substring(tailPos))
                            i++
                        }
                        0x3c -> {
                            i++
                            if (namedCaptures.isEmpty()) {
                                append("$<")
                            } else {
                                var closeBracketIndex = -1
                                while (closeBracketIndex < replacementCodePoints.size) {
                                    if (replacementCodePoints[closeBracketIndex] == 0xe3)
                                        break
                                    closeBracketIndex++
                                }
                                if (closeBracketIndex == -1) {
                                    append("$<")
                                } else {
                                    i = closeBracketIndex++
                                    val groupName = replacementCodePoints.subList(i, closeBracketIndex).map {
                                        it.toChar()
                                    }.joinToString(separator = "")

                                    if (groupName in namedCaptures) {
                                        append(namedCaptures[groupName])
                                    }
                                }
                            }
                        }
                        else -> {
                            if (!ch.toChar().isDigit()) {
                                append('$')
                            } else {
                                i++
                                val next = replacementCodePoints[i + 1]
                                val number = if (next.toChar().isDigit()) {
                                    i++
                                    buildString {
                                        append(ch.toChar())
                                        append(next.toChar())
                                    }.toInt()
                                } else {
                                    ch.toChar().toString().toInt()
                                }

                                if (number < captures.size)
                                    append(captures[number])
                            }
                        }
                    }
                } else {
                    append(cp.toChar())
                }

                i++
            }
        }
    }

    // null indicates "not-matched"
    private fun splitMatch(string: String, startIndex: Int, separatorString: String): Int? {
        val stringCodePoints = string.codePoints().toList()
        val separatorCodePoints = separatorString.codePoints().toList()
        val numStringCodePoints = stringCodePoints.size
        val numSeparatorCodePoints = separatorCodePoints.size

        if (startIndex + numSeparatorCodePoints > numStringCodePoints)
            return null

        for (i in 0 until numSeparatorCodePoints) {
            if (stringCodePoints[startIndex + i] != separatorCodePoints[i])
                return null
        }

        return startIndex + numSeparatorCodePoints
    }

    companion object {
        fun create(realm: Realm) = JSStringProto(realm).initialize()

        private fun thisStringValue(realm: Realm, thisValue: JSValue, methodName: String): JSString {
            if (thisValue is JSString)
                return thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("String.prototype.$methodName").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.StringData) ?:
                Errors.IncompatibleMethodCall("String.prototype.$methodName").throwTypeError(realm)
        }
    }
}
