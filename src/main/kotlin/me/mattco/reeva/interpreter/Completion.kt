package me.mattco.reeva.interpreter

import me.mattco.reeva.runtime.JSValue

/**
 * A record of the termination status of a compiler function.
 *
 * This serves the same concept as the CompletionRecords in the
 * spec, however, these are a compile-time only concept. They
 * are only used to return from generated functions, and do
 * not exist throughout the runtime.
 */
data class Completion @JvmOverloads constructor(
    @JvmField
    val type: Type,
    @JvmField
    val value: JSValue,
    @JvmField
    val target: String? = null,
) {
    val isNormal: Boolean
        get() = type == Type.Normal

    val isReturn: Boolean
        get() = type == Type.Return

    val isBreak: Boolean
        get() = type == Type.Break

    val isContinue: Boolean
        get() = type == Type.Continue

    val isThrow: Boolean
        get() = type == Type.Throw

    val isAbrupt: Boolean
        get() = !isNormal

    inline fun ifNormal(block: (Completion) -> Unit) = apply {
        if (isNormal)
            block(this)
    }

    inline fun ifReturn(block: (Completion) -> Unit) = apply {
        if (isReturn)
            block(this)
    }

    inline fun ifBreak(block: (Completion) -> Unit) = apply {
        if (isBreak)
            block(this)
    }

    inline fun ifContinue(block: (Completion) -> Unit) = apply {
        if (isContinue)
            block(this)
    }

    inline fun ifThrow(block: (Completion) -> Unit) = apply {
        if (isThrow)
            block(this)
    }

    inline fun ifAbrupt(block: (Completion) -> Unit) = apply {
        if (type != Completion.Type.Normal)
            block(this)
    }

    enum class Type {
        Normal,
        Return,
        Break,
        Continue,
        Throw,
    }
}
