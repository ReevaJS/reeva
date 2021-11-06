package com.reevajs.reeva.transformer

data class FunctionInfo(
    val name: String,
    val ir: IR,
    val isStrict: Boolean,
    val length: Int,
    val isTopLevel: Boolean,
)

enum class LocalKind {
    Int,
    Value,
    Boolean,
}
