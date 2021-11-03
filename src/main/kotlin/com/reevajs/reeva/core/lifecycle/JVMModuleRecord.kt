package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.jvmcompat.JSClassObject
import com.reevajs.reeva.jvmcompat.JSPackageObject
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

class JVMModuleRecord(realm: Realm, private val specifier: String) : ModuleRecord(realm) {
    private var status = CyclicModuleRecord.Status.Unlinked
    private val requiredNames = mutableSetOf<String>()

    private val jvmClass = try {
        Class.forName(specifier)
    } catch (e: ClassNotFoundException) {
        null
    }

    private val jvmObj = if (jvmClass != null) {
        JSClassObject.create(realm, jvmClass)
    } else JSPackageObject.create(realm, specifier)

    override fun link() {
        ecmaAssert(status != CyclicModuleRecord.Status.Linking && status != CyclicModuleRecord.Status.Evaluating)
        status = CyclicModuleRecord.Status.Linked

        // We don't care about the outer env, as this module is never actually executed
        env = ModuleEnvRecord(null)
    }

    override fun evaluate(): JSValue {
        for (requiredName in requiredNames) {
            when (requiredName) {
                DEFAULT_SPECIFIER, NAMESPACE_SPECIFIER -> env.setBinding(requiredName, jvmObj)
                else -> {
                    // TODO: This will not work for classes, as JSClassObject doesn't override get
                    env.setBinding(requiredName, jvmObj.get(requiredName))
                }
            }
        }

        return Operations.promiseResolve(realm.promiseCtor, JSUndefined)
    }

    override fun getExportedNames() = emptyList<String>()

    override fun notifyImportedNames(names: Set<String>) {
        expect(status == CyclicModuleRecord.Status.Linked || status == CyclicModuleRecord.Status.Unlinked)

        if (DEFAULT_SPECIFIER in names && jvmObj is JSPackageObject)
            Errors.JVMCompat.JVMDefaultPackageImport(specifier).throwSyntaxError(realm)

        if (NAMESPACE_SPECIFIER in names && jvmObj is JSClassObject)
            Errors.JVMCompat.JVMNamespaceClassImport(specifier).throwSyntaxError(realm)

        requiredNames.addAll(names)
    }

    override fun execute() = unreachable()

    companion object {
        private val jvmSpecifierRegex = """^([\w$]+\.)+[\w$]+$""".toRegex()

        fun isJVMSpecifier(specifier: String) = jvmSpecifierRegex.matches(specifier)
    }
}