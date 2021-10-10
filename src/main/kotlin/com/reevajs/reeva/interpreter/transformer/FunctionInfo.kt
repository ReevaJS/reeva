package com.reevajs.reeva.interpreter.transformer

data class FunctionInfo(
    val name: String,
    val opcodes: List<Opcode>,
    val localSlots: List<LocalKind>,
    val argCount: Int,
    val isStrict: Boolean,
    val isTopLevel: Boolean,
)

enum class LocalKind {
    Int,
    Value,
}
