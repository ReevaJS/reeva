package com.reevajs.reeva.runtime

import com.reevajs.reeva.ast.statements.StatementList
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.other.URIParser
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.isRadixDigit
import com.reevajs.reeva.utils.toValue

open class JSGlobalObject protected constructor(
    realm: Realm,
    proto: JSObject = realm.objectProto
) : JSObject(realm, proto) {
    override fun init() {
        super.init()

        val realm = Agent.activeAgent.getActiveRealm()
        val attrs = Descriptor.CONFIGURABLE or Descriptor.WRITABLE
        defineOwnProperty("Array", realm.arrayCtor, attrs)
        defineOwnProperty("ArrayBuffer", realm.arrayBufferCtor, attrs)
        defineOwnProperty("BigInt", realm.bigIntCtor, attrs)
        defineOwnProperty("Boolean", realm.booleanCtor, attrs)
        defineOwnProperty("DataView", realm.dataViewCtor, attrs)
        defineOwnProperty("Date", realm.dateCtor, attrs)
        defineOwnProperty("Error", realm.errorCtor, attrs)
        defineOwnProperty("EvalError", realm.evalErrorCtor, attrs)
        defineOwnProperty("Function", realm.functionCtor, attrs)
        defineOwnProperty("InternalError", realm.internalErrorCtor, attrs)
        defineOwnProperty("Map", realm.mapCtor, attrs)
        defineOwnProperty("Number", realm.numberCtor, attrs)
        defineOwnProperty("Object", realm.objectCtor, attrs)
        defineOwnProperty("Packages", realm.packageObj, attrs)
        defineOwnProperty("Promise", realm.promiseCtor, attrs)
        defineOwnProperty("Proxy", realm.proxyCtor, attrs)
        defineOwnProperty("RangeError", realm.rangeErrorCtor, attrs)
        defineOwnProperty("ReferenceError", realm.referenceErrorCtor, attrs)
        defineOwnProperty("Reflect", realm.reflectObj, attrs)
        defineOwnProperty("RegExp", realm.regExpCtor, attrs)
        defineOwnProperty("Set", realm.setCtor, attrs)
        defineOwnProperty("String", realm.stringCtor, attrs)
        defineOwnProperty("Symbol", realm.symbolCtor, attrs)
        defineOwnProperty("SyntaxError", realm.syntaxErrorCtor, attrs)
        defineOwnProperty("TypeError", realm.typeErrorCtor, attrs)
        defineOwnProperty("URIError", realm.uriErrorCtor, attrs)

        defineOwnProperty("Math", realm.mathObj, attrs)
        defineOwnProperty("JSON", realm.jsonObj, attrs)
        defineOwnProperty("console", realm.consoleObj, attrs)

        defineOwnProperty("Int8Array", realm.int8ArrayCtor, attrs)
        defineOwnProperty("Uint8Array", realm.uint8ArrayCtor, attrs)
        defineOwnProperty("Uint8ClampedArray", realm.uint8CArrayCtor, attrs)
        defineOwnProperty("Int16Array", realm.int16ArrayCtor, attrs)
        defineOwnProperty("Uint16Array", realm.uint16ArrayCtor, attrs)
        defineOwnProperty("Int32Array", realm.int32ArrayCtor, attrs)
        defineOwnProperty("Uint32Array", realm.uint32ArrayCtor, attrs)
        defineOwnProperty("Float32Array", realm.float32ArrayCtor, attrs)
        defineOwnProperty("Float64Array", realm.float64ArrayCtor, attrs)
        defineOwnProperty("BigInt64Array", realm.bigInt64ArrayCtor, attrs)
        defineOwnProperty("BigUint64Array", realm.bigUint64ArrayCtor, attrs)

        defineOwnProperty("Infinity", JSNumber.POSITIVE_INFINITY, 0)
        defineOwnProperty("NaN", JSNumber.NaN, 0)
        defineOwnProperty("globalThis", this, Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        defineOwnProperty("undefined", JSUndefined, 0)

        defineBuiltin("isNaN", 1, ReevaBuiltin.GlobalIsNaN)
        defineBuiltin("eval", 1, ReevaBuiltin.GlobalEval)
        defineBuiltin("parseInt", 1, ReevaBuiltin.GlobalParseInt)
        defineBuiltin("id", 1, ReevaBuiltin.GlobalId)
        defineBuiltin("jvm", 1, ReevaBuiltin.GlobalJvm)
        defineBuiltin("inspect", 1, ReevaBuiltin.GlobalInspect)

        // TODO: The tests involving these functions have some pretty intense loop which increase
        //       the test suite time significantly (~40%). These tests fail-fast when the functions
        //       don't exist. These can be uncommented when Reeva's performance improves.
        // defineBuiltin("decodeURI", 1, ReevaBuiltin.GlobalDecodeURI)
        // defineBuiltin("decodeURIComponent", 1, ReevaBuiltin.GlobalDecodeURIComponent)
        // defineBuiltin("encodeURI", 1, ReevaBuiltin.GlobalEncodeURI)
        // defineBuiltin("encodeURIComponent", 1, ReevaBuiltin.GlobalEncodeURIComponent)

        // Debug method
        // TODO
        // defineNativeFunction("isStrict".key(), 0, 0) { Operations.isStrict().toValue() }
    }

    companion object {
        private val reservedURISet = setOf(';', '/', '?', ':', '@', '&', '=', '+', '$', ',', '#')
        private val uriUnescaped = mutableSetOf<Char>().also {
            it.addAll('a'..'z')
            it.addAll('A'..'Z')
            it.addAll('0'..'9')
            it.add('-')
            it.add('_')
            it.add('.')
            it.add('!')
            it.add('~')
            it.add('*')
            it.add('\'')
            it.add('(')
            it.add(')')
        }.toSet()

        private val uriUnescapedExtended = uriUnescaped + reservedURISet + listOf('#')

        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSGlobalObject(realm).initialize()

        @JvmStatic
        @ECMAImpl("19.2.3")
        fun isNaN(arguments: JSArguments): JSValue {
            val num = arguments.argument(0).toNumber()
            return (num == JSNumber.NaN).toValue()
        }

        @JvmStatic
        @ECMAImpl("19.2.6.2")
        fun decodeURI(arguments: JSArguments): JSValue {
            return URIParser.decode(arguments.argument(0).toJSString().string, reservedURISet).toValue()
        }

        @JvmStatic
        @ECMAImpl("19.2.6.3")
        fun decodeURIComponent(arguments: JSArguments): JSValue {
            return URIParser.decode(arguments.argument(0).toJSString().string, emptySet()).toValue()
        }

        @JvmStatic
        @ECMAImpl("19.2.6.4")
        fun encodeURI(arguments: JSArguments): JSValue {
            return URIParser.encode(arguments.argument(0).toJSString().string, uriUnescapedExtended).toValue()
        }

        @JvmStatic
        @ECMAImpl("19.2.6.5")
        fun encodeURIComponent(arguments: JSArguments): JSValue {
            return URIParser.encode(arguments.argument(0).toJSString().string, uriUnescaped).toValue()
        }

        @ECMAImpl("18.2.1.1")
        fun performEval(
            argument: JSValue,
            callerRealm: Realm,
            strictCaller: Boolean,
            direct: Boolean,
        ): JSValue {
            // TODO
            Errors.Custom("eval is not yet implemented in Reeva").throwInternalError()
        }

        @JvmStatic
        fun id(arguments: JSArguments): JSValue {
            val o = arguments.argument(0)
            return "${o::class.java.simpleName}@${Integer.toHexString(o.hashCode())}".toValue()
        }

        @JvmStatic
        fun eval(arguments: JSArguments): JSValue {
            return performEval(
                arguments.argument(0),
                Agent.activeAgent.getActiveRealm(),
                strictCaller = false,
                direct = false,
            )
        }

        @JvmStatic
        fun parseInt(arguments: JSArguments): JSValue {
            var inputString = Operations.trimString(
                Operations.toString(arguments.argument(0)),
                Operations.TrimType.Start,
            )
            val sign = when {
                inputString.startsWith("-") -> {
                    inputString = inputString.substring(1)
                    -1
                }
                inputString.startsWith("+") -> {
                    inputString = inputString.substring(1)
                    1
                }
                else -> 1
            }

            var stripPrefix = true
            var radix = Operations.toInt32(arguments.argument(1)).asInt.let {
                if (it != 0) {
                    if (it !in 2..36)
                        return JSNumber.NaN
                    if (it != 16)
                        stripPrefix = false
                    it
                } else 10
            }

            if (stripPrefix && inputString.lowercase().startsWith("0x")) {
                inputString = inputString.substring(2)
                radix = 16
            }

            val end = inputString.indexOfFirst { !it.isRadixDigit(radix) }
                .let { if (it == -1) inputString.length else it }
            val content = inputString.substring(0, end)
            if (content.isEmpty())
                return JSNumber.NaN
            val numericValue = content.toLongOrNull(radix) ?: return JSNumber.NaN
            return JSNumber(numericValue * sign)
        }

        @JvmStatic
        fun jvm(arguments: JSArguments): JSValue {
            // TODO
            return JSUndefined

//        if (arguments.isEmpty())
//            Errors.JVMCompat.JVMFuncNoArgs.throwTypeError()
//
//        if (arguments.any { it !is JSClassObject })
//            Errors.JVMCompat.JVMFuncBadArgType.throwTypeError()
//
//        val classObjects = arguments.map { (it as JSClassObject).clazz }
//        if (classObjects.count { it.isInterface } > 1)
//            Errors.JVMCompat.JVMFuncMultipleBaseClasses.throwTypeError()
//
//        classObjects.firstOrNull {
//            Modifier.isFinal(it.modifiers)
//        }?.let {
//            Errors.JVMCompat.JVMFuncFinalClass(it.name).throwTypeError()
//        }
//
//        val baseClass = classObjects.firstOrNull { !it.isInterface }
//        val interfaces = classObjects.filter { it.isInterface }
//
//        return JSClassObject.create(
//            realm,
//            ProxyClassCompiler().makeProxyClass(baseClass, interfaces)
//        )
        }

        @JvmStatic
        fun inspect(arguments: JSArguments): JSValue {
            val value = arguments.argument(0)
            println(inspect(value, simple = false).stringify())
            return JSUndefined
        }
    }
}
