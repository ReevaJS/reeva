package me.mattco.reeva.runtime.values.exotics

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.annotations.JSThrows
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.arrays.JSArray
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.Attributes
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.runtime.values.wrappers.JSBigIntObject
import me.mattco.reeva.runtime.values.wrappers.JSBooleanObject
import me.mattco.reeva.runtime.values.wrappers.JSNumberObject
import me.mattco.reeva.runtime.values.wrappers.JSStringObject
import me.mattco.reeva.utils.*
import kotlin.math.floor
import kotlin.math.min

class JSONObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSNativePropertyGetter("@@toStringTag", Attributes.CONFIGURABLE)
    fun `get@@toStringTag`(thisValue: JSValue): JSValue {
        return "JSON".toValue()
    }

    @ECMAImpl("JSON.parse", "24.5.1")
    @JSMethod("parse", 2, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun parse(thisValue: JSValue, arguments: JSArguments): JSValue {
        TODO()
    }

    @JSThrows
    @ECMAImpl("JSON.stringify", "24.5.2")
    @JSMethod("stringify", 3, Attributes.CONFIGURABLE and Attributes.WRITABLE)
    fun stringify(thisValue: JSValue, arguments: JSArguments): JSValue {
        // TODO: ReplacerFunction

        val value = arguments.argument(0)
        val replacer = arguments.argument(1)
        var space = arguments.argument(2)

        var gap = ""

        if (replacer.isObject) {
            TODO()
        }

        if (space.isObject) {
            if (space is JSNumberObject) {
                space = space.number
            } else if (space is JSStringObject) {
                space = space.string
            }
        }

        if (space.isNumber) {
            repeat(min(10, Operations.toIntegerOrInfinity(space).asInt)) {
                gap += " "
            }
            checkError() ?: return INVALID_VALUE
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
        checkError() ?: return INVALID_VALUE

        return serializeJSONProperty(state, "", wrapper)?.toValue() ?: JSUndefined
    }

    private fun serializeJSONProperty(state: SerializeState, key: String, holder: JSObject): String? {
        var value = holder.get(key)
        checkError() ?: return null
        if (value.isObject || value.isBigInt) {
            val toJSON = Operations.getV(value, "toJSON".toValue())
            checkError() ?: return null
            if (Operations.isCallable(toJSON)) {
                value = Operations.call(toJSON, value, listOf(key.toValue()))
                checkError() ?: return null
            }
        }
        if (value.isObject) {
            when (value) {
                is JSNumberObject -> value = Operations.toNumber(value)
                is JSStringObject -> value = Operations.toString(value)
                is JSBooleanObject -> value = value.value
                is JSBigIntObject -> value = value.value
            }
            checkError() ?: return null
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
        if (value.isBigInt) {
            throwError<JSTypeErrorObject>("JSON.stringify cannot serialize BigInt values")
            return null
        }
        if (value.isObject && !Operations.isCallable(value)) {
            if (Operations.isArray(value))
                return serializeJSONArray(state, value as JSArray)
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

    private fun serializeJSONArray(state: SerializeState, value: JSArray): String {
        TODO()
    }

    private fun serializeJSONObject(state: SerializeState, value: JSObject): String {
        if (value in state.stack) {
            throwError<JSTypeErrorObject>("JSON.stringify cannot stringify circular objects")
            return ""
        }

        state.stack.add(value)
        val stepback = state.indent
        state.indent += state.gap
        val partial = mutableListOf<String>()

        Operations.enumerableOwnPropertyNames(value, JSObject.PropertyKind.Key).forEach { property ->
            val strP = serializeJSONProperty(state, property.asString, value)
            if (strP != null) {
                partial.add(buildString {
                    append(quoteJSONString(property.asString))
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
        fun create(realm: Realm) = JSONObject(realm).also { it.init() }
    }
}
