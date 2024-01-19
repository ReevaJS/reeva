package com.reevajs.reeva.core.environment

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject

/**
 * The runtime-equivalent of the Parser's Scope objects
 */
@ECMAImpl("9.1.1")
abstract class EnvRecord(val outer: EnvRecord?) : JSValue() {
    val depth: Int by lazy { outer?.depth?.plus(1) ?: 0 }

    abstract fun hasBinding(name: String): Boolean

    abstract fun createMutableBinding(name: String, deletable: Boolean)

    abstract fun createImmutableBinding(name: String, isStrict: Boolean)

    abstract fun initializeBinding(name: String, value: JSValue)

    abstract fun setMutableBinding(name: String, value: JSValue, isStrict: Boolean)

    abstract fun getBindingValue(name: String, isStrict: Boolean): JSValue

    abstract fun deleteBinding(name: String): Boolean

    abstract fun hasThisBinding(): Boolean

    abstract fun hasSuperBinding(): Boolean

    abstract fun withBaseObject(): JSObject?
}
