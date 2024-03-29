package com.reevajs.reeva.runtime.regexp

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*

class JSRegExpProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val attrs = attrs { +conf; -enum }
        defineBuiltinGetter("dotAll", ::getDotAll, attributes = attrs)
        defineBuiltinGetter("flags", ::getFlags, attributes = attrs)
        defineBuiltinGetter("global", ::getGlobal, attributes = attrs)
        defineBuiltinGetter("ignoreCase", ::getIgnoreCase, attributes = attrs)
        defineBuiltinGetter("multiline", ::getMultiline, attributes = attrs)
        defineBuiltinGetter("source", ::getSource, attributes = attrs)
        defineBuiltinGetter("sticky", ::getSticky, attributes = attrs)
        defineBuiltinGetter("unicode", ::getUnicode, attributes = attrs)
        defineBuiltin(Realm.WellKnownSymbols.match, 1, ::symbolMatch)
        defineBuiltin(Realm.WellKnownSymbols.matchAll, 1, ::symbolMatchAll)
        defineBuiltin(Realm.WellKnownSymbols.replace, 2, ::symbolReplace)
        defineBuiltin(Realm.WellKnownSymbols.search, 1, ::symbolSearch)
        defineBuiltin(Realm.WellKnownSymbols.split, 2, ::symbolSplit)
        defineBuiltin("exec", 1, ::exec)
        defineBuiltin("test", 1, ::test)
        defineBuiltin("toString", 0, ::toString)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSRegExpProto(realm).initialize()

        @ECMAImpl("22.2.5.2")
        @JvmStatic
        fun exec(arguments: JSArguments): JSValue {
            // Handled by the regExpBuiltinExec function
//        Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher)
            return AOs.regExpBuiltinExec(
                arguments.thisValue,
                arguments.argument(0).toJSString()
            )
        }

        @ECMAImpl("22.2.5.3")
        @JvmStatic
        fun getDotAll(arguments: JSArguments): JSValue {
            return getFlagHelper(arguments.thisValue, "dotAll", JSRegExpObject.Flag.DotAll)
        }

        @ECMAImpl("22.2.5.4")
        @JvmStatic
        fun getFlags(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.RegExpMatcher))
                Errors.IncompatibleMethodCall("RegExp.prototype.flags").throwTypeError()
            var result = ""
            if (thisValue.get("global").toBoolean())
                result += "g"
            if (thisValue.get("ignoreCase").toBoolean())
                result += "i"
            if (thisValue.get("multiline").toBoolean())
                result += "m"
            if (thisValue.get("dotAll").toBoolean())
                result += "s"
            if (thisValue.get("unicode").toBoolean())
                result += "u"
            if (thisValue.get("sticky").toBoolean())
                result += "s"
            return result.toValue()
        }

        @ECMAImpl("22.2.5.5")
        @JvmStatic
        fun getGlobal(arguments: JSArguments): JSValue {
            return getFlagHelper(arguments.thisValue, "global", JSRegExpObject.Flag.Global)
        }

        @ECMAImpl("22.2.5.6")
        @JvmStatic
        fun getIgnoreCase(arguments: JSArguments): JSValue {
            return getFlagHelper(arguments.thisValue, "ignoreCase", JSRegExpObject.Flag.IgnoreCase)
        }

        @ECMAImpl("22.2.5.7")
        @JvmStatic
        fun symbolMatch(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@match]").throwTypeError()

            val string = arguments.argument(0).toJSString()
            val global = thisValue.get("global").toBoolean()
            if (!global)
                return AOs.regExpExec(thisValue, string, "[@@match]")

            val fullUnicode = thisValue.get("unicode").toBoolean()
            AOs.set(thisValue, "lastIndex".key(), 0.toValue(), true)
            val arr = AOs.arrayCreate(0)
            var n = 0
            while (true) {
                val result = AOs.regExpExec(thisValue, string, "[@@match]")
                if (result == JSNull)
                    return if (n == 0) JSNull else arr
                val matchStr = (result as JSObject).get(0).toJSString()
                AOs.createDataPropertyOrThrow(arr, n.key(), matchStr)
                if (matchStr.string == "") {
                    val thisIndex = thisValue.get("lastIndex").toLength().asInt
                    // TODO: AdvanceStringIndex
                    val nextIndex = thisIndex + 1
                    AOs.set(thisValue, "lastIndex".key(), nextIndex.toValue(), true)
                }
                n++
            }
        }

        @ECMAImpl("22.2.5.8")
        @JvmStatic
        fun symbolMatchAll(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@matchAll]").throwTypeError()

            val string = arguments.argument(0).toJSString()
            val ctor = AOs.speciesConstructor(thisValue, Agent.activeAgent.getActiveRealm().regExpCtor)
            val flags = thisValue.get("flags").toJSString()
            val lastIndex = thisValue.get("lastIndex").toLength()

            val matcher = AOs.construct(ctor, listOf(thisValue, flags))
            expect(matcher is JSObject)
            AOs.set(matcher, "lastIndex".key(), lastIndex, true)
            val global = 'g' in flags.string
            val fullUnicode = 'u' in flags.string
            return JSRegExpStringIterator.create(thisValue as JSRegExpObject, string, global, fullUnicode)
        }

        @ECMAImpl("22.2.5.9")
        @JvmStatic
        fun getMultiline(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            return getFlagHelper(thisValue, "multiline", JSRegExpObject.Flag.Multiline)
        }

        @ECMAImpl("22.2.5.10")
        @JvmStatic
        fun symbolReplace(arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@replace]").throwTypeError()
            TODO()
        }

        @ECMAImpl("22.2.5.11")
        @JvmStatic
        fun symbolSearch(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@search]").throwTypeError()

            val string = arguments.argument(0).toJSString()
            val previousLastIndex = thisValue.get("lastIndex")
            if (previousLastIndex.asInt != 0)
                AOs.set(thisValue, "lastIndex".key(), 0.toValue(), true)

            val result = AOs.regExpExec(thisValue, string, "[@@search]")
            val currentLastIndex = thisValue.get("lastIndex")
            if (!currentLastIndex.sameValue(previousLastIndex))
                AOs.set(thisValue, "lastIndex".key(), previousLastIndex, true)
            if (result == JSNull)
                return (-1).toValue()

            expect(result is JSObject)
            return result.get("index")
        }

        @ECMAImpl("22.2.5.12")
        @JvmStatic
        fun getSource(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype.source").throwTypeError()
            if (!AOs.requireInternalSlot(thisValue, Slot.RegExpMatcher)) {
                if (thisValue.sameValue(thisValue))
                    return "(?:)".toValue()
                Errors.IncompatibleMethodCall("RegExp.prototype.source").throwTypeError()
            }
            // TODO: EscapeRegExpPattern (21.2.5.12)
            return thisValue[Slot.OriginalSource].toValue()
        }

        @ECMAImpl("22.2.5.13")
        @JvmStatic
        fun symbolSplit(arguments: JSArguments): JSValue {
            if (arguments.thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype[@@split]").throwTypeError()
            TODO()
        }

        @ECMAImpl("22.2.5.14")
        @JvmStatic
        fun getSticky(arguments: JSArguments): JSValue {
            return getFlagHelper(arguments.thisValue, "sticky", JSRegExpObject.Flag.Sticky)
        }

        @ECMAImpl("22.2.5.15")
        @JvmStatic
        fun test(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.RegExpMatcher))
                Errors.IncompatibleMethodCall("RegExp.prototype.test").throwTypeError()
            val string = arguments.argument(0).toJSString()
            val match = AOs.regExpExec( thisValue, string, ".test")
            return (match != JSNull).toValue()
        }

        @ECMAImpl("22.2.5.16")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.RegExpMatcher))
                Errors.IncompatibleMethodCall("RegExp.prototype.toString").throwTypeError()
            val pattern = thisValue.get("source").toJSString()
            val flags = thisValue.get("flags").toJSString()
            return buildString {
                append('/')
                append(pattern.string)
                append('/')
                append(flags.string)
            }.toValue()
        }

        @ECMAImpl("22.2.5.17")
        @JvmStatic
        fun getUnicode(arguments: JSArguments): JSValue {
            return getFlagHelper(arguments.thisValue, "unicode", JSRegExpObject.Flag.Unicode)
        }

        private fun getFlagHelper(
            thisValue: JSValue,
            methodName: String,
            flag: JSRegExpObject.Flag,
        ): JSValue {
            if (thisValue !is JSObject)
                Errors.IncompatibleMethodCall("RegExp.prototype.$methodName").throwTypeError()
            val flags = thisValue.getSlotOrNull(Slot.OriginalFlags)
            if (flags == null) {
                if (thisValue.sameValue(Agent.activeAgent.getActiveRealm().regExpProto))
                    return JSUndefined
                Errors.IncompatibleMethodCall("RegExp.prototype.$methodName").throwTypeError()
            }
            return (flag.char in flags).toValue()
        }
    }
}
