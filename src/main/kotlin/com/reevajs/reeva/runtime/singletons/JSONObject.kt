package com.reevajs.reeva.runtime.singletons

import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
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

        defineBuiltinGetter(Realm.WellKnownSymbols.toStringTag, ReevaBuiltin.JSONGetSymbolToStringTag, attrs { +conf; -enum; -writ })
        defineBuiltin("parse", 2, ReevaBuiltin.JSONParse)
        defineBuiltin("stringify", 3, ReevaBuiltin.JSONStringify)
    }

    private data class SerializeState(
        val stack: MutableList<JSObject>,
        var indent: String,
        val gap: String,
        val propertyList: MutableList<String>,
    )

    companion object {
        fun create(realm: Realm) = JSONObject(realm).initialize()

        @ECMAImpl("24.5.1")
        @JvmStatic
        fun parse(realm: Realm, arguments: JSArguments): JSValue {
            val text = arguments.argument(0).toJSString(realm).string
            val reviver = arguments.argument(1).let {
                if (Operations.isCallable(it)) it else null
            }

            val result = try {
                Json.Default.parseToJsonElement(text)
            } catch (e: SerializationException) {
                Error(e.message ?: "An error occurred while parsing JSON").throwSyntaxError(realm)
            }
            return parseHelper(realm, result, reviver)
        }

        private fun defineKeyValueProperty(
            realm: Realm,
            key: JSValue,
            element: JsonElement,
            value: JSObject,
            reviver: JSValue?,
        ) {
            val result = parseHelper(realm, element, reviver)
            if (reviver != null) {
                val revived = Operations.call(realm, reviver, value, listOf(key, result))
                if (revived != JSUndefined)
                    Operations.createDataProperty(realm, value, key, revived)
            } else {
                Operations.createDataProperty(realm, value, key, result)
            }
        }

        private fun parseHelper(realm: Realm, element: JsonElement, reviver: JSValue?): JSValue {
            return when (element) {
                JsonNull -> JSNull
                is JsonPrimitive -> if (element.isString) {
                    JSString(element.content)
                } else JSNumber(element.content.toDouble())
                is JsonObject -> {
                    val obj = JSObject.create(realm)
                    for ((key, value) in element)
                        defineKeyValueProperty(realm, key.toValue(), value, obj, reviver)
                    obj
                }
                is JsonArray -> {
                    val arr = JSArrayObject.create(realm)
                    for ((index, value) in element.withIndex())
                        defineKeyValueProperty(realm, index.toString().toValue(), value, arr, reviver)
                    arr
                }
            }
        }

        @ECMAImpl("24.5.2")
        @JvmStatic
        fun stringify(realm: Realm, arguments: JSArguments): JSValue {
            // TODO: ReplacerFunction

            val value = arguments.argument(0)
            val replacer = arguments.argument(1)
            var space = arguments.argument(2)

            var gap = ""

            if (replacer is JSObject) {
                TODO()
            }

            if (space is JSObject) {
                if (space.hasSlot(SlotName.NumberData)) {
                    space = space.toNumber(realm)
                } else if (space.hasSlot(SlotName.StringData)) {
                    space = Operations.toString(realm, space)
                }
            }

            if (space.isNumber) {
                repeat(min(10, Operations.toIntegerOrInfinity(realm, space).asInt)) {
                    gap += " "
                }
            } else if (space.isString) {
                val str = space.asString
                gap = if (str.length <= 10) str else str.substring(0, 10)
            }

            val wrapper = JSObject.create(realm)
            Operations.createDataPropertyOrThrow(realm, wrapper, "".toValue(), value)
            val state = SerializeState(
                mutableListOf(),
                "",
                gap,
                mutableListOf()
            )

            return serializeJSONProperty(realm, state, "".key(), wrapper)?.toValue() ?: JSUndefined
        }

        @ECMAImpl("25.5.3")
        @JvmStatic
        fun getSymbolToStringTag(realm: Realm, arguments: JSArguments): JSValue {
            return "JSON".toValue()
        }

        private fun serializeJSONProperty(
            realm: Realm,
            state: SerializeState,
            key: PropertyKey,
            holder: JSObject,
        ): String? {
            var value = holder.get(key)
            if (value is JSObject || value is JSBigInt) {
                val toJSON = Operations.getV(realm, value, "toJSON".toValue())
                if (Operations.isCallable(toJSON)) {
                    value = Operations.call(realm, toJSON, value, listOf(key.asValue))
                }
            }
            if (value is JSObject) {
                value = when {
                    value.hasSlot(SlotName.NumberData) -> value.toNumber(realm)
                    value.hasSlot(SlotName.StringData) -> Operations.toString(realm, value)
                    value.hasSlot(SlotName.BooleanData) -> value.getSlotAs(SlotName.BooleanData)
                    value.hasSlot(SlotName.BigIntData) -> value.getSlotAs(SlotName.BigIntData)
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
                    return Operations.toString(realm, value).string
                }
                return "null"
            }
            if (value.isBigInt)
                Errors.JSON.StringifyBigInt.throwTypeError(realm)
            if (value.isObject && !Operations.isCallable(value)) {
                if (Operations.isArray(realm, value))
                    return serializeJSONArray(realm, state, value as JSArrayObject)
                return serializeJSONObject(realm, state, value as JSObject)
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

        private fun serializeJSONArray(realm: Realm, state: SerializeState, value: JSArrayObject): String {
            if (value in state.stack)
                Errors.JSON.StringifyCircular.throwTypeError(realm)

            state.stack.add(value)
            val stepback = state.indent
            state.indent += state.gap
            val partial = mutableListOf<String>()
            val len = Operations.lengthOfArrayLike(realm, value)
            for (i in 0 until len) {
                val strP = serializeJSONProperty(realm, state, i.key(), value)
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

        private fun serializeJSONObject(realm: Realm, state: SerializeState, value: JSObject): String {
            if (value in state.stack)
                Errors.JSON.StringifyCircular.throwTypeError(realm)

            state.stack.add(value)
            val stepback = state.indent
            state.indent += state.gap
            val partial = mutableListOf<String>()

            Operations.enumerableOwnPropertyNames(realm, value, PropertyKind.Key).forEach { property ->
                val strP = serializeJSONProperty(realm, state, Operations.toPropertyKey(realm, property), value)
                if (strP != null) {
                    partial.add(
                        buildString {
                            append(quoteJSONString(Operations.toString(realm, property).string))
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
