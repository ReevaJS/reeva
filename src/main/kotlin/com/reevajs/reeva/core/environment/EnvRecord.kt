package com.reevajs.reeva.core.environment

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject

typealias EnvRecordKey = Any /* String | Int */

/**
 * The runtime-equivalent of the Parser's Scope objects
 */
@ECMAImpl("9.1.1")
abstract class EnvRecord(val outer: EnvRecord?) : JSValue() {
    abstract fun hasBinding(name: EnvRecordKey): Boolean

    abstract fun createMutableBinding(name: EnvRecordKey, deletable: Boolean)

    abstract fun createImmutableBinding(name: EnvRecordKey, isStrict: Boolean)

    abstract fun initializeBinding(name: EnvRecordKey, value: JSValue)

    abstract fun setMutableBinding(name: EnvRecordKey, value: JSValue, isStrict: Boolean)

    abstract fun getBindingValue(name: EnvRecordKey, isStrict: Boolean): JSValue

    abstract fun deleteBinding(name: EnvRecordKey): Boolean

    abstract fun hasThisBinding(): Boolean

    abstract fun hasSuperBinding(): Boolean

    abstract fun withBaseObject(): JSObject?
}
