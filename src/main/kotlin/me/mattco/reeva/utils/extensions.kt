package me.mattco.reeva.utils

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Agent.Companion.ifError
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.errors.JSErrorObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.values.primitives.*

fun Iterable<Boolean>.all() = this.all { it }
fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean) = this.mapIndexed(predicate).all()
fun CharArray.allIndexed(predicate: (index: Int, Char) -> Boolean) = this.mapIndexed(predicate).all()

fun Char.isHexDigit() = isDigit() || this in 'A'..'F' || this in 'a'..'f'

fun StringBuilder.newline() = append("\n")

fun String.key() = PropertyKey(this)
fun Int.key() = PropertyKey(this)
fun JSSymbol.key() = PropertyKey(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse
fun String.toValue() = JSString(this)
fun Number.toValue() = JSNumber(this.toDouble())

inline fun <reified T : JSErrorObject> throwError(message: String? = null) {
    ifError { return }
    val obj = T::class.java.getDeclaredMethod("create", Realm::class.java, String::class.java)
        .invoke(null, Agent.runningContext.realm, message) as T
    Agent.throwError(obj)
}
