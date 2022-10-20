package com.reevajs.reeva.transformer

import com.reevajs.reeva.core.lifecycle.SourceInfo

data class FunctionInfo(
    val name: String,
    val ir: IR,
    val isStrict: Boolean,
    val length: Int,
    val isTopLevel: Boolean,
    val isGenerator: Boolean,
    val isArrow: Boolean,
    var sourceInfo: SourceInfo? = null,
)

enum class LocalKind {
    Int,
    Value,
    Boolean,
}
