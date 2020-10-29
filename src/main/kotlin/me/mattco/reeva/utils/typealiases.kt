package me.mattco.reeva.utils

import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.primitives.JSUndefined

typealias JSArguments = List<JSValue>

fun JSArguments.argument(index: Int) = if (index > lastIndex) JSUndefined else this[index]
fun JSArguments.takeArgs(range: IntRange) = range.map { argument(it) }

typealias NativeGetterSignature = (thisValue: JSValue) -> JSValue
typealias NativeSetterSignature = (thisValue: JSValue, value: JSValue) -> Unit
typealias NativeFunctionSignature = (thisValue: JSValue, arguments: JSArguments) -> JSValue
