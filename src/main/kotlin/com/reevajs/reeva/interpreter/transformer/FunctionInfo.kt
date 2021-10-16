package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.interpreter.transformer.opcodes.Opcode

data class FunctionInfo(
    val name: String,
    val ir: IR,
    val isStrict: Boolean,
    val isTopLevel: Boolean,
)

enum class LocalKind {
    Int,
    Value,
    Boolean,
}
