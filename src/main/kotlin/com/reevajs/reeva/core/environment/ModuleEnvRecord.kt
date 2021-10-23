package com.reevajs.reeva.core.environment

import com.reevajs.reeva.core.ModuleRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

class ModuleEnvRecord(outer: EnvRecord?) : EnvRecord(outer) {
    private val moduleRecords = mutableSetOf<ModuleRecord>()
    private val cachedVariables = mutableMapOf<String, JSValue>()

    fun storeModuleRecord(moduleRecord: ModuleRecord) {
        expect(moduleRecord !in moduleRecords)
        moduleRecords.add(moduleRecord)
    }

    // We don't perform a hasBinding check for module imports, as imports are
    // always validated at the beginning of the script
    override fun hasBinding(name: String) = unreachable()

    override fun getBinding(name: String): JSValue {
        var result = cachedVariables[name]
        if (result != null)
            return result

        for (record in moduleRecords) {
            result = record.getNamedExport(name)
            if (result != null) {
                cachedVariables[name] = result
                return result
            }
        }

        unreachable()
    }

    override fun setBinding(name: String, value: JSValue) {
        // No need to worry about setting the value in the ModuleRecord, as
        // this only affects the local module, not the source module
        cachedVariables[name] = value
    }

    override fun hasBinding(slot: Int) = unreachable()
    override fun getBinding(slot: Int) = unreachable()
    override fun setBinding(slot: Int, value: JSValue) = unreachable()
}
