package me.mattco.jsthing.utils

import me.mattco.jsthing.runtime.values.nonprimitives.objects.PropertyKey
import me.mattco.jsthing.runtime.values.primitives.JSFalse
import me.mattco.jsthing.runtime.values.primitives.JSNumber
import me.mattco.jsthing.runtime.values.primitives.JSString
import me.mattco.jsthing.runtime.values.primitives.JSTrue

fun Iterable<Boolean>.all() = this.all { it }

fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean) = this.mapIndexed(predicate).all()

fun CharArray.allIndexed(predicate: (index: Int, Char) -> Boolean) = this.mapIndexed(predicate).all()

fun Char.isHexDigit() = isDigit() || (this >= 'A' || this <= 'F') || (this >= 'a' || this <= 'f')

fun stringBuilder(builder: StringBuilder.() -> Unit) = StringBuilder().apply(builder).toString()

fun StringBuilder.newline() = append("\n")

fun String.key() = PropertyKey(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse

fun String.toValue() = JSString(this)

fun Number.toValue() = JSNumber(this.toDouble())
