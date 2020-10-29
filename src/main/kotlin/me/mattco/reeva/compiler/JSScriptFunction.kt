package me.mattco.reeva.compiler

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction

abstract class JSScriptFunction @JvmOverloads constructor(
    realm: Realm,
    thisMode: ThisMode,
    isStrict: Boolean,
    envRecord: EnvRecord? = null,
) : JSFunction(realm, thisMode, envRecord, isStrict = isStrict) {
    abstract fun getSourceText(): String

    abstract fun getParameterNames(): Array<String>

    abstract fun getParamHasDefaultValue(index: Int): Boolean

    abstract fun getDefaultParameterValue(index: Int): JSValue
}
