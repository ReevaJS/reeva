package com.reevajs.reeva.core

import com.reevajs.reeva.runtime.JSValue

open class ModuleRecord(val realm: Realm, val specifier: String) {
    /**
     * The list of named exports this module provides. A value of JSEmpty
     * indicates that the module has an export with that particular name, but
     * it has not been evaluated yet. This should be treated as an error at
     * the time of _use_ of that variable.
     */
    private val namedExportsBacker = mutableMapOf<String, JSValue>()

    fun getNamedExports(): Map<String, JSValue> = namedExportsBacker

    fun getNamedExport(export: String): JSValue? = namedExportsBacker[export]

    internal fun setNamedExport(export: String, value: JSValue) {
        namedExportsBacker[export] = value
    }

    companion object {
        const val DEFAULT_SPECIFIER = "*default*"
    }
}
