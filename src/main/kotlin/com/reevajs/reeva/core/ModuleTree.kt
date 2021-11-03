package com.reevajs.reeva.core

import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.utils.expect

class ModuleTree {
    private val moduleTree = mutableMapOf<ModuleRecord, MutableMap<String, ModuleRecord>>()

    fun getAllLoadedModules(): Map<ModuleRecord, Map<String, ModuleRecord>> = moduleTree

    fun resolveImportedModule(referencingModule: ModuleRecord, specifier: String) =
        moduleTree[referencingModule]?.get(specifier)

    fun setImportedModule(referencingModule: ModuleRecord, specifier: String, resolvedModule: ModuleRecord) {
        val map = moduleTree.getOrPut(referencingModule, ::mutableMapOf)
        expect(specifier !in map)
        map[specifier] = resolvedModule
    }
}
