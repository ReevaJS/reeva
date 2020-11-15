package me.mattco.reeva.core.modules.resolver

import me.mattco.reeva.core.modules.records.ModuleRecord
import me.mattco.reeva.runtime.annotations.ECMAImpl

abstract class ModuleResolver {
    private val cachedModules = mutableMapOf<String, ModuleRecord>()

    abstract fun resolve(path: String): ModuleRecord?

    @ECMAImpl("15.2.1.18")
    fun hostResolveImportedModule(referencingModule: ModuleRecord, specifier: String): ModuleRecord {
        return cachedModules.getOrPut(specifier) {
            resolve(specifier) ?: TODO()
        }
    }
}
