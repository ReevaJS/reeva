package me.mattco.reeva.core

import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSFunction

class ExecutionContext(
    @JvmField
    val realm: Realm,
    @JvmField
    val function: JSFunction?,
) {
    @JvmField
    var lexicalEnv: EnvRecord? = null
    @JvmField
    var variableEnv: EnvRecord? = null
    @JvmField
    var privateEnv: EnvRecord? = null
}
