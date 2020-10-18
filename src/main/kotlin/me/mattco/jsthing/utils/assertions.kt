package me.mattco.jsthing.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun unreachable(): Nothing {
    throw IllegalStateException("Encountered unreachable() call")
}

fun shouldThrowError(errorName: String? = null): Nothing {
    throw IllegalStateException("FIXME: This should have instead thrown a JS ${errorName ?: "error"}")
}

@OptIn(ExperimentalContracts::class)
fun ecmaAssert(condition: Boolean, message: String? = null) {
    contract {
        returns() implies condition
    }

    if (!condition) {
        throw ECMAError(stringBuilder {
            append("ECMA assertion failed")
            if (message != null) {
                append(": ")
                append(message)
            }
        })
    }
}

@OptIn(ExperimentalContracts::class)
fun expect(condition: Boolean, message: String? = null) {
    contract {
        returns() implies condition
    }

    if (!condition) {
        throw ExpectationError(stringBuilder {
            append("Expectation failed")
            if (message != null) {
                append(": ")
                append(message)
            }
        })
    }
}

class ECMAError(message: String) : Exception(message)

class ExpectationError(message: String) : Exception(message)
