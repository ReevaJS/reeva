package me.mattco.reeva.runtime.objects.index

class IndexedPropertyIterator(
    private val storage: IndexedProperties,
    startIndex: Int,
    private val skipEmpty: Boolean,
) : Iterator<Int> {
    private var currentIndex = startIndex

    init {
        if (skipEmpty)
            skipEmptyIndices()
    }

    override fun hasNext() = currentIndex < storage.arrayLikeSize

    override fun next(): Int {
        currentIndex++

        if (skipEmpty)
            skipEmptyIndices()

        return currentIndex
    }

    private fun skipEmptyIndices() {
        val indices = storage.indices()
        if (indices.isEmpty()) {
            currentIndex = storage.arrayLikeSize
            return
        }
        for (index in indices) {
            if (index >= currentIndex) {
                currentIndex = index
                break
            }
        }
    }
}
