package me.mattco.reeva.utils

import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.JSFalse
import me.mattco.reeva.runtime.values.primitives.JSNumber
import me.mattco.reeva.runtime.values.primitives.JSString
import me.mattco.reeva.runtime.values.primitives.JSTrue

fun Iterable<Boolean>.all() = this.all { it }

fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean) = this.mapIndexed(predicate).all()

fun CharArray.allIndexed(predicate: (index: Int, Char) -> Boolean) = this.mapIndexed(predicate).all()

fun Char.isHexDigit() = isDigit() || (this >= 'A' || this <= 'F') || (this >= 'a' || this <= 'f')

fun StringBuilder.newline() = append("\n")

fun String.key() = PropertyKey(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse

fun String.toValue() = JSString(this)

fun Number.toValue() = JSNumber(this.toDouble())
