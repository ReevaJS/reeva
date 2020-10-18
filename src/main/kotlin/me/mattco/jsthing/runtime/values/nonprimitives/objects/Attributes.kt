package me.mattco.jsthing.runtime.values.nonprimitives.objects

data class Attributes(var attributes: Int = 0) {
    val isEmpty: Boolean
        get() = attributes == 0

    val hasConfigurable: Boolean
        get() = attributes and HAS_CONFIGURABLE != 0

    val hasEnumerable: Boolean
        get() = attributes and HAS_ENUMERABLE != 0

    val hasWritable: Boolean
        get() = attributes and HAS_WRITABLE != 0

    val hasGetter: Boolean
        get() = attributes and HAS_GETTER != 0

    val hasSetter: Boolean
        get() = attributes and HAS_SETTER != 0

    val isConfigurable: Boolean
        get() = attributes and CONFIGURABLE != 0

    val isEnumerable: Boolean
        get() = attributes and ENUMERABLE != 0

    val isWritable: Boolean
        get() = attributes and WRITABLE != 0

    init {
        if (attributes and CONFIGURABLE != 0)
            attributes = attributes or HAS_CONFIGURABLE
        if (attributes and ENUMERABLE != 0)
            attributes = attributes or HAS_ENUMERABLE
        if (attributes and WRITABLE != 0)
            attributes = attributes or HAS_WRITABLE
    }

    fun setHasConfigurable() = apply {
        attributes = attributes or HAS_CONFIGURABLE
    }

    fun setHasEnumerable() = apply {
        attributes = attributes or HAS_ENUMERABLE
    }

    fun setHasWritable() = apply {
        attributes = attributes or HAS_WRITABLE
    }

    fun setHasGetter() = apply {
        attributes = attributes or HAS_GETTER
    }

    fun setHasSetter() = apply {
        attributes = attributes or HAS_SETTER
    }

    fun setConfigurable() = apply {
        attributes = attributes or CONFIGURABLE
    }

    fun setEnumerable() = apply {
        attributes = attributes or ENUMERABLE
    }

    fun setWritable() = apply {
        attributes = attributes or WRITABLE
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
