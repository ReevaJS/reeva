package com.reevajs.reeva.interpreter.transformer

data class IRPackage(
    val opcodes: List<Opcode>,
    val localSlots: List<LocalKind>,
)

enum class LocalKind {
    Int,
    Value,
}
