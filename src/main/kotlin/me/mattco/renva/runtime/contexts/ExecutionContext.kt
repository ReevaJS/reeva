package me.mattco.renva.runtime.contexts

import me.mattco.renva.ast.ScriptNode
import me.mattco.renva.runtime.Agent
import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.environment.EnvRecord
import me.mattco.renva.runtime.values.nonprimitives.functions.JSFunction

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
