package com.reevajs.reeva.interpreter.transformer

sealed class TransformerResult {
    class Success(val ir: IRPackage) : TransformerResult()

    class UnsupportedError(val message: String) : TransformerResult()

    class InternalError(val cause: Throwable) : TransformerResult()
}