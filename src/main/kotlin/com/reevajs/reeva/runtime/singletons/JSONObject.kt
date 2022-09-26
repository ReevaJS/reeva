package com.reevajs.reeva.runtime.singletons

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import kotlin.math.min

class JSONObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltinGetter(Realm.WellKnownSymbols.toStringTag, ::getSymbolToStringTag, attrs { +conf; -enum; -writ })
        defineBuiltin("parse", 2, ::parse)
        defineBuiltin("stringify", 3, ::stringify)
    }

    private data class SerializeState(
        val stack: MutableList<JSObject>,
        var indent: String,
        val gap: String,
        val propertyList: MutableList<String>,
    )

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSONObject(realm).initialize()

        @ECMAImpl("24.5.1")
        @JvmStatic
        fun parse(arguments: JSArguments): JSValue {
            val text = arguments.argument(0).toJSString().string
            val reviver = arguments.argument(1).let {
                if (AOs.isCallable(it)) it else null
            }

            val result = try {
                Json.Default.parseToJsonElement(text)
            } catch (e: SerializationException) {
                Error(e.message ?: "An error occurred while parsing JSON").throwSyntaxError()
            }
            return parseHelper(result, reviver)
        }

        private fun defineKeyValueProperty(
            key: JSValue,
            element: JsonElement,
            value: JSObject,
            reviver: JSValue?,
        ) {
            val result = parseHelper(element, reviver)
            if (reviver != null) {
                val revived = AOs.call(reviver, value, listOf(key, result))
                if (revived != JSUndefined)
                    AOs.createDataProperty(value, key, revived)
            } else {
                AOs.createDataProperty(value, key, result)
            }
        }

        private fun parseHelper(element: JsonElement, reviver: JSValue?): JSValue {
            return when (element) {
                JsonNull -> JSNull
                is JsonPrimitive -> if (element.isString) {
                    JSString(element.content)
                } else JSNumber(element.content.toDouble())
                is JsonObject -> {
                    val obj = JSObject.create()
                    for ((key, value) in element)
                        defineKeyValueProperty(key.toValue(), value, obj, reviver)
                    obj
                }
                is JsonArray -> {
                    val arr = JSArrayObject.create()
                    for ((index, value) in element.withIndex())
                        defineKeyValueProperty(index.toString().toValue(), value, arr, reviver)
                    arr
                }
            }
        }

        @ECMAImpl("24.5.2")
        @JvmStatic
        fun stringify(arguments: JSArguments): JSValue {
            // TODO: ReplacerFunction

            val value = arguments.argument(0)
            val replacer = arguments.argument(1)
            var space = arguments.argument(2)

            var gap = ""

            if (replacer is JSObject) {
                TODO()
            }

            if (space is JSObject) {
                if (SlotName.NumberData in space) {
                    space = space.toNumber()
                } else if (SlotName.StringData in space) {
                    space = space.toJSString()
                }
            }

            if (space.isNumber) {
                repeat(min(10, space.toIntegerOrInfinity().asInt)) {
                    gap += " "
                }
            } else if (space.isString) {
                val str = space.asString
                gap = if (str.length <= 10) str else str.substring(0, 10)
            }

            val wrapper = JSObject.create()
            AOs.createDataPropertyOrThrow(wrapper, "".toValue(), value)
            val state = SerializeState(
                mutableListOf(),
                "",
                gap,
                mutableListOf()
            )

            return serializeJSONProperty(state, "".key(), wrapper)?.toValue() ?: JSUndefined
        }

        @ECMAImpl("25.5.3")
        @JvmStatic
        fun getSymbolToStringTag(arguments: JSArguments): JSValue {
            return "JSON".toValue()
        }

        private fun serializeJSONProperty(
            state: SerializeState,
            key: PropertyKey,
            holder: JSObject,
        ): String? {
            var value = holder.get(key)
            if (value is JSObject || value is JSBigInt) {
                val toJSON = AOs.getV(value, "toJSON".toValue())
                if (AOs.isCallable(toJSON)) {
                    value = AOs.call(toJSON, value, listOf(key.asValue))
                }
            }
            if (value is JSObject) {
                value = when {
                    SlotName.NumberData in value -> value.toNumber()
                    SlotName.StringData in value -> value.toJSString()
                    SlotName.BooleanData in value -> value[SlotName.BooleanData]
                    SlotName.BigIntData in value -> value[SlotName.BigIntData]
                    else -> value
                }
            }
            if (value.isNull)
                return "null"
            if (value == JSTrue)
                return "true"
            if (value == JSFalse)
                return "false"
            if (value.isString)
                return quoteJSONString(value.asString)
            if (value.isNumber) {
                if (value.isFinite) {
                    return value.toJSString().string
                }
                return "null"
            }
            if (value.isBigInt)
                Errors.JSON.StringifyBigInt.throwTypeError()
            if (value.isObject && !AOs.isCallable(value)) {
                if (AOs.isArray(value))
                    return serializeJSONArray(state, value as JSArrayObject)
                return serializeJSONObject(state, value as JSObject)
            }
            return null
        }

        private fun quoteJSONString(value: String): String {
            return buildString {
                append('"')
                value.codePoints().forEach { ch ->
                    when {
                        ch == 0x8 -> append("\\b")
                        ch == 0x9 -> append("\\t")
                        ch == 0xa -> append("\\n")
                        ch == 0xc -> append("\\f")
                        ch == 0xd -> append("\\r")
                        ch == 0x22 -> append("\\\"")
                        ch == 0x5c -> append("\\\\")
                        ch <= 0x20 -> append(unicodeEscape(ch))
                        ch > 0xffff -> codePointToUtf16CodeUnits(ch).forEach { append(unicodeEscape(it)) }
                        else -> append(ch.toChar())
                    }
                }
                append('"')
            }
        }

        private fun unicodeEscape(value: Int): String {
            expect(value <= 0xffff)
            return buildString {
                append("\\u")
                append("%04x".format(value))
            }
        }

        private fun codePointToUtf16CodeUnits(codepoint: Int): List<Int> {
            if (codepoint <= 0xffff)
                return listOf(codepoint)
            val c1 = ((codepoint - 0x10000) / 0x400) + 0xd800
            val c2 = ((codepoint - 0x10000) % 0x400) + 0xdc00
            return listOf(c1, c2)
        }

        private fun serializeJSONArray(state: SerializeState, value: JSArrayObject): String {
            if (value in state.stack)
                Errors.JSON.StringifyCircular.throwTypeError()

            state.stack.add(value)
            val stepback = state.indent
            state.indent += state.gap
            val partial = mutableListOf<String>()
            val len = AOs.lengthOfArrayLike(value)
            for (i in 0 until len) {
                val strP = serializeJSONProperty(state, i.key(), value)
                partial.add(strP ?: "null")
            }
            val final = when {
                partial.isEmpty() -> "[]"
                state.gap.isEmpty() -> buildString {
                    append("[")
                    append(partial.joinToString(","))
                    append("]")
                }
                else -> buildString {
                    append("[\n")
                    append(state.indent)
                    append(partial.joinToString(",\n${state.indent}"))
                    append("\n")
                    append(stepback)
                    append("]")
                }
            }

            state.stack.removeLast()
            state.indent = stepback
            return final
        }

        private fun serializeJSONObject(state: SerializeState, value: JSObject): String {
            if (value in state.stack)
                Errors.JSON.StringifyCircular.throwTypeError()

            state.stack.add(value)
            val stepback = state.indent
            state.indent += state.gap
            val partial = mutableListOf<String>()

            AOs.enumerableOwnPropertyNames(value, PropertyKind.Key).forEach { property ->
                val strP = serializeJSONProperty(state, property.toPropertyKey(), value)
                if (strP != null) {
                    partial.add(
                        buildString {
                            append(quoteJSONString(property.toJSString().string))
                            append(":")
                            if (state.gap.isNotEmpty())
                                append(" ")
                            append(strP)
                        }
                    )
                }
            }

            val final = when {
                partial.isEmpty() -> "{}"
                state.gap.isEmpty() -> buildString {
                    append("{")
                    append(partial.joinToString(","))
                    append("}")
                }
                else -> buildString {
                    append("{")
                    append("\n")
                    append(state.indent)
                    append(partial.joinToString(",\n${state.indent}"))
                    append("\n")
                    append(stepback)
                    append("}")
                }
            }

            state.stack.removeLast()
            state.indent = stepback
            return final
        }
    }
}
