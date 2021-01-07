package me.mattco.reeva.utils

import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
import java.math.BigInteger
import kotlin.math.abs
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

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

fun Char.isRadixDigit(radix: Int): Boolean {
    val range = 0 until radix
    return when (radix) {
        in 2..10 -> this - '0' in range
        else -> {
            expect(radix > 10)
            this - '0' in range || this.toLowerCase() - 'a' in range
        }
    }
}

fun StringBuilder.newline(): StringBuilder = append("\n")

fun String.key() = PropertyKey(this)
fun Int.key() = PropertyKey(this)
fun Long.key() = PropertyKey(this)
fun JSSymbol.key() = PropertyKey(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse
fun String.toValue() = JSString(this)
fun Char.toValue() = JSString(this.toString())
fun Number.toValue() = JSNumber(this.toDouble())
fun BigInteger.toValue() = JSBigInt(this)

fun <T> KMutableProperty0<T>.temporaryChange(newValue: T): () -> Unit {
    val oldValue = this.get()
    this.set(newValue)

    return { this.set(oldValue) }
}
