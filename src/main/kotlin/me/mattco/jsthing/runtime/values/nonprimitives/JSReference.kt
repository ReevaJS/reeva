package me.mattco.jsthing.runtime.values.nonprimitives

import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.Ref

class JSReference(
    val baseValue: Ref,
    val name: String,
    val strict: Boolean
) : JSValue()
