package com.reevajs.reeva.runtime

import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSArguments(
    arguments: List<JSValue>,
    val thisValue: JSValue = JSUndefined,
    val newTarget: JSValue = JSUndefined,
) : List<JSValue> by arguments {
    fun argument(index: Int) = if (index > lastIndex) JSUndefined else this[index]

    fun takeArgs(range: IntRange) = range.map(::argument)

    fun withThisValue(newThisValue: JSValue) = JSArguments(this, newThisValue, newTarget)
    fun withNewTarget(newNewTarget: JSValue) = JSArguments(this, thisValue, newNewTarget)

    operator fun component1() = this.toList()
    operator fun component2() = thisValue
    operator fun component3() = newTarget
}
