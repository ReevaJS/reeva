package com.reevajs.reeva.runtime.wrappers.strings

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.*
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

class JSStringProto private constructor(realm: Realm) : JSStringObject(realm, JSString("")) {
    override fun init() {
        // No super call to avoid prototype complications
        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.stringCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineBuiltin("at", 1, ReevaBuiltin.StringProtoAt)
        defineBuiltin("charAt", 1, ReevaBuiltin.StringProtoCharAt)
        defineBuiltin("charCodeAt", 1, ReevaBuiltin.StringProtoCharCodeAt)
        defineBuiltin("codePointAt", 1, ReevaBuiltin.StringProtoCodePointAt)
        defineBuiltin("concat", 1, ReevaBuiltin.StringProtoConcat)
        defineBuiltin("endsWith", 1, ReevaBuiltin.StringProtoEndsWith)
        defineBuiltin("includes", 1, ReevaBuiltin.StringProtoIncludes)
        defineBuiltin("indexOf", 1, ReevaBuiltin.StringProtoIndexOf)
        defineBuiltin("lastIndexOf", 1, ReevaBuiltin.StringProtoLastIndexOf)
        defineBuiltin("padEnd", 1, ReevaBuiltin.StringProtoPadEnd)
        defineBuiltin("padStart", 1, ReevaBuiltin.StringProtoPadStart)
        defineBuiltin("repeat", 1, ReevaBuiltin.StringProtoRepeat)
        defineBuiltin("replace", 1, ReevaBuiltin.StringProtoReplace)
        defineBuiltin("slice", 2, ReevaBuiltin.StringProtoSlice)
        defineBuiltin("split", 2, ReevaBuiltin.StringProtoSplit)
        defineBuiltin("startsWith", 1, ReevaBuiltin.StringProtoStartsWith)
        defineBuiltin("substring", 2, ReevaBuiltin.StringProtoSubstring)
        defineBuiltin("toLowerCase", 0, ReevaBuiltin.StringProtoToLowerCase)
        defineBuiltin("toString", 0, ReevaBuiltin.StringProtoToString)
        defineBuiltin("toUpperCase", 0, ReevaBuiltin.StringProtoToUpperCase)
        defineBuiltin("trim", 0, ReevaBuiltin.StringProtoTrim)
        defineBuiltin("trimEnd", 0, ReevaBuiltin.StringProtoTrimEnd)
        defineBuiltin("trimStart", 0, ReevaBuiltin.StringProtoTrimStart)
        defineBuiltin("valueOf", 0, ReevaBuiltin.StringProtoValueOf)
        defineBuiltin(Realm.WellKnownSymbols.iterator, 0, ReevaBuiltin.StringProtoSymbolIterator)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSStringProto(realm).initialize()

        private fun thisStringValue(thisValue: JSValue, methodName: String): JSString {
            if (thisValue is JSString)
                return thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("String.prototype.$methodName").throwTypeError()
            return thisValue.getSlotAs(SlotName.StringData)
                ?: Errors.IncompatibleMethodCall("String.prototype.$methodName").throwTypeError()
        }

        @JvmStatic
        fun at(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val str = Operations.toString(obj).string
            val len = str.length
            val relativeIndex = Operations.toIntegerOrInfinity(arguments.argument(0))

            val k = if (relativeIndex.isPositiveInfinity || relativeIndex.asLong >= 0) {
                relativeIndex.asLong
            } else {
                len + relativeIndex.asLong
            }

            if (k < 0 || k >= len)
                return JSUndefined

            expect(k < Int.MAX_VALUE)
            return str[k.toInt()].toValue()
        }

        @ECMAImpl("22.1.3.1")
        @JvmStatic
        fun charAt(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val str = Operations.toString(obj)
            val position = Operations.toIntegerOrInfinity(arguments.argument(0)).let {
                if (it.isInfinite)
                    return "".toValue()
                it.asInt
            }
            val size = str.string.length
            if (position < 0 || position >= size)
                return "".toValue()
            return str.string[position].toValue()
        }

        @ECMAImpl("22.1.3.2")
        @JvmStatic
        fun charCodeAt(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val str = Operations.toString(obj)
            val position = Operations.toIntegerOrInfinity(arguments.argument(0)).let {
                if (it.isInfinite)
                    return JSNumber.NaN
                it.asInt
            }
            val size = str.string.length
            if (position < 0 || position >= size)
                return JSNumber.NaN
            return str.string[position].code.toValue()
        }

        @ECMAImpl("22.1.3.3")
        @JvmStatic
        fun codePointAt(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            val position = Operations.toIntegerOrInfinity(arguments.argument(0)).let {
                if (it.isInfinite)
                    return JSUndefined
                it.asInt
            }
            if (position < 0 || position >= string.length)
                return JSUndefined
            return Operations.codePointAt(string, position).codePoint.toValue()
        }

        @ECMAImpl("22.1.3.4")
        @JvmStatic
        fun concat(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            var result = string
            arguments.forEach {
                result += Operations.toString(it)
            }
            return result.toValue()
        }

        @ECMAImpl("22.1.3.6")
        @JvmStatic
        fun endsWith(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            // TODO: RegExp check
            val searchString = Operations.toString(arguments.argument(0)).string

            val end = arguments.argument(1).let {
                if (it == JSUndefined) {
                    string.length
                } else Operations.toIntegerOrInfinity(it).asInt
            }.coerceIn(0, string.length)

            if (searchString.isEmpty())
                return JSTrue

            val start = end - searchString.length
            if (start < 0)
                return JSFalse

            return (string.substring(start, end) == searchString).toValue()
        }

        @ECMAImpl("22.1.3.7")
        @JvmStatic
        fun includes(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            // TODO: RegExp check
            val searchString = Operations.toString(arguments.argument(0)).string
            val pos = Operations.toIntegerOrInfinity(arguments.argument(1)).asInt.coerceIn(0, string.length)
            return string.substring(pos).contains(searchString).toValue()
        }

        @ECMAImpl("22.1.3.8")
        @JvmStatic
        fun indexOf(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            // TODO: RegExp check
            val searchString = Operations.toString(arguments.argument(0)).string
            val pos = Operations.toIntegerOrInfinity(arguments.argument(1)).asInt.coerceIn(0, string.length)

            return string.indexOf(searchString, pos).toValue()
        }

        @ECMAImpl("22.1.3.9")
        @JvmStatic
        fun lastIndexOf(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            // TODO: RegExp check
            val searchString = Operations.toString(arguments.argument(0)).string
            val numPos = Operations.toNumber(arguments.argument(1))
            val pos = if (numPos.isNaN) {
                string.length
            } else Operations.toIntegerOrInfinity(numPos).asInt.coerceIn(0, string.length)

            return string.lastIndexOf(searchString, pos).toValue()
        }

        @ECMAImpl("22.1.3.14")
        @JvmStatic
        fun padEnd(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            return stringPad(obj, arguments.argument(0), arguments.argument(1), false).toValue()
        }

        @ECMAImpl("22.1.3.15")
        @JvmStatic
        fun padStart(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            return stringPad(obj, arguments.argument(0), arguments.argument(1), true).toValue()
        }

        @ECMAImpl("22.1.3.16")
        @JvmStatic
        fun repeat(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            val count = Operations.toIntegerOrInfinity(arguments.argument(0)).asInt
            if (count < 0 || count == Int.MAX_VALUE)
                Errors.TODO("String.prototype.repeat").throwRangeError()
            return string.repeat(count).toValue()
        }

        @ECMAImpl("22.1.3.17")
        @JvmStatic
        fun replace(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            var (searchValue, replaceValue) = arguments.takeArgs(0..1)

            if (!searchValue.isNullish) {
                val replacer = Operations.getMethod(searchValue, Realm.WellKnownSymbols.replace)
                if (replacer != JSUndefined)
                    return Operations.call(replacer, searchValue, listOf(obj, replaceValue))
            }

            val string = Operations.toString(obj).string
            val searchString = Operations.toString(searchValue).asString
            val functionalReplace = Operations.isCallable(replaceValue)
            if (!functionalReplace)
                replaceValue = Operations.toString(replaceValue)

            val position = string.indexOf(searchString)
            if (position == -1)
                return string.toValue()

            val preserved = string.substring(0, position)
            val replacement = if (functionalReplace) {
                Operations.toString(
                    Operations.call(
                        replaceValue,
                        JSUndefined,
                        listOf(searchString.toValue(), position.toValue(), string.toValue())
                    )
                ).asString
            } else {
                getSubstitution(searchString, string, position, emptyList(), emptyMap(), replaceValue.asString)
            }

            return (preserved + replacement + string.substring(position + searchString.length)).toValue()
        }

        @ECMAImpl("22.1.3.20")
        @JvmStatic
        fun slice(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            val from = Operations.toIntegerOrInfinity(arguments.argument(0)).let {
                when {
                    it.isNegativeInfinity -> 0
                    it.asInt < 0 -> max(string.length + it.asInt, 0)
                    else -> min(it.asInt, string.length)
                }
            }

            val to = if (arguments.size > 1) {
                Operations.toIntegerOrInfinity(arguments[1])
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

        @ECMAImpl("22.1.3.21")
        @JvmStatic
        fun split(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val (separator, limit) = arguments.takeArgs(0..1)

            if (!separator.isNullish) {
                val splitter = Operations.getMethod(separator, Realm.WellKnownSymbols.split)
                if (splitter != JSUndefined)
                    return Operations.call(splitter, separator, listOf(obj, limit))
            }

            val string = Operations.toString(obj).string
            val array = Operations.arrayCreate(0)
            var arrayLength = 0
            val lim = if (limit == JSUndefined) {
                Operations.MAX_32BIT_INT - 1
            } else Operations.toUint32(limit).asInt

            val separatorString = Operations.toString(separator).string
            if (lim == 0)
                return array
            if (separator == JSUndefined) {
                Operations.createDataPropertyOrThrow(array, 0.key(), string.toValue())
                return array
            }

            val stringLength = string.length
            if (stringLength == 0) {
                if (separatorString.isNotEmpty())
                    Operations.createDataPropertyOrThrow(array, 0.key(), string.toValue())
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

                Operations.createDataPropertyOrThrow(
                    array,
                    arrayLength.key(),
                    string.substring(splitStart, splitEnd).toValue(),
                )
                arrayLength++
                if (arrayLength == lim)
                    return array
                splitStart = splitMatchIndex
                splitEnd = splitStart
            }

            Operations.createDataPropertyOrThrow(
                array,
                arrayLength.key(),
                string.substring(splitStart, stringLength).toValue(),
            )

            return array
        }

        @ECMAImpl("22.1.3.22")
        @JvmStatic
        fun startsWith(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            // TODO: RegExp check
            val searchString = Operations.toString(arguments.argument(0)).string

            val start = arguments.argument(1).let {
                if (it == JSUndefined) {
                    0
                } else Operations.toIntegerOrInfinity(it).asInt
            }.coerceIn(0, string.length)

            if (searchString.isEmpty())
                return JSTrue

            val end = start + searchString.length
            if (end > string.length)
                return JSFalse

            return (string.substring(start, end) == searchString).toValue()
        }

        @ECMAImpl("22.1.3.23")
        @JvmStatic
        fun substring(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            val intStart = Operations.toIntegerOrInfinity(arguments.argument(0)).asInt
            val intEnd = if (arguments.size < 2) {
                string.length
            } else Operations.toIntegerOrInfinity(arguments[1]).asInt

            val finalStart = intStart.coerceIn(0, string.length)
            val finalEnd = intEnd.coerceIn(0, string.length)

            val from = min(finalStart, finalEnd)
            val to = max(finalStart, finalEnd)

            return string.substring(from, to).toValue()
        }

        @ECMAImpl("22.1.3.26")
        @JvmStatic
        fun toLowerCase(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            // TODO: unicode handling
            return string.lowercase().toValue()
        }

        @ECMAImpl("22.1.3.27")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            return thisStringValue(arguments.thisValue, "toString")
        }

        @ECMAImpl("22.1.3.28")
        @JvmStatic
        fun toUpperCase(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string

            // TODO: unicode handling
            return string.uppercase().toValue()
        }

        @ECMAImpl("22.1.3.29")
        @JvmStatic
        fun trim(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            return string.trim().toValue()
        }

        @ECMAImpl("22.1.3.39")
        @JvmStatic
        fun trimEnd(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            return string.trimEnd().toValue()
        }

        @ECMAImpl("22.1.3.31")
        @JvmStatic
        fun trimStart(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = Operations.toString(obj).string
            return string.trimStart().toValue()
        }

        @ECMAImpl("22.1.3.32")
        @JvmStatic
        fun valueOf(arguments: JSArguments): JSValue {
            return thisStringValue(arguments.thisValue, "valueOf")
        }

        @ECMAImpl("22.1.3.34")
        @JvmStatic
        fun symbolIterator(arguments: JSArguments): JSValue {
            val obj = Operations.requireObjectCoercible(arguments.thisValue)
            val string = obj.toJSString().string
            return JSStringIteratorObject.create(string)
        }

        private fun stringPad(
            obj: JSValue,
            maxLength: JSValue,
            fillString: JSValue,
            isStart: Boolean,
        ): String {
            val string = Operations.toString(obj).string
            val intMaxLength = Operations.toLength(maxLength).asInt
            if (intMaxLength < string.length)
                return string
            val filler = if (fillString == JSUndefined) {
                " "
            } else Operations.toString(fillString).string

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
    }
}
