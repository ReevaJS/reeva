package me.mattco.reeva.compiler

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSFunction
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined

abstract class JSScriptFunction(
    realm: Realm,
    thisMode: ThisMode,
    val strict: Boolean,
) : JSFunction(realm, thisMode) {
    abstract fun getSourceText(): String

    abstract fun isClassConstructor(): Boolean

    abstract fun getHomeObject(): JSValue

    abstract fun getParameterNames(): Array<String>

    abstract fun getParamHasDefaultValue(index: Int): Boolean

    abstract fun getDefaultParameterValue(index: Int): JSValue
}