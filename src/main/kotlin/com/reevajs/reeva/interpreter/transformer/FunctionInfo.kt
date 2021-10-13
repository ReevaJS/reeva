package com.reevajs.reeva.interpreter.transformer

data class FunctionInfo(
    val name: String,
    val opcodes: List<Opcode>,
    val localSlots: List<LocalKind>,
    val argCount: Int,
    val isStrict: Boolean,
    val isTopLevel: Boolean,

    // Just so we can print them with the top-level script, not actually
    // necessary for function.
    val nestedFunctions: List<FunctionInfo>,
)

enum class LocalKind {
    Int,
    Value,
}
