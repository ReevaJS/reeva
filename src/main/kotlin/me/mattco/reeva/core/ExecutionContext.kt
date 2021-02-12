package me.mattco.reeva.core

import me.mattco.reeva.runtime.functions.JSFunction

// TODO: Make this class more useful or remove it
class ExecutionContext(val function: JSFunction) {
    val realm: Realm
        get() = function.realm
}
