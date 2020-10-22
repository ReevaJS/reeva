package me.mattco.reeva.utils

import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.primitives.JSUndefined

typealias JSArguments = List<JSValue>

fun JSArguments.argument(index: Int) = if (index > lastIndex) JSUndefined else this[index]
