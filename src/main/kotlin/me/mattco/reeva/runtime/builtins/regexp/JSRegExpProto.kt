package me.mattco.reeva.runtime.builtins.regexp

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

class JSRegExpProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        val attrs = attrs { +conf -enum }
        defineNativeAccessor("dotAll", attrs, ::getDotAll, null)
        defineNativeAccessor("flags", attrs, ::getFlags, null)
        defineNativeAccessor("global", attrs, ::getGlobal, null)
        defineNativeAccessor("ignoreCase", attrs, ::getIgnoreCase, null)
        defineNativeAccessor("multiline", attrs, ::getMultiline, null)
        defineNativeAccessor("source", attrs, ::getSource, null)
        defineNativeAccessor("sticky", attrs, ::getSticky, null)
        defineNativeAccessor("unicode", attrs, ::getUnicode, null)
        defineNativeFunction(Realm.`@@match`.key(), 1, function = ::`@@match`)
        defineNativeFunction(Realm.`@@matchAll`.key(), 1, function = ::`@@matchAll`)
        defineNativeFunction(Realm.`@@replace`.key(), 2, function = ::`@@replace`)
        defineNativeFunction(Realm.`@@search`.key(), 1, function = ::`@@search`)
        defineNativeFunction(Realm.`@@split`.key(), 2, function = ::`@@split`)
        defineNativeFunction("exec", 1, ::exec)
        defineNativeFunction("test", 1, ::test)
        defineNativeFunction("toString", 0, ::toString)
    }

    fun getDotAll(realm: Realm, thisValue: JSValue): JSValue {
        return getFlagHelper(thisValue, "dotAll", JSRegExpObject.Flag.DotAll)
    }

    fun getFlags(realm: Realm, thisValue: JSValue): JSValue {
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

    fun getGlobal(realm: Realm, thisValue: JSValue): JSValue {
        return getFlagHelper(thisValue, "global", JSRegExpObject.Flag.Global)
    }

    fun getIgnoreCase(realm: Realm, thisValue: JSValue): JSValue {
        return getFlagHelper(thisValue, "ignoreCase", JSRegExpObject.Flag.IgnoreCase)
    }

    fun getMultiline(realm: Realm, thisValue: JSValue): JSValue {
        return getFlagHelper(thisValue, "multiline", JSRegExpObject.Flag.Multiline)
    }

    fun getSource(realm: Realm, thisValue: JSValue): JSValue {
        if (thisValue !is JSObject)
            Errors.IncompatibleMethodCall("RegExp.prototype.source").throwTypeError(realm)
        if (!Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher)) {
            if (thisValue.sameValue(this))
                return "(?:)".toValue()
            Errors.IncompatibleMethodCall("RegExp.prototype.source").throwTypeError(realm)
        }
        // TODO: EscapeRegExpPattern (21.2.5.12)
        return thisValue.getSlotAs<String>(SlotName.OriginalSource).toValue()
    }

    fun getSticky(realm: Realm, thisValue: JSValue): JSValue {
        return getFlagHelper(thisValue, "sticky", JSRegExpObject.Flag.Sticky)
    }

    fun getUnicode(realm: Realm, thisValue: JSValue): JSValue {
        return getFlagHelper(thisValue, "unicode", JSRegExpObject.Flag.Unicode)
    }

    fun `@@match`(realm: Realm, arguments: JSArguments): JSValue {
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

    fun `@@matchAll`(realm: Realm, arguments: JSArguments): JSValue {
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

    fun `@@replace`(realm: Realm, arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("RegExp.prototype[@@replace]").throwTypeError(realm)
        TODO()
    }

    fun `@@search`(realm: Realm, arguments: JSArguments): JSValue {
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

    fun `@@split`(realm: Realm, arguments: JSArguments): JSValue {
        if (arguments.thisValue !is JSObject)
            Errors.IncompatibleMethodCall("RegExp.prototype[@@split]").throwTypeError(realm)
        TODO()
    }

    fun exec(realm: Realm, arguments: JSArguments): JSValue {
        // Handled by the regExpBuiltinExec function
//        Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher)
        return Operations.regExpBuiltinExec(realm, arguments.thisValue, Operations.toString(realm, arguments.argument(0)))
    }

    fun test(realm: Realm, arguments: JSArguments): JSValue {
        val thisValue = arguments.thisValue
        if (!Operations.requireInternalSlot(thisValue, SlotName.RegExpMatcher))
            Errors.IncompatibleMethodCall("RegExp.prototype.test").throwTypeError(realm)
        val string = Operations.toString(realm, arguments.argument(0))
        val match = Operations.regExpExec(realm, thisValue, string, ".test")
        return (match != JSNull).toValue()
    }

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

    private fun getFlagHelper(thisValue: JSValue, methodName: String, flag: JSRegExpObject.Flag): JSValue {
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

    companion object {
        fun create(realm: Realm) = JSRegExpProto(realm).initialize()
    }
}
