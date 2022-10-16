package com.reevajs.reeva.runtime.collections

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.primitives.JSEmpty

data class MapData(
    val map: MutableMap<JSValue, JSValue> = mutableMapOf(),
    val keyInsertionOrder: MutableList<JSValue> = mutableListOf(),
) {
    var iterationCount = 0
        set(value) {
            if (value == 0)
                keyInsertionOrder.removeIf { it == JSEmpty }
            field = value
        }
}
