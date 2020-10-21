package me.mattco.renva.runtime.values.objects

data class Attributes(var num: Int = 0) {
    val isEmpty: Boolean
        get() = num == 0

    val hasConfigurable: Boolean
        get() = num and HAS_CONFIGURABLE != 0

    val hasEnumerable: Boolean
        get() = num and HAS_ENUMERABLE != 0

    val hasWritable: Boolean
        get() = num and HAS_WRITABLE != 0

    val hasGetter: Boolean
        get() = num and HAS_GETTER != 0

    val hasSetter: Boolean
        get() = num and HAS_SETTER != 0

    val isConfigurable: Boolean
        get() = num and CONFIGURABLE != 0

    val isEnumerable: Boolean
        get() = num and ENUMERABLE != 0

    val isWritable: Boolean
        get() = num and WRITABLE != 0

    init {
        if (num and CONFIGURABLE != 0)
            num = num or HAS_CONFIGURABLE
        if (num and ENUMERABLE != 0)
            num = num or HAS_ENUMERABLE
        if (num and WRITABLE != 0)
            num = num or HAS_WRITABLE
    }

    fun setHasConfigurable() = apply {
        num = num or HAS_CONFIGURABLE
    }

    fun setHasEnumerable() = apply {
        num = num or HAS_ENUMERABLE
    }

    fun setHasWritable() = apply {
        num = num or HAS_WRITABLE
    }

    fun setHasGetter() = apply {
        num = num or HAS_GETTER
    }

    fun setHasSetter() = apply {
        num = num or HAS_SETTER
    }

    fun setConfigurable() = apply {
        num = num or CONFIGURABLE
    }

    fun setEnumerable() = apply {
        num = num or ENUMERABLE
    }

    fun setWritable() = apply {
        num = num or WRITABLE
    }

    companion object {
        const val CONFIGURABLE = 1 shl 0
        const val ENUMERABLE = 1 shl 1
        const val WRITABLE = 1 shl 2
        const val HAS_GETTER = 1 shl 3
        const val HAS_SETTER = 1 shl 4
        const val HAS_CONFIGURABLE = 1 shl 5
        const val HAS_ENUMERABLE = 1 shl 6
        const val HAS_WRITABLE = 1 shl 7

        const val defaultAttributes = CONFIGURABLE or ENUMERABLE or WRITABLE
    }
}
