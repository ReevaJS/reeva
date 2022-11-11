package com.reevajs.reeva.runtime

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.jvmcompat.JSPackageObject
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSBuiltinFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.other.URIParser
import com.reevajs.reeva.runtime.primitives.JSNumber
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*

open class JSGlobalObject protected constructor(
    realm: Realm,
    proto: JSObject = realm.objectProto
) : JSObject(realm, proto) {
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

        fun create(realm: Realm) = JSGlobalObject(realm).initialize()

        @JvmStatic
        @ECMAImpl("9.3.4")
        fun setDefaultGlobalBindings(realm: Realm): JSObject {
            // 1. Let global be realmRec.[[GlobalObject]].
            val global = realm.globalObject

            // 2. For each property of the Global Object specified in clause 19, do
            //    a. Let name be the String value of the property name.
            //    b. Let desc be the fully populated data Property Descriptor for the property, containing the specified attributes for the property. For properties listed in 19.2, 19.3, or 19.4 the value of the [[Value]] attribute is the corresponding intrinsic object from realmRec.
            //    c. Perform ? DefinePropertyOrThrow(global, name, desc).

            AOs.definePropertyOrThrow(
                global, "globalThis".key(), Descriptor(realm.globalEnv.globalThisValue, attrs { +conf; -enum; +writ }),
            )
            AOs.definePropertyOrThrow(
                global, "Infinity".key(), Descriptor(JSNumber.POSITIVE_INFINITY, attrs { -conf; -enum; -writ }),
            )
            AOs.definePropertyOrThrow(
                global, "NaN".key(), Descriptor(JSNumber.NaN, attrs { -conf; -enum; -writ })
            )
            AOs.definePropertyOrThrow(
                global, "undefined".key(), Descriptor(JSUndefined, attrs { -conf; -enum; -writ })
            )

            val attr = attrs { +conf; +writ }

            val builtinFunctions = listOf(
                JSBuiltinFunction.create("eval", 1, ::eval),
                JSBuiltinFunction.create("isFinite", 1, ::isFinite),
                JSBuiltinFunction.create("isNaN", 1, ::isNaN),
                // TODO
                // JSBuiltinFunction.create("parseFloat", 1, ::parseFloat),
                JSBuiltinFunction.create("parseInt", 1, ::parseInt),
                JSBuiltinFunction.create("id", 1, ::id),
                JSBuiltinFunction.create("jvm", 1, ::jvm),

                // TODO: The tests involving these functions have some pretty intense for loops which increase
                //       the test suite time significantly (~40%). These tests fail-fast when the functions
                //       don't exist. These can be uncommented when Reeva's performance improves.
                // defineBuiltin(realm, "decodeURI", 1, ReevaBuiltin.GlobalDecodeURI)
                // defineBuiltin(realm, "decodeURIComponent", 1, ReevaBuiltin.GlobalDecodeURIComponent)
                // defineBuiltin(realm, "encodeURI", 1, ReevaBuiltin.GlobalEncodeURI)
                // defineBuiltin(realm, "encodeURIComponent", 1, ReevaBuiltin.GlobalEncodeURIComponent)
            )

            for (function in builtinFunctions)
                AOs.definePropertyOrThrow(global, function.debugName.key(), Descriptor(function, attr))

            val bindings = mapOf(
                "Array" to realm.arrayCtor,
                "ArrayBuffer" to realm.arrayBufferCtor,
                "BigInt" to realm.bigIntCtor,
                "BigInt64Array" to realm.bigInt64ArrayCtor,
                "BigUint64Array" to realm.bigUint64ArrayCtor,
                "Boolean" to realm.booleanCtor,
                "DataView" to realm.dataViewCtor,
                "Date" to realm.dateCtor,
                "Error" to realm.errorCtor,
                "EvalError" to realm.evalErrorCtor,
                "Float32Array" to realm.float32ArrayCtor,
                "Float64Array" to realm.float64ArrayCtor,
                "Function" to realm.functionCtor,
                "Int8Array" to realm.int8ArrayCtor,
                "Int16Array" to realm.int16ArrayCtor,
                "Int32Array" to realm.int32ArrayCtor,
                "InternalError" to realm.internalErrorCtor, // non-standard
                "Map" to realm.mapCtor,
                "Number" to realm.numberCtor,
                "Object" to realm.objectCtor,
                "Packages" to realm.packageObj, // non-standard
                "Promise" to realm.promiseCtor,
                "Proxy" to realm.proxyCtor,
                "RangeError" to realm.rangeErrorCtor,
                "ReferenceError" to realm.referenceErrorCtor,
                "RegExp" to realm.regExpCtor,
                "Set" to realm.setCtor,
                // TODO
                // "SharedArrayBuffer" to realm.sharedArrayBuffer,
                "String" to realm.stringCtor,
                "Symbol" to realm.symbolCtor,
                "SyntaxError" to realm.syntaxErrorCtor,
                "TypeError" to realm.typeErrorCtor,
                "Uint8Array" to realm.uint8ArrayCtor,
                "Uint8ClampedArray" to realm.uint8ArrayCtor,
                "Uint16Array" to realm.uint16ArrayCtor,
                "Uint32Array" to realm.uint32ArrayCtor,
                "URIError" to realm.uriErrorCtor,
                // TODO
                // "WeakMap" to realm.weakMapCtor,
                // "WeakRef" to realm.weakRefCtor,
                // "WeakSet" to realm.weakSetCtor,

                // TODO
                // "Atomics" to realm.atomicObj,
                "JSON" to realm.jsonObj,
                "Math" to realm.mathObj,
                "Reflect" to realm.reflectObj,

                // Not from ECMA262
                "console" to realm.consoleObj,
            )

            for ((key, value) in bindings)
                AOs.definePropertyOrThrow(global, key.key(), Descriptor(value, attr))

            // 3. Return global.
            return global
        }

        @JvmStatic
        @ECMAImpl("19.2.2")
        fun isFinite(arguments: JSArguments): JSValue {
            return arguments.argument(0).toNumber().isFinite.toValue()
        }

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

        @JvmStatic
        fun id(arguments: JSArguments): JSValue {
            val o = arguments.argument(0)
            return "${o::class.java.simpleName}@${Integer.toHexString(o.hashCode())}".toValue()
        }

        @JvmStatic
        fun eval(arguments: JSArguments): JSValue {
            return AOs.performEval(
                arguments.argument(0),
                Agent.activeAgent.getActiveRealm(),
                strictCaller = false,
                direct = false,
            )
        }

        @JvmStatic
        fun parseInt(arguments: JSArguments): JSValue {
            var inputString = AOs.trimString(
                arguments.argument(0).toJSString(),
                AOs.TrimType.Start,
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
            var radix = arguments.argument(1).toInt32().asInt.let {
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
    }
}
