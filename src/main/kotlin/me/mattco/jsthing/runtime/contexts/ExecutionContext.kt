package me.mattco.jsthing.runtime.contexts

import me.mattco.jsthing.runtime.Agent
import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction

class ExecutionContext(
    val agent: Agent,
    val realm: Realm,
    val function: JSFunction?,
    /*val scriptOrModule: ScriptOrModule*/
) {
    var lexicalEnv: EnvRecord? = null
    var variableEnv: EnvRecord? = null
}
