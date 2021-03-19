package me.mattco.reeva.pipeline

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.parser.TokenLocation
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.toJSString

sealed class PipelineError {
    abstract override fun toString(): String

    class Parse(val reason: String, val start: TokenLocation, val end: TokenLocation) : PipelineError() {
        override fun toString(): String {
            return "\u001b[31mParse error ($start-$end): $reason\u001B[0m"
        }
    }

    class Runtime(val realm: Realm, val cause: JSValue) : PipelineError() {
        override fun toString(): String {
            return Reeva.activeAgent.withRealm(realm) {
                "\u001b[31m${cause.toJSString()}\u001B[0m"
            }
        }
    }

    class Internal(val cause: Throwable) : PipelineError() {
        override fun toString(): String {
            return "\u001b[31mReeva error: ${cause.message}\u001B[0m"
        }
    }
}
