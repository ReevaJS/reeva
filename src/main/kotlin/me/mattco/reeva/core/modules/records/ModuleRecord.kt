package me.mattco.reeva.core.modules.records

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.modules.ResolvedBindingRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.module.JSModuleNamespaceObject

@ECMAImpl("15.2.1.15")
abstract class ModuleRecord(
    val realm: Realm,
    var namespace: JSModuleNamespaceObject?,
) {
    private var linked = false
    private var evaluatedValue: Any? = null

    abstract fun getExportedNames(exportStarSet: MutableSet<ModuleRecord> = mutableSetOf()): List<String>

    abstract fun resolveExport(exportName: String, resolveSet: MutableList<ResolvedBindingRecord> = mutableListOf()): ResolvedBindingRecord?

    abstract fun resolveBinding(importName: String): JSValue

    abstract fun link()

    abstract fun evaluate(interpreter: Interpreter): JSValue
}
