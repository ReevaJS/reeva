package me.mattco.reeva.compiler

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject

/**
 * A function declared in a JS script context. Created by
 * the interpreter.
 */
abstract class JSCompilerFunction @JvmOverloads constructor(
    realm: Realm,
    thisMode: ThisMode,
    envRecord: EnvRecord?,
    isStrict: Boolean,
    homeObject: JSValue,
    internal val sourceText: String,
    prototype: JSObject = realm.functionProto,
) : JSFunction(
    realm,
    thisMode,
    envRecord,
    homeObject,
    isStrict,
    prototype
)
