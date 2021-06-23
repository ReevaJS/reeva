package me.mattco.reeva.jvmcompat

import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.modules.ResolvedBindingRecord
import me.mattco.reeva.core.modules.records.ModuleRecord
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.module.JSModuleNamespaceObject
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.unreachable

class JVMPackageModuleRecord(realm: Realm, private val packageName: String) : ModuleRecord(realm) {
    private val packageObj = JSPackageObject.create(realm, packageName)

    override fun makeNamespaceObject(): JSObject {
        return object : JSModuleNamespaceObject(realm, this@JVMPackageModuleRecord, emptyList()) {
            override fun get(property: PropertyKey, receiver: JSValue): JSValue {
                if (property.isSymbol)
                    return super.get(property, receiver)
                return resolveBinding(property.asString)
            }
        }
    }

    override fun getExportedNames(exportStarSet: MutableSet<ModuleRecord>): List<String> {
        unreachable()
    }

    override fun resolveBinding(importName: String): JSValue {
        val obj = packageObj.get(importName)
        if (obj !is JSClassObject)
            Errors.JVMCompat.BadImportFromPackage(importName, packageName).throwReferenceError(realm)
        return obj
    }

    override fun resolveExport(exportName: String, resolveSet: MutableList<ResolvedBindingRecord>): ResolvedBindingRecord? {
        resolveSet.forEach {
            if (this == it.module && exportName == it.bindingName) {
                // TODO: Error?
                return null
            }
        }

        return ResolvedBindingRecord(this, exportName)
    }

    // nop
    override fun link() = Unit

    // nop
    override fun evaluate(interpreter: Interpreter) = JSUndefined
}
