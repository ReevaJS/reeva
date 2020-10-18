package me.mattco.jsthing.parser

data class ParserError(
    val message: String,
    private val line_: Int,
    private val column_: Int
) {
    val line = line_ + 1
    val column = column_ + 1
}
