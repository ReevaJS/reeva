package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*

class JSRegExpProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val attrs = attrs { +conf; -enum }
        defineBuiltinGetter("dotAll", ReevaBuiltin.RegExpProtoGetDotAll, attributes = attrs)
        defineBuiltinGetter("flags", ReevaBuiltin.RegExpProtoGetFlags, attributes = attrs)
        defineBuiltinGetter("global", ReevaBuiltin.RegExpProtoGetGlobal, attributes = attrs)
        defineBuiltinGetter("ignoreCase", ReevaBuiltin.RegExpProtoGetIgnoreCase, attributes = attrs)
        defineBuiltinGetter("multiline", ReevaBuiltin.RegExpProtoGetMultiline, attributes = attrs)
        defineBuiltinGetter("source", ReevaBuiltin.RegExpProtoGetSource, attributes = attrs)
        defineBuiltinGetter("sticky", ReevaBuiltin.RegExpProtoGetSticky, attributes = attrs)
        defineBuiltinGetter("unicode", ReevaBuiltin.RegExpProtoGetUnicode, attributes = attrs)
        defineBuiltin(Realm.WellKnownSymbols.match, 1, ReevaBuiltin.RegExpProtoMatch)
        defineBuiltin(Realm.WellKnownSymbols.matchAll, 1, ReevaBuiltin.RegExpProtoMatchAll)
        defineBuiltin(Realm.WellKnownSymbols.replace, 2, ReevaBuiltin.RegExpProtoReplace)
        defineBuiltin(Realm.WellKnownSymbols.search, 1, ReevaBuiltin.RegExpProtoSearch)
        defineBuiltin(Realm.WellKnownSymbols.split, 2, ReevaBuiltin.RegExpProtoSplit)
        defineBuiltin("exec", 1, ReevaBuiltin.RegExpProtoExec)
        defineBuiltin("test", 1, ReevaBuiltin.RegExpProtoTest)
        defineBuiltin("toString", 0, ReevaBuiltin.RegExpProtoToString)
    }

    companion object {
        fun create(realm: Realm) = JSRegExpProto(realm).initialize()

        @ECMAImpl("22.2.5.2")
        @JvmStatic
        fun exec(realm: Realm, arguments: JSArguments): JSValue {
            // Handled by the regExpBuiltinExec function
//        Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher)
            return Operations.regExpBuiltinExec(
                realm,
                arguments.thisValue,
                Operations.toString(realm, arguments.argument(0))
            )
        }

        @ECMAImpl("22.2.5.3")
        @JvmStatic
        fun getDotAll(realm: Realm, arguments: JSArguments): JSValue {
            return getFlagHelper(realm, arguments.thisValue, "dotAll", JSRegExpObject.Flag.DotAll)
        }

        @ECMAImpl("22.2.5.4")
        @JvmStatic
        fun getFlags(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher))
                Errors.IncompatibleMethodCall("RegExp.prototype.flags").throwTypeError(realm)
            var result = ""
            if (Operations.toBoolean(thisValue.get("global")))
                result += "g"
            if (Operations.toBoolean(thisValue.get("ignoreCase")))
                result += "i"
            if (Operations.toBoolean(thisValue.get("multiline")))
                result += "m"
            if (Operations.toBoolean(thisValue.get("dotAll")))
                result += "s"
            if (Operations.toBoolean(thisValue.get("unicode")))
                result += "u"
            if (Operations.toBoolean(thisValue.get("sticky")))
                result += "s"
            return result.toValue()
        }

        @ECMAImpl("22.2.5.5")
        @JvmStatic
        fun getGlobal(realm: Realm, arguments: JSArguments): JSValue {
            return getFlagHelper(realm, arguments.thisValue, "global", JSRegExpObject.Flag.Global)
        }

        @ECMAImpl("22.2.5.6")
        @JvmStatic
        fun getIgnoreCase(realm: Realm, arguments: JSArguments): JSValue {
            return getFlagHelper(realm, arguments.thisValue, "ignoreCase", JSRegExpObject.Flag.IgnoreCase)
        }

        @ECMAImpl("22.2.5.7")
        @JvmStatic
        fun symbolMatch(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@match]").throwTypeError(realm)

            val string = Operations.toString(realm, arguments.argument(0))
            val global = Operations.toBoolean(thisValue.get("global"))
            if (!global)
                return Operations.regExpExec(realm, thisValue, string, "[@@match]")

            val fullUnicode = Operations.toBoolean(thisValue.get("unicode"))
            Operations.set(realm, thisValue, "lastIndex".key(), 0.toValue(), true)
            val arr = Operations.arrayCreate(realm, 0)
            var n = 0
            while (true) {
                val result = Operations.regExpExec(realm, thisValue, string, "[@@match]")
                if (result == JSNull)
                    return if (n == 0) JSNull else arr
                val matchStr = Operations.toString(realm, (result as JSObject).get(0))
                Operations.createDataPropertyOrThrow(realm, arr, n.key(), matchStr)
                if (matchStr.string == "") {
                    val thisIndex = Operations.toLength(realm, thisValue.get("lastIndex")).asInt
                    // TODO: AdvanceStringIndex
                    val nextIndex = thisIndex + 1
                    Operations.set(realm, thisValue, "lastIndex".key(), nextIndex.toValue(), true)
                }
                n++
            }
        }

        @ECMAImpl("22.2.5.8")
        @JvmStatic
        fun symbolMatchAll(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@matchAll]").throwTypeError(realm)

            val string = Operations.toString(realm, arguments.argument(0))
            val ctor = Operations.speciesConstructor(realm, thisValue, realm.regExpCtor)
            val flags = Operations.toString(realm, thisValue.get("flags"))
            val lastIndex = Operations.toLength(realm, thisValue.get("lastIndex"))

            val matcher = Operations.construct(ctor, listOf(thisValue, flags))
            expect(matcher is JSObject)
            Operations.set(realm, matcher, "lastIndex".key(), lastIndex, true)
            val global = 'g' in flags.string
            val fullUnicode = 'u' in flags.string
            return JSRegExpStringIterator.create(realm, thisValue as JSRegExpObject, string, global, fullUnicode)
        }

        @ECMAImpl("22.2.5.9")
        @JvmStatic
        fun getMultiline(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            return getFlagHelper(realm, thisValue, "multiline", JSRegExpObject.Flag.Multiline)
        }

        @ECMAImpl("22.2.5.10")
        @JvmStatic
        fun symbolReplace(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@replace]").throwTypeError(realm)
            TODO()
        }

        @ECMAImpl("22.2.5.11")
        @JvmStatic
        fun symbolSearch(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@search]").throwTypeError(realm)

            val string = Operations.toString(realm, arguments.argument(0))
            val previousLastIndex = thisValue.get("lastIndex")
            if (previousLastIndex.asInt != 0)
                Operations.set(realm, thisValue, "lastIndex".key(), 0.toValue(), true)

            val result = Operations.regExpExec(realm, thisValue, string, "[@@search]")
            val currentLastIndex = thisValue.get("lastIndex")
            if (!currentLastIndex.sameValue(previousLastIndex))
                Operations.set(realm, thisValue, "lastIndex".key(), previousLastIndex, true)
            if (result == JSNull)
                return (-1).toValue()

            expect(result is JSObject)
            return result.get("index")
        }

        @ECMAImpl("22.2.5.12")
        @JvmStatic
        fun getSource(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype.source").throwTypeError(realm)
            if (!Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher)) {
                if (thisValue.sameValue(thisValue))
                    return "(?:)".toValue()
                Errors.IncompatibleMethodCall("RegExp.prototype.source").throwTypeError(realm)
            }
            // TODO: EscapeRegExpPattern (21.2.5.12)
            return thisValue.getSlotAs<String>(SlotName.OriginalSource).toValue()
        }

        @ECMAImpl("22.2.5.13")
        @JvmStatic
        fun symbolSplit(realm: Realm, arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@split]").throwTypeError(realm)
            TODO()
        }

        @ECMAImpl("22.2.5.14")
        @JvmStatic
        fun getSticky(realm: Realm, arguments: JSArguments): JSValue {
            return getFlagHelper(realm, arguments.thisValue, "sticky", JSRegExpObject.Flag.Sticky)
        }

        @ECMAImpl("22.2.5.15")
        @JvmStatic
        fun test(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher))
                Errors.IncompatibleMethodCall("RegExp.prototype.test").throwTypeError(realm)
            val string = Operations.toString(realm, arguments.argument(0))
            val match = Operations.regExpExec(realm, thisValue, string, ".test")
            return (match != JSNull).toValue()
        }

        @ECMAImpl("22.2.5.16")
        @JvmStatic
        fun toString(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher))
                Errors.IncompatibleMethodCall("RegExp.prototype.toString").throwTypeError(realm)
            val pattern = Operations.toString(realm, thisValue.get("source"))
            val flags = Operations.toString(realm, thisValue.get("flags"))
            return buildString {
                append('/')
                append(pattern.string)
                append('/')
                append(flags.string)
            }.toValue()
        }

        @ECMAImpl("22.2.5.17")
        @JvmStatic
        fun getUnicode(realm: Realm, arguments: JSArguments): JSValue {
            return getFlagHelper(realm, arguments.thisValue, "unicode", JSRegExpObject.Flag.Unicode)
        }

        private fun getFlagHelper(
            realm: Realm,
            thisValue: JSValue,
            methodName: String,
            flag: JSRegExpObject.Flag,
        ): JSValue {
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype.$methodName").throwTypeError(realm)
            val flags = thisValue.getSlotAs<String?>(SlotName.OriginalFlags)
            if (flags == null) {
                if (thisValue.sameValue(realm.regExpProto))
                    return JSUndefined
                Errors.IncompatibleMethodCall("RegExp.prototype.$methodName").throwTypeError(realm)
            }
            return (flag.char in flags).toValue()
        }
    }
}
