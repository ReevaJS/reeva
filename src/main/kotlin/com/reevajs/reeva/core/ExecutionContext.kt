package com.reevajs.reeva.core

import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.functions.JSNativeFunction

data class ExecutionContext(
    val enclosingFunction: JSFunction?,
    val realm: Realm,
    var envRecord: EnvRecord?,
    val executable: Executable?,
    val invocationLocation: SourceLocation?,
) {
    val isNative = enclosingFunction is JSNativeFunction
}