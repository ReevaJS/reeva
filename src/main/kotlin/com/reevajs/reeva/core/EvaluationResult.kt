package com.reevajs.reeva.core

import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.runtime.JSValue

sealed class EvaluationResult(val value: JSValue) {
    abstract val isError: Boolean

    class Success(value: JSValue) : EvaluationResult(value) {
        override val isError = false
    }

    class ParseFailure(value: JSValue, val start: TokenLocation, val end: TokenLocation) : EvaluationResult(value) {
        override val isError = true
    }

    class RuntimeError(value: JSValue) : EvaluationResult(value) {
        override val isError = true
    }
}

