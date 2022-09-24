package com.reevajs.reeva.core

import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction

data class ExecutionContext @JvmOverloads constructor(
    val realm: Realm,
    val function: JSFunction? = null,
    var envRecord: EnvRecord? = null,
    val executable: Executable? = null,
    val invocationLocation: SourceLocation? = null,
) {
    val isNative = function is JSNativeFunction
}
