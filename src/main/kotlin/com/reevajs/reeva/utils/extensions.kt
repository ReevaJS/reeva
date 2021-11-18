package com.reevajs.reeva.utils

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.*
import java.math.BigInteger
import kotlin.reflect.KMutableProperty0

fun Iterable<Boolean>.all() = this.all { it }
fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean) = this.mapIndexed(predicate).all()
fun CharArray.allIndexed(predicate: (index: Int, Char) -> Boolean) = this.mapIndexed(predicate).all()

fun Char.isHexDigit() = isDigit() || this in 'A'..'F' || this in 'a'..'f'
fun Char.hexValue(): Int {
    if (this in '0'..'9')
        return this.code - '0'.code
    if (this in 'a'..'f')
        return this.code - 'a'.code + 10
    if (this in 'A'..'F')
        return this.code - 'A'.code + 10
    throw NumberFormatException("$this is not a hex character")
}

fun Char.isRadixDigit(radix: Int): Boolean {
    val range = 0 until radix
    return when (radix) {
        in 2..10 -> this - '0' in range
        else -> {
            expect(radix > 10)
            this - '0' in range || this.lowercaseChar() - 'a' in range
        }
    }
}

fun StringBuilder.newline(): StringBuilder = append("\n")

fun Any.key() = PropertyKey.from(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse
fun String.toValue() = JSString(this)
fun Char.toValue() = JSString(this.toString())
fun Number.toValue() = JSNumber(this.toDouble())
fun BigInteger.toValue() = JSBigInt(this)

fun Any.toValue() = JSValue.from(this)

fun <T> KMutableProperty0<T>.temporaryChange(newValue: T): () -> Unit {
    val oldValue = this.get()
    this.set(newValue)

    return { this.set(oldValue) }
}

fun <T> Iterable<T>.duplicates(): Set<T> {
    val seen = mutableSetOf<T>()
    val duplicates = mutableSetOf<T>()
    for (item in this) {
        if (item in seen) {
            duplicates.add(item)
        } else seen.add(item)
    }
    return duplicates
}
