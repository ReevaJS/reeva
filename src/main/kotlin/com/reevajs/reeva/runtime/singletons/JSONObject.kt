package com.reevajs.reeva.runtime.singletons

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

class JSONObject private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltinGetter(Realm.WellKnownSymbols.toStringTag, ::getSymbolToStringTag, attrs { +conf; -enum; -writ })
        defineBuiltin("parse", 2, ::parse)
        defineBuiltin("stringify", 3, ::stringify)
    }

    private data class SerializeRecord(
        val replacerFunction: JSFunction?,
        val stack: MutableList<JSObject>,
        var indent: String,
        val gap: String,
        val propertyList: MutableList<String>?,
    )

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSONObject(realm).initialize()

        @ECMAImpl("25.5.1")
        @JvmStatic
        fun parse(arguments: JSArguments): JSValue {
            // 1. Let jsonString be ? ToString(text).
            val jsonString = arguments.argument(0).toJSString().string

            // 2. Parse StringToCodePoints(jsonString) as a JSON text as specified in ECMA-404. Throw a SyntaxError
            //    exception if it is not a valid JSON text as defined in that specification.
            // 3. Let scriptString be the string-concatenation of "(", jsonString, and ");".
            // 4. Let script be ParseText(StringToCodePoints(scriptString), Script).
            // 5. NOTE: The early error rules defined in 13.2.5.1 have special handling for the above invocation of
            //    ParseText.
            // 6. Assert: script is a Parse Node.
            // 7. Let completion be the result of evaluating script.
            // 8. NOTE: The PropertyDefinitionEvaluation semantics defined in 13.2.5.5 have special handling for the
            //    above evaluation.
            // 9. Let unfiltered be completion.[[Value]].
            val jsonValue = try {
                Json.Default.parseToJsonElement(jsonString)
            } catch (e: SerializationException) {
                Error(e.message ?: "An error occurred while parsing JSON").throwSyntaxError()
            }

            val unfiltered = jsonElementToJSValue(jsonValue)

            // 10. Assert: unfiltered is either a String, Number, Boolean, Null, or an Object that is defined by either
            //     an ArrayLiteral or an ObjectLiteral.
            ecmaAssert(unfiltered.let { it.isString || it.isNumber || it.isBoolean || it.isNull || it.isObject })

            // 11. If IsCallable(reviver) is true, then
            val reviver = arguments.argument(1)
            if (AOs.isCallable(reviver)) {
                // a. Let root be OrdinaryObjectCreate(%Object.prototype%).
                val root = AOs.ordinaryObjectCreate(Agent.activeAgent.getActiveRealm().objectProto)

                // b. Let rootName be the empty String.
                val rootName = JSString.EMPTY

                // c. Perform ! CreateDataPropertyOrThrow(root, rootName, unfiltered).
                AOs.createDataPropertyOrThrow(root, rootName, unfiltered)

                // d. Return ? InternalizeJSONProperty(root, rootName, reviver).
                return internalizeJSONProperty(root, rootName, reviver)
            }
            // 12. Else,
            else {
                // a. Return unfiltered.
                return unfiltered
            }
        }

        private fun jsonElementToJSValue(element: JsonElement): JSValue {
            return when (element) {
                JsonNull -> JSNull
                is JsonPrimitive -> when {
                    element.isString -> JSString(element.content)
                    element.content == "true" -> JSTrue
                    element.content == "false" -> JSFalse
                    else -> element.content.toDoubleOrNull()?.let(::JSNumber)
                        ?: Error("An error occurred while parsing JSON").throwSyntaxError()
                }
                is JsonArray -> {
                    val array = JSArrayObject.create()
                    for ((index, value) in element.withIndex())
                        array.indexedProperties.set(array, index, jsonElementToJSValue(value))
                    array
                }
                is JsonObject -> {
                    val obj = JSObject.create()
                    for ((key, value) in element)
                        AOs.definePropertyOrThrow(obj, key.key(), Descriptor(jsonElementToJSValue(value)))
                    obj
                }
            }
        }

        private fun internalizeJSONProperty(holder: JSObject, name: JSString, reviver: JSFunction): JSValue {
            // 1. Let val be ? Get(holder, name).
            val value = holder.get(name.key())

            // 2. If Type(val) is Object, then
            if (value is JSObject) {
                // a. Let isArray be ? IsArray(val).
                // b. If isArray is true, then
                if (AOs.isArray(value)) {
                    // i. Let I be 0.
                    var i = 0

                    // ii. Let len be ? LengthOfArrayLike(val).
                    val len = AOs.lengthOfArrayLike(value)

                    // iii. Repeat, while I < len,
                    while (i < len) {
                        // 1. Let prop be ! ToString(𝔽(I)).
                        val prop = JSString(i.toString())

                        // 2. Let newElement be ? InternalizeJSONProperty(val, prop, reviver).
                        val newElement = internalizeJSONProperty(value, prop, reviver)

                        // 3. If newElement is undefined, then
                        if (newElement == JSUndefined) {
                            // a. Perform ? val.[[Delete]](prop).
                            value.delete(prop.string)
                        }
                        // 4. Else,
                        else {
                            // a. Perform ? CreateDataProperty(val, prop, newElement).
                            AOs.createDataProperty(value, prop, newElement)
                        }

                        // 5. Set I to I + 1.
                        i++
                    }
                }
                // c. Else,
                else {
                    // i. Let keys be ? EnumerableOwnPropertyNames(val, key).
                    val keys = AOs.enumerableOwnPropertyNames(value, JSObject.PropertyKind.Key)

                    // ii. For each String P of keys, do
                    for (key in keys.map { it as JSString }) {
                        // 1. Let newElement be ? InternalizeJSONProperty(val, P, reviver).
                        val newElement = internalizeJSONProperty(value, key, reviver)

                        // 2. If newElement is undefined, then
                        if (newElement == JSUndefined) {
                            // a. Perform ? val.[[Delete]](P).
                            value.delete(key.string)
                        }
                        // 3. Else,
                        else {
                            // a. Perform ? CreateDataProperty(val, P, newElement).
                            AOs.createDataProperty(value, key, newElement)
                        }
                    }
                }
            }

            // 3. Return ? Call(reviver, holder, « name, val »).
            return AOs.call(reviver, holder, listOf(name, value))
        }

        @ECMAImpl("25.5.2")
        @JvmStatic
        fun stringify(arguments: JSArguments): JSValue {
            val value = arguments.argument(0)
            val replacer = arguments.argument(1)
            var space = arguments.argument(2)

            // 1. Let stack be a new empty List.
            val stack = mutableListOf<JSObject>()

            // 2. Let indent be the empty String.
            val indent = ""

            // 3. Let PropertyList and ReplacerFunction be undefined.
            var replacerFunction: JSFunction? = null
            var propertyList: MutableList<String>? = null

            // 4. If Type(replacer) is Object, then
            if (replacer is JSObject) {
                // a. If IsCallable(replacer) is true, then
                if (AOs.isCallable(replacer)) {
                    // i. Set ReplacerFunction to replacer.
                    replacerFunction = replacer
                }
                // b. Else,
                else {
                    // i. Let isArray be ? IsArray(replacer).
                    // ii. If isArray is true, then
                    if (AOs.isArray(replacer)) {
                        // 1. Set PropertyList to a new empty List.
                        propertyList = mutableListOf()

                        // 2. Let len be ? LengthOfArrayLike(replacer).
                        val len = AOs.lengthOfArrayLike(replacer)

                        // 3. Let k be 0.
                        var k = 0

                        // 4. Repeat, while k < len,
                        while (k < len) {
                            // a. Let prop be ! ToString(𝔽(k)).
                            val prop = k.toString()

                            // b. Let v be ? Get(replacer, prop).
                            val v = replacer.get(prop)

                            // c. Let item be undefined.
                            var item: String? = null

                            // d. If Type(v) is String, set item to v.
                            if (v is JSString) {
                                item = v.string
                            }
                            // e. Else if Type(v) is Number, set item to ! ToString(v).
                            else if (v is JSNumber) {
                                item = v.toJSString().string
                            }
                            // f. Else if Type(v) is Object, then
                            else if (v is JSObject) {
                                // i. If v has a [[StringData]] or [[NumberData]] internal slot, set item to
                                //    ? ToString(v).
                                if (SlotName.StringData in v || SlotName.NumberData in v)
                                    item = v.toJSString().string
                            }

                            // g. If item is not undefined and item is not currently an element of PropertyList, then
                            if (item != null && item !in propertyList) {
                                // i. Append item to the end of PropertyList.
                                propertyList.add(item)
                            }

                            // h. Set k to k + 1.
                            k++
                        }
                    }
                }
            }

            // 5. If Type(space) is Object, then
            if (space is JSObject) {
                // a. If space has a [[NumberData]] internal slot, then
                if (SlotName.NumberData in space) {
                    // i. Set space to ? ToNumber(space).
                    space = space.toNumber()
                }
                // b. Else if space has a [[StringData]] internal slot, then
                else if (SlotName.StringData in space) {
                    // i. Set space to ? ToString(space).
                    space = space.toJSString()
                }
            }

            // 6. If Type(space) is Number, then
            val gap = if (space is JSNumber) {
                // a. Let spaceMV be ! ToIntegerOrInfinity(space).
                // b. Set spaceMV to min(10, spaceMV).
                val spaceMV = space.toIntegerOrInfinity().number.toInt().coerceAtMost(10)

                // c. If spaceMV < 1, let gap be the empty String; otherwise let gap be the String value containing
                //    spaceMV occurrences of the code unit 0x0020 (SPACE).
                if (spaceMV < 1) "" else " ".repeat(spaceMV)
            }
            // 8. Else if Type(space) is String, then
            else if (space is JSString) {
                // a. If the length of space is 10 or less, let gap be space; otherwise let gap be the substring of
                //    space from 0 to 10.
                space.string.take(10)
            }
            // 8. Else,
            else {
                // a. Let gap be the empty String.
                ""
            }

            // 9. Let wrapper be OrdinaryObjectCreate(%Object.prototype%).
            val wrapper = AOs.ordinaryObjectCreate(Agent.activeAgent.getActiveRealm().objectProto)

            // 10. Perform ! CreateDataPropertyOrThrow(wrapper, the empty String, value).
            AOs.createDataPropertyOrThrow(wrapper, "".key(), value)

            // 11. Let state be the Record { [[ReplacerFunction]]: ReplacerFunction, [[Stack]]: stack,
            //     [[Indent]]: indent, [[Gap]]: gap, [[PropertyList]]: PropertyList }.
            val state = SerializeRecord(replacerFunction, stack, indent, gap, propertyList)

            // 12. Return ? SerializeJSONProperty(state, the empty String, wrapper).
            return serializeJSONProperty(state, "".key(), wrapper)?.toValue() ?: JSUndefined
        }

        @ECMAImpl("25.5.2.1")
        private fun serializeJSONProperty(
            state: SerializeRecord,
            key: PropertyKey,
            holder: JSObject,
        ): String? {
            // 1. Let value be ? Get(holder, key).
            var value = holder.get(key)

            // 2. If Type(value) is Object or BigInt, then
            if (value is JSObject || value is JSBigInt) {
                // a. Let toJSON be ? GetV(value, "toJSON").
                val toJSON = AOs.getV(value, "toJSON".toValue())

                // b. If IsCallable(toJSON) is true, then
                if (AOs.isCallable(toJSON)) {
                    // i. Set value to ? Call(toJSON, value, « key »).
                    value = AOs.call(toJSON, value, listOf(key.asValue))
                }
            }

            // 3. If state.[[ReplacerFunction]] is not undefined, then
            if (state.replacerFunction != null) {
                // a. Set value to ? Call(state.[[ReplacerFunction]], holder, « key, value »).
                value = AOs.call(state.replacerFunction, holder, listOf(key.asValue, value))
            }

            // 4. If Type(value) is Object, then
            if (value is JSObject) {
                value = when {
                    // a. If value has a [[NumberData]] internal slot, then
                    //    i. Set value to ? ToNumber(value).
                    SlotName.NumberData in value -> value.toNumber()

                    // b. Else if value has a [[StringData]] internal slot, then
                    //    i. Set value to ? ToString(value).
                    SlotName.StringData in value -> value.toJSString()

                    // c. Else if value has a [[BooleanData]] internal slot, then
                    //    i. Set value to value.[[BooleanData]].
                    SlotName.BooleanData in value -> value[SlotName.BooleanData]

                    // d. Else if value has a [[BigIntData]] internal slot, then
                    //    i. Set value to value.[[BigIntData]].
                    SlotName.BigIntData in value -> value[SlotName.BigIntData]

                    else -> value
                }
            }

            // 5. If value is null, return "null".
            if (value.isNull)
                return "null"

            // 6. If value is true, return "true".
            if (value == JSTrue)
                return "true"

            // 7. If value is false, return "false".
            if (value == JSFalse)
                return "false"

            // 8. If Type(value) is String, return QuoteJSONString(value).
            if (value.isString)
                return quoteJSONString(value.asString)

            // 9. If Type(value) is Number, then
            if (value.isNumber) {
                // a. If value is finite, return ! ToString(value).
                if (value.isFinite)
                    return value.toJSString().string

                // b. Return "null".
                return "null"
            }

            // 10. If Type(value) is BigInt, throw a TypeError exception.
            if (value.isBigInt)
                Errors.JSON.StringifyBigInt.throwTypeError()

            // 11. If Type(value) is Object and IsCallable(value) is false, then
            if (value.isObject && !AOs.isCallable(value)) {
                // a. Let isArray be ? IsArray(value).
                // b. If isArray is true, return ? SerializeJSONArray(state, value).
                if (AOs.isArray(value))
                    return serializeJSONArray(state, value as JSObject)

                // c. Return ? SerializeJSONObject(state, value).
                return serializeJSONObject(state, value as JSObject)
            }

            // 12. Return undefined.
            return null
        }

        @ECMAImpl("25.5.2.2")
        private fun quoteJSONString(value: String): String {

            return buildString {
                // 1. Let product be the String value consisting solely of the code unit 0x0022 (QUOTATION MARK).
                append('"')

                // 2. For each code point C of StringToCodePoints(value), do
                value.codePoints().forEach { ch ->
                    when {
                        // a. If C is listed in the “Code Point” column of Table 72, then
                        //    i. Set product to the string-concatenation of product and the escape sequence for C as specified in the “Escape Sequence” column of the corresponding row.
                        ch == 0x8 -> append("\\b")
                        ch == 0x9 -> append("\\t")
                        ch == 0xa -> append("\\n")
                        ch == 0xc -> append("\\f")
                        ch == 0xd -> append("\\r")
                        ch == 0x22 -> append("\\\"")
                        ch == 0x5c -> append("\\\\")

                        // b. Else if C has a numeric value less than 0x0020 (SPACE), or if C has the same numeric value
                        //    as a leading surrogate or trailing surrogate, then
                        ch <= 0x20 || ch in 0xd800..0xdbff || ch in 0xdc00..0xdfff -> {
                            // i. Let unit be the code unit whose numeric value is that of C.
                            // ii. Set product to the string-concatenation of product and UnicodeEscape(unit).
                            append(unicodeEscape(ch))
                        }

                        // c. Else,
                        //    i. Set product to the string-concatenation of product and UTF16EncodeCodePoint(C).
                        else -> AOs.utf16EncodeCodePoint(ch).forEach(::appendCodePoint)
                    }
                }

                // 3. Set product to the string-concatenation of product and the code unit 0x0022 (QUOTATION MARK).
                append('"')

                // 4. Return product.
            }
        }

        @ECMAImpl("25.5.2.3")
        private fun unicodeEscape(value: Int): String {
            // 1. Let n be the numeric value of C.
            // 2. Assert: n ≤ 0xFFFF.
            ecmaAssert(value <= 0xffff)

            // 3. Return the string-concatenation of:
            return buildString {
                // - the code unit 0x005C (REVERSE SOLIDUS)
                // - "u"
                append("\\u")

                // - the String representation of n, formatted as a four-digit lowercase hexadecimal number, padded to
                //   the left with zeroes if necessary
                append("%04x".format(value))
            }
        }

        @ECMAImpl("2.5.5.2.4")
        private fun serializeJSONObject(state: SerializeRecord, value: JSObject): String {
            // 1. If state.[[Stack]] contains value, throw a TypeError exception because the structure is cyclical.
            if (value in state.stack)
                Errors.JSON.StringifyCircular.throwTypeError()

            // 2. Append value to state.[[Stack]].
            state.stack.add(value)

            // 3. Let stepback be state.[[Indent]].
            val stepback = state.indent

            // 4. Set state.[[Indent]] to the string-concatenation of state.[[Indent]] and state.[[Gap]].
            state.indent += state.gap

            // 5. If state.[[PropertyList]] is not undefined, then
            val properties = if (state.propertyList != null) {
                // a. Let K be state.[[PropertyList]].
                state.propertyList
            }
            // 6. Else,
            else {
                // a. Let K be ? EnumerableOwnPropertyNames(value, key).
                AOs.enumerableOwnPropertyNames(value, PropertyKind.Key).map { it.toJSString().string }
            }

            // 7. Let partial be a new empty List.
            val partial = mutableListOf<String>()

            // 8. 8. For each element P of K, do
            for (property in properties) {
                // a. Let strP be ? SerializeJSONProperty(state, P, value).
                val stringProperty = serializeJSONProperty(state, property.key(), value)

                // b. If strP is not undefined, then
                if (stringProperty != null) {
                    // i. Let member be QuoteJSONString(P).
                    // ii. Set member to the string-concatenation of member and ":".
                    var member = quoteJSONString(property) + ":"

                    // iii. If state.[[Gap]] is not the empty String, then
                    if (state.gap.isNotEmpty()) {
                        // 1. Set member to the string-concatenation of member and the code unit 0x0020 (SPACE).
                        member += " "
                    }

                    // iv. Set member to the string-concatenation of member and strP.
                    member += stringProperty

                    // v. Append member to partial.
                    partial.add(member)
                }
            }

            // 9. If partial is empty, then
            val final = if (partial.isEmpty()) {
                // a. Let final be "{}".
                "{}"
            }
            // 10. Else,
            else {
                // a. If state.[[Gap]] is the empty String, then
                if (state.gap.isEmpty()) {
                    // i. Let properties be the String value formed by concatenating all the element Strings of partial
                    //    with each adjacent pair of Strings separated with the code unit 0x002C (COMMA). A comma is not
                    //    inserted either before the first String or after the last String.
                    val properties = partial.joinToString(",")

                    // ii. Let final be the string-concatenation of "{", properties, and "}".
                    "{$properties}"
                }
                // b. Else,
                else {
                    // i. Let separator be the string-concatenation of the code unit 0x002C (COMMA), the code unit
                    //    0x000A (LINE FEED), and state.[[Indent]].
                    val separator = ",\n${state.indent}"

                    // ii. Let properties be the String value formed by concatenating all the element Strings of partial
                    //     with each adjacent pair of Strings separated with separator. The separator String is not
                    //     inserted either before the first String or after the last String.
                    val properties = partial.joinToString(separator)

                    // iii. Let final be the string-concatenation of "{", the code unit 0x000A (LINE FEED),
                    //      state.[[Indent]], properties, the code unit 0x000A (LINE FEED), stepback, and "}".
                    "{\n${state.indent}$properties\n$stepback}"
                }
            }

            // 11. Remove the last element of state.[[Stack]].
            state.stack.removeLast()

            // 12. Set state.[[Indent]] to stepback.
            state.indent = stepback

            // 13. Return final.
            return final
        }

        @ECMAImpl("25.5.2.5")
        private fun serializeJSONArray(state: SerializeRecord, value: JSObject): String {
            // 1. If state.[[Stack]] contains value, throw a TypeError exception because the structure is cyclical.
            if (value in state.stack)
                Errors.JSON.StringifyCircular.throwTypeError()

            // 2. Append value to state.[[Stack]].
            state.stack.add(value)

            // 3. Let stepback be state.[[Indent]].
            val stepback = state.indent

            // 4. Set state.[[Indent]] to the string-concatenation of state.[[Indent]] and state.[[Gap]].
            state.indent += state.gap

            // 5. Let partial be a new empty List.
            val partial = mutableListOf<String>()

            // 6. Let len be ? LengthOfArrayLike(value).
            val len = AOs.lengthOfArrayLike(value)

            // 7. Let index be 0.
            // 8. Repeat, while index < len,
            for (index in 0 until len) {
                // a. Let strP be ? SerializeJSONProperty(state, ! ToString(𝔽(index)), value).
                val strP = serializeJSONProperty(state, index.key(), value)

                // b. If strP is undefined, then
                //    i. Append "null" to partial.
                // c. Else,
                //    i. Append strP to partial.
                partial.add(strP ?: "null")

                // d. Set index to index + 1.
            }

            // 9. If partial is empty, then
            val final = if (partial.isEmpty()) {
                // a. Let final be "[]".
                "[]"
            }
            // 10. Else,
            else {
                // a. If state.[[Gap]] is the empty String, then
                if (state.gap.isEmpty()) {
                    // i. Let properties be the String value formed by concatenating all the element Strings of
                    //    partial with each adjacent pair of Strings separated with the code unit 0x002C (COMMA). A
                    //    comma is not inserted either before the first String or after the last String.
                    val properties = partial.joinToString(",")

                    // ii. Let final be the string-concatenation of "[", properties, and "]".
                    "[$properties]"
                }
                // b. Else,
                else {
                    // i. Let separator be the string-concatenation of the code unit 0x002C (COMMA), the code unit
                    //    0x000A (LINE FEED), and state.[[Indent]].
                    val separator = ",\n${state.indent}"

                    // ii. Let properties be the String value formed by concatenating all the element Strings of partial
                    //     with each adjacent pair of Strings separated with separator. The separator String is not
                    //     inserted either before the first String or after the last String.
                    val properties = partial.joinToString(separator)

                    // iii. Let final be the string-concatenation of "[", the code unit 0x000A (LINE FEED),
                    // state.[[Indent]], properties, the code unit 0x000A (LINE FEED), stepback, and "]".
                    "[\n${state.indent}$properties\n$stepback]"
                }
            }

            // 11. Remove the last element of state.[[Stack]].
            state.stack.removeLast()

            // 12. Set state.[[Indent]] to stepback.
            state.indent = stepback

            // 13. Return final.
            return final
        }

        @ECMAImpl("25.5.3")
        @JvmStatic
        fun getSymbolToStringTag(arguments: JSArguments): JSValue {
            return "JSON".toValue()
        }
    }
}
