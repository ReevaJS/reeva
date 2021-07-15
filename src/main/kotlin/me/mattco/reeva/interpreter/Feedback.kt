package me.mattco.reeva.interpreter

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.builtins.JSProxyObject
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.unreachable

class Feedback(size: Int) {
    val slots = Array<Slot>(size) { UninitializedSlot }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Slot> slot(index: Int): T {
        val slot = slots[index]
        if (slot is UninitializedSlot) {
            when (T::class) {
                TypeSlot::class -> {
                    slots[index] = TypeSlot(0)
                    return slots[index] as T
                }
                else -> TODO()
            }
        }
        return slot as T
    }

    sealed class Slot

    object UninitializedSlot : Slot()

    class TypeSlot(var flags: Int) : Slot() {
        val types: Int
            get() = flags.countOneBits()

        fun update(value: JSValue) {
            flags = lowerExcessiveType(flags or typeFromValue(value))
        }

        operator fun contains(type: Int): Boolean {
            val mask = type or ANY
            return (flags and mask) != 0
        }

        private fun typeFromValue(value: JSValue) = when (value) {
            JSUndefined -> UNDEFINED
            JSNull -> NULL
            is JSBoolean -> BOOLEAN
            is JSNumber -> NUMBER
            is JSBigInt -> BIGINT
            is JSString -> STRING
            is JSSymbol -> SYMBOL
            is JSProxyObject -> PROXY
            is JSFunction -> {
                var v = 0
                if (value.isCallable)
                    v = v or CALLABLE
                if (value.isConstructor())
                    v = v or CONSTRUCTABLE
                v
            }
            is JSObject -> OBJECT
            else -> unreachable()
        }

        companion object {
            const val NONE = 0
            const val UNDEFINED = 1 shl 1
            const val NULL = 1 shl 2
            const val BOOLEAN = 1 shl 3
            const val NUMBER = 1 shl 4
            const val BIGINT = 1 shl 5
            const val STRING = 1 shl 6
            const val SYMBOL = 1 shl 7
            const val CALLABLE = 1 shl 8
            const val CONSTRUCTABLE = 1 shl 9
            const val OBJECT = 1 shl 10
            const val PROXY = 1 shl 11

            const val UNDEFINED_OR_NULL = UNDEFINED or NULL
            const val ANY = 1 shl 31

            // TODO: A sane algorithm here, this is just temporary to prevent types
            // from being too excessive
            private fun lowerExcessiveType(flags: Int): Int {
                return if (flags.countOneBits() <= 3) flags else ANY
            }
        }
    }
}