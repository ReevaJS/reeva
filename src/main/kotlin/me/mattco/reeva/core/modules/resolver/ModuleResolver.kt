package me.mattco.reeva.core.modules.resolver

import me.mattco.reeva.core.modules.records.CyclicModuleRecord
import me.mattco.reeva.core.modules.records.JVMPackageModuleRecord
import me.mattco.reeva.core.modules.records.ModuleRecord
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.module.JSModuleNamespaceObject
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect

abstract class ModuleResolver {
    private val cachedModules = mutableMapOf<String, ModuleRecord>()

    abstract fun resolve(path: String): ModuleRecord?

    @ECMAImpl("15.2.1.18")
    fun hostResolveImportedModule(referencingModule: ModuleRecord, specifier: String): ModuleRecord {
        return cachedModules.getOrPut(specifier) {
            resolve(specifier) ?: TODO()
        }
    }

    companion object {
        @ECMAImpl("15.2.1.21")
        fun getModuleNamespace(module: ModuleRecord): JSModuleNamespaceObject {
            if (module is JVMPackageModuleRecord)
                TODO()
            if (module is CyclicModuleRecord)
                ecmaAssert(module.status != CyclicModuleRecord.Status.Unlinked)

            if (module.namespace != null)
                return module.namespace!!

            val unambiguousNames = mutableListOf<String>()
            module.getExportedNames(mutableSetOf()).forEach { name ->
                val resolution = module.resolveExport(name)
                if (resolution != null)
                    unambiguousNames.add(name)
            }

            return JSModuleNamespaceObject.create(module.realm, module, unambiguousNames).also {
                module.namespace = it
            }
        }
    }
}
