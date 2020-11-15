package me.mattco.reeva.core.modules

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.modules.records.ModuleRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.utils.unreachable


@ECMAImpl("15.2.1.15")
data class ResolvedBindingRecord(
    val module: ModuleRecord,
    val bindingName: String,
) {
    companion object {
        val AMBIGUOUS = object : ModuleRecord(Realm.EMPTY_REALM, null) {
            override fun getExportedNames(exportStarSet: MutableSet<ModuleRecord>) = unreachable()
            override fun resolveExport(exportName: String, resolveSet: MutableList<ResolvedBindingRecord>) = unreachable()
            override fun resolveBinding(importName: String) = unreachable()
            override fun link() = unreachable()
            override fun evaluate(interpreter: Interpreter) = unreachable()
        }.let { ResolvedBindingRecord(it, "") }
    }
}

data class ImportEntryRecord(
    val moduleRequest: String,
    val importName: String,
    val localName: String,
)

data class ExportEntryRecord(
    val moduleRequest: String?,
    val exportName: String?,
    val importName: String?,
    val localName: String?,
)
