package com.reevajs.reeva.interpreter.transformer

sealed class TransformerResult {
    class Success(val ir: FunctionInfo) : TransformerResult()

    class UnsupportedError(val message: String) : TransformerResult()

    class InternalError(val cause: Throwable) : TransformerResult()
}
