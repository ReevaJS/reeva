package me.mattco.reeva.pipeline

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.Realm
import me.mattco.reeva.parser.TokenLocation
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.toJSString

sealed class PipelineError {
    abstract fun print()

    class Parse(val reason: String, val start: TokenLocation, val end: TokenLocation) : PipelineError() {
        override fun print() {
            println("\u001b[31mParse error ($start-$end): $reason\u001B[0m\n")
        }
    }

    class Runtime(val realm: Realm, val cause: JSValue) : PipelineError() {
        override fun print() {
            Reeva.activeAgent.withRealm(realm) {
                println("\u001b[31m${cause.toJSString()}\u001B[0m")
            }
        }
    }

    class Internal(val cause: Throwable) : PipelineError() {
        override fun print() {
            println("\u001b[31mInternal Reeva error\u001B[0m")
            cause.printStackTrace()
        }
    }
}
