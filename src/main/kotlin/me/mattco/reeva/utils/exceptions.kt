package me.mattco.reeva.utils

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.errors.*

fun throwEvalError(message: String): Nothing {
    throw ThrowException(JSEvalErrorObject.create(Agent.runningContext.realm, message))
}

fun throwTypeError(message: String): Nothing {
    throw ThrowException(JSTypeErrorObject.create(Agent.runningContext.realm, message))
}

fun throwRangeError(message: String): Nothing {
    throw ThrowException(JSRangeErrorObject.create(Agent.runningContext.realm, message))
}

fun throwReferenceError(message: String): Nothing {
    throw ThrowException(JSReferenceErrorObject.create(Agent.runningContext.realm, message))
}

fun throwSyntaxError(message: String): Nothing {
    throw ThrowException(JSSyntaxErrorObject.create(Agent.runningContext.realm, message))
}

fun throwURIError(message: String): Nothing {
    throw ThrowException(JSURIErrorObject.create(Agent.runningContext.realm, message))
}
