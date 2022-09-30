package com.reevajs.reeva.runtime.primitives

import com.reevajs.reeva.runtime.JSValue
import java.util.LinkedList

class JSString private constructor(
    private var selfString: String?,
    private var ropeLhs: JSString?,
    private var ropeRhs: JSString?,
) : JSValue() {
    private val isRope: Boolean get() = ropeLhs != null

    val string: String
        get() {
            if (selfString == null) {
                val queue = LinkedList(listOf(ropeLhs!!, ropeRhs!!))
                val builder = StringBuilder()

                while (queue.isNotEmpty()) {
                    val element = queue.removeFirst()
                    if (element.isRope) {
                        queue.addFirst(element.ropeRhs!!)
                        queue.addFirst(element.ropeLhs!!)
                    } else {
                        builder.append(element.selfString!!)
                    }
                }

                selfString = builder.toString()
                ropeLhs = null
                ropeRhs = null
            }

            return selfString!!
        }

    constructor(value: String) : this(value, null, null)

    override fun toString() = string

    companion object {
        val EMPTY = JSString("")

        fun makeRope(lhs: JSString, rhs: JSString) = JSString(null, lhs, rhs)
    }
}
