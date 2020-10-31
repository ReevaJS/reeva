package me.mattco.reeva.utils

import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*

fun Iterable<Boolean>.all() = this.all { it }
fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean) = this.mapIndexed(predicate).all()
fun CharArray.allIndexed(predicate: (index: Int, Char) -> Boolean) = this.mapIndexed(predicate).all()

fun Char.isHexDigit() = isDigit() || this in 'A'..'F' || this in 'a'..'f'
fun Char.hexValue(): Int {
    if (this in '0'..'9')
        return this.toInt() - '0'.toInt()
    if (this in 'a'..'f')
        return this.toInt() - 'a'.toInt() + 10
    if (this in 'A'..'F')
        return this.toInt() - 'A'.toInt() + 10
    throw NumberFormatException("$this is not a hex character")
}

fun StringBuilder.newline() = append("\n")

fun String.key() = PropertyKey(this)
fun Int.key() = PropertyKey(this)
fun JSSymbol.key() = PropertyKey(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse
fun String.toValue() = JSString(this)
fun Char.toValue() = JSString(this.toString())
fun Number.toValue() = JSNumber(this.toDouble())
