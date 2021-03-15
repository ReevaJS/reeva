package me.mattco.reeva.ir

class DeclarationsArray(
    varDecls: List<String>,
    lexDecls: List<String>,
    funcDecls: List<String>,
) : Iterable<String> {
    private val values = varDecls.toTypedArray() + lexDecls.toTypedArray() + funcDecls.toTypedArray()
    private val firstLex = varDecls.size
    private val firstFunc = firstLex + lexDecls.size

    val size: Int
        get() = values.size

    override fun iterator() = values.iterator()

    fun varIterator() = getValuesIterator(0, firstLex)
    fun lexIterator() = getValuesIterator(firstLex, firstFunc)
    fun funcIterator() = getValuesIterator(firstFunc, values.size)

    private fun getValuesIterator(start: Int, end: Int) = object : Iterable<String> {
        override fun iterator() = object : Iterator<String> {
            private var i = start

            override fun hasNext() = i < end
            override fun next() = values[i++]
        }
    }
}
