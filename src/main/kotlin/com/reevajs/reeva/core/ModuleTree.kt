package com.reevajs.reeva.core

class ModuleTree {
    private val moduleTree = mutableMapOf<RootRecord, MutableMap<String, RootExecutable>>()

    fun resolveImportedModule(referencingRecord: RootRecord, specifier: String): RootExecutable? {
        val moduleMap = moduleTree[referencingRecord] ?: return null
        return moduleMap[specifier]
    }

    fun setImportedModule(referencingRecord: RootRecord, specifier: String, resolvedModule: RootExecutable) {
        moduleTree.getOrPut(referencingRecord) { mutableMapOf() }[specifier] = resolvedModule
    }
}