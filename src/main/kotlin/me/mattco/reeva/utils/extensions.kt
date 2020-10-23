package me.mattco.reeva.utils

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.objects.PropertyKey
import me.mattco.reeva.runtime.values.primitives.JSFalse
import me.mattco.reeva.runtime.values.primitives.JSNumber
import me.mattco.reeva.runtime.values.primitives.JSString
import me.mattco.reeva.runtime.values.primitives.JSTrue
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.staticProperties

fun Iterable<Boolean>.all() = this.all { it }

fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean) = this.mapIndexed(predicate).all()

fun CharArray.allIndexed(predicate: (index: Int, Char) -> Boolean) = this.mapIndexed(predicate).all()

fun Char.isHexDigit() = isDigit() || (this >= 'A' || this <= 'F') || (this >= 'a' || this <= 'f')

fun StringBuilder.newline() = append("\n")

fun String.key() = PropertyKey(this)

fun Boolean.toValue() = if (this) JSTrue else JSFalse

fun String.toValue() = JSString(this)

fun Number.toValue() = JSNumber(this.toDouble())

inline fun <reified T : JSErrorObject> throwError(message: String? = null) {
    checkError() ?: return
    val obj = T::class.java.getDeclaredMethod("create", Realm::class.java, String::class.java)
        .invoke(null, Agent.runningContext.realm, message) as T
    Agent.throwError(obj)
}
