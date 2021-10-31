package com.reevajs.reeva.core

import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.core.lifecycle.SourceInfo
import com.reevajs.reeva.utils.expect

class ModuleTree {
    private val moduleTree = mutableMapOf<SourceInfo, ModuleRecord>()

    fun resolveImportedModule(sourceInfo: SourceInfo) = moduleTree[sourceInfo]

    fun setImportedModule(sourceInfo: SourceInfo, resolvedModule: ModuleRecord) {
        expect(moduleTree[sourceInfo] == null)
        moduleTree[sourceInfo] = resolvedModule
    }
}
