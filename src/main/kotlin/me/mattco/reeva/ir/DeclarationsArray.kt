package me.mattco.reeva.ir

class DeclarationsArray(
    varDecls: Collection<String>,
    letDecls: Collection<String>,
    constDecls: Collection<String>,
) : Iterable<String> {
    private val values = varDecls.toTypedArray() + letDecls.toTypedArray() + constDecls.toTypedArray()
    private val firstLet = varDecls.size
    private val firstConst = firstLet + letDecls.size

    val size: Int
        get() = values.size

    fun declarationAt(index: Int) = values[index]

    fun isVar(index: Int) = index < firstLet
    fun isLet(index: Int) = !isVar(index) && index < firstConst
    fun isConst(index: Int) = index >= firstConst

    override fun iterator() = values.iterator()

    fun varIterator() = getValuesIterator(0, firstLet)
    fun letIterator() = getValuesIterator(firstLet, firstConst)
    fun constIterator() = getValuesIterator(firstConst, values.size)

    private fun getValuesIterator(start: Int, end: Int) = object : Iterable<String> {
        override fun iterator() = object : Iterator<String> {
            private var i = start

            override fun hasNext() = i < end
            override fun next() = values[i++]
        }
    }
}
