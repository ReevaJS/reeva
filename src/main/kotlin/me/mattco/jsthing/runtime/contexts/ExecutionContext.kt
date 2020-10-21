package me.mattco.jsthing.runtime.contexts

import me.mattco.jsthing.ast.ScriptNode
import me.mattco.jsthing.runtime.Agent
import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.environment.EnvRecord
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunction

class ExecutionContext(
    @JvmField
    val agent: Agent,
    @JvmField
    val realm: Realm,
    @JvmField
    val function: JSFunction?,
    @JvmField
    val scriptOrModule: ScriptNode
) {
    @JvmField
    var lexicalEnv: EnvRecord? = null
    @JvmField
    var variableEnv: EnvRecord? = null
}
