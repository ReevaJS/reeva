package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.jvmcompat.JSClassObject
import com.reevajs.reeva.jvmcompat.JSPackageObject
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable
import java.net.URI

class JVMModuleRecord(realm: Realm, val specifier: String) : ModuleRecord(realm) {
    override val uri = URI(specifier.replace('.', '/'))

    private val classOrPkgName = specifier.removePrefix("jvm:")
    private var status = CyclicModuleRecord.Status.Unlinked
    private val requiredNames = mutableSetOf<String>()

    private val jvmClass = try {
        Class.forName(classOrPkgName)
    } catch (e: ClassNotFoundException) {
        null
    }

    private val jvmObj = if (jvmClass != null) {
        JSClassObject.create(jvmClass, realm)
    } else JSPackageObject.create(classOrPkgName, realm)

    override fun link() {
        ecmaAssert(status != CyclicModuleRecord.Status.Linking && status != CyclicModuleRecord.Status.Evaluating)
        status = CyclicModuleRecord.Status.Linked

        // We don't care about the outer env, as this module is never actually executed
        val env = ModuleEnvRecord(realm, null)
        environment = env

        for (requiredName in requiredNames) {
            env.createImmutableBinding(requiredName, isStrict = true)

            if (jvmObj is JSPackageObject) {
                if (requiredName == "default")
                    Errors.JVMCompat.JVMDefaultPackageImport(classOrPkgName).throwSyntaxError(realm)

                env.initializeBinding(requiredName, jvmObj.get(requiredName))
            } else {
                // TODO: Should we allow this?
                if (requiredName != "default")
                    Errors.JVMCompat.JVMNonDefaultClassImport(classOrPkgName).throwSyntaxError(realm)

                if (requiredName == "namespace-object")
                    Errors.JVMCompat.JVMNamespaceClassImport(classOrPkgName).throwSyntaxError(realm)

                env.initializeBinding(requiredName, jvmObj)
            }
        }
    }

    override fun evaluate(): JSValue {
        return AOs.promiseResolve(realm.promiseCtor, JSUndefined)
    }

    override fun makeNamespaceImport(exports: List<String>): JSObject {
        expect(jvmObj is JSPackageObject)
        return jvmObj
    }

    override fun getExportedNames(exportStarSet: MutableSet<SourceTextModuleRecord>) = emptyList<String>()

    override fun resolveExport(exportName: String, resolveSet: MutableList<ResolvedBinding>): ResolvedBinding {
        return ResolvedBinding.Record(this, exportName).also(resolveSet::add)
    }

    override fun notifyImportedNames(names: Set<String>) {
        expect(status == CyclicModuleRecord.Status.Linked || status == CyclicModuleRecord.Status.Unlinked)
        requiredNames.addAll(names)
    }

    override fun execute() = unreachable()
}
