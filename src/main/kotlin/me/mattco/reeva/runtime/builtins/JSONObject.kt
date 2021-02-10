package me.mattco.reeva.runtime.builtins

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.*
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.arrays.JSArrayObject
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSBigInt
import me.mattco.reeva.runtime.wrappers.JSBigIntObject
import me.mattco.reeva.runtime.wrappers.JSBooleanObject
import me.mattco.reeva.runtime.wrappers.JSNumberObject
import me.mattco.reeva.runtime.wrappers.JSStringObject
import me.mattco.reeva.utils.*
import kotlin.math.min

class JSONObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineNativeProperty(Realm.`@@toStringTag`.key(), attrs { +conf -enum -writ }, ::`get@@toStringTag`, null)
        defineNativeFunction("parse", 2, function = ::parse)
        defineNativeFunction("stringify", 3, function = ::stringify)
    }

    fun `get@@toStringTag`(thisValue: JSValue): JSValue {
        return "JSON".toValue()
    }

    @ECMAImpl("24.5.1")
    fun parse(arguments: JSArguments): JSValue {
        TODO()
    }

    @ECMAImpl("24.5.2")
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
            if (space.hasSlot(SlotName.NumberData)) {
                space = space.toNumber()
            } else if (space.hasSlot(SlotName.StringData)) {
                space = Operations.toString(space)
            }
        }

        if (space.isNumber) {
            repeat(min(10, Operations.toIntegerOrInfinity(space).asInt)) {
                gap += " "
            }
        } else if (space.isString) {
            val str = space.asString
            gap = if (str.length <= 10) str else str.substring(0, 10)
        }

        val wrapper = JSObject.create(realm)
        Operations.createDataPropertyOrThrow(wrapper, "".toValue(), value)
        val state = SerializeState(
            mutableListOf(),
            "",
            gap,
            mutableListOf()
        )

        return serializeJSONProperty(state, "".key(), wrapper)?.toValue() ?: JSUndefined
    }

    private fun serializeJSONProperty(state: SerializeState, key: PropertyKey, holder: JSObject): String? {
        var value = holder.get(key)
        if (value is JSObject || value is JSBigInt) {
            val toJSON = Operations.getV(value, "toJSON".toValue())
            if (Operations.isCallable(toJSON)) {
                value = Operations.call(toJSON, value, listOf(key.asValue))
            }
        }
        if (value is JSObject) {
            value = when {
                value.hasSlot(SlotName.NumberData) -> value.toNumber()
                value.hasSlot(SlotName.StringData) -> Operations.toString(value)
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
                return Operations.toString(value).string
            }
            return "null"
        }
        if (value.isBigInt)
            Errors.JSON.StringifyBigInt.throwTypeError()
        if (value.isObject && !Operations.isCallable(value)) {
            if (Operations.isArray(value))
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
        val len = Operations.lengthOfArrayLike(value)
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

        Operations.enumerableOwnPropertyNames(value, PropertyKind.Key).forEach { property ->
            val strP = serializeJSONProperty(state, Operations.toPropertyKey(property), value)
            if (strP != null) {
                partial.add(buildString {
                    append(quoteJSONString(Operations.toString(property).string))
                    append(":")
                    if (state.gap.isNotEmpty())
                        append(" ")
                    append(strP)
                })
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

    data class SerializeState(
        val stack: MutableList<JSObject>,
        var indent: String,
        val gap: String,
        val propertyList: MutableList<String>,
    )

    companion object {
        fun create(realm: Realm) = JSONObject(realm).initialize()
    }
}
