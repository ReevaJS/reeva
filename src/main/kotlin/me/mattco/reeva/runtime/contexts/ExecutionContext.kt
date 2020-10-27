package me.mattco.reeva.runtime.contexts

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.environment.EnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.functions.JSFunction

class ExecutionContext(
    @JvmField
    val agent: Agent,
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
    var error: JSValue? = null
}
