package me.mattco.reeva.runtime.functions

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.ReturnException
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

/**
 * A function declared in a JS script context. Created by
 * the interpreter or compiler.
 */
abstract class JSRuntimeFunction @JvmOverloads constructor(
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
