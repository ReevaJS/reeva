package me.mattco.reeva.core

import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.runtime.functions.JSFunction

class ExecutionContext(
    @JvmField
    val realm: Realm,
    @JvmField
    val function: JSFunction?,
) {
    @JvmField
    var variableEnv: EnvRecord? = null
    @JvmField
    var lexicalEnv: EnvRecord? = null
}
