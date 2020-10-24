package me.mattco.reeva.compiler

import me.mattco.reeva.runtime.values.JSValue

/**
 * A record of the termination status of a compiler function.
 *
 * This serves the same concept as the CompletionRecords in the
 * spec, however, these are a compile-time only concept. They
 * are only used to return from generated functions, and do
 * not exist throughout the runtime.
 */
data class CompletionRecord @JvmOverloads constructor(
    @JvmField
    val type: Type,
    @JvmField
    val value: JSValue,
    @JvmField
    val target: String? = null,
) {
    enum class Type {
        Return,
        Break,
        Continue,
        Throw,
    }
}
