package me.mattco.reeva.core.modules.records

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.modules.ResolvedBindingRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.module.JSModuleNamespaceObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.unreachable

class JVMPackageModuleRecord(
    realm: Realm,
    environment: EnvRecord?,
    namespace: JSModuleNamespaceObject?,
    private val packageName: String,
) : ModuleRecord(realm, environment, namespace) {
    override fun getExportedNames(exportStarSet: MutableSet<ModuleRecord>): List<String> {
        unreachable()
    }

    override fun resolveExport(exportName: String, resolveSet: MutableList<ResolvedBindingRecord>): ResolvedBindingRecord? {
        TODO()
    }

    override fun link() {
        // nop
    }

    override fun evaluate(interpreter: Interpreter): JSValue {
        // nop
        return JSUndefined
    }
}
