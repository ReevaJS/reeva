package com.reevajs.reeva.core

import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.utils.expect
import java.net.URI

class ModuleTree {
    private val allModules = mutableMapOf<URI, ModuleRecord>()
    private val moduleTree = mutableMapOf<URI, MutableMap<String, ModuleRecord>>()

    fun getAllLoadedModules() = allModules.values

    fun getModule(uri: URI) = allModules[uri]

    fun resolveImportedModule(referencingModule: ModuleRecord, specifier: String) =
        moduleTree[referencingModule.uri]?.get(specifier)

    fun setImportedModule(referencingModule: ModuleRecord, specifier: String, resolvedModule: ModuleRecord) {
        if (resolvedModule.uri in allModules)
            expect(allModules[resolvedModule.uri] == resolvedModule)

        allModules[resolvedModule.uri] = resolvedModule

        val map = moduleTree.getOrPut(referencingModule.uri, ::mutableMapOf)
        expect(specifier !in map)
        map[specifier] = resolvedModule
    }
}
