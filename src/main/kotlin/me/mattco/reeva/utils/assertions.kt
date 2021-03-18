package me.mattco.reeva.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun unreachable(): Nothing {
    throw IllegalStateException("Encountered unreachable() call")
}

@OptIn(ExperimentalContracts::class)
fun ecmaAssert(condition: Boolean, message: String? = null) {
    contract {
        returns() implies condition
    }

    if (!condition) {
        throw ECMAError(buildString {
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

    if (!condition)
        throw ExpectationError(message ?: "Expectation failed")
}

@OptIn(ExperimentalContracts::class)
fun expect(condition: Boolean, messageProvider: () -> String) {
    contract {
        returns() implies condition
    }

    if (!condition)
        throw ExpectationError(messageProvider())
}

class ECMAError(message: String) : Exception(message)

class ExpectationError(message: String) : Exception(message)
