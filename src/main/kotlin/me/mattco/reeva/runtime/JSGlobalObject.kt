package me.mattco.reeva.runtime

import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSNumber
import me.mattco.reeva.runtime.values.primitives.JSUndefined

open class JSGlobalObject protected constructor(
    realm: Realm,
    proto: JSObject = realm.objectProto
) : JSObject(realm, proto) {
    override fun init() {
        set("Object", realm.objectCtor)
        set("Function", realm.functionCtor)
        set("Array", realm.arrayCtor)
        set("String", realm.stringCtor)
        set("Number", realm.numberCtor)
        set("Boolean", realm.booleanCtor)
        set("Symbol", realm.symbolCtor)
        set("Error", realm.errorCtor)
        set("EvalError", realm.evalErrorCtor)
        set("TypeError", realm.typeErrorCtor)
        set("RangeError", realm.rangeErrorCtor)
        set("ReferenceError", realm.referenceErrorCtor)
        set("SyntaxError", realm.syntaxErrorCtor)
        set("URIError", realm.uriErrorCtor)

        set("JSON", realm.jsonObj)
        set("console", realm.consoleObj)

        defineOwnProperty("Infinity", JSNumber(Double.POSITIVE_INFINITY), 0)
        defineOwnProperty("NaN", JSNumber(Double.NaN), 0)
        defineOwnProperty("globalThis", this, Descriptor.WRITABLE or Descriptor.CONFIGURABLE)
        defineOwnProperty("undefined", JSUndefined, 0)
    }

    companion object {
        fun create(realm: Realm) = JSGlobalObject(realm).also { it.init() }
    }
}
