package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import java.net.URI

/**
 * ModuleRecord translates fairly literally to the object of the same name in the
 * ECMAScript specification. The most notable different, however, is that ECMA
 * defines three types of module records, all with varying levels of abstraction:
 *
 *   - ModuleRecord: This is an abstract record, and serves as the root of all
 *                   modules both in and out of the ECMAScript spec. Very slim,
 *                   has only four fields and no methods
 *   - CyclicModuleRecord: This is an abstract ModuleRecord which can participate
 *                   in cyclic dependencies with other cyclic modules. See the
 *                   following link for a more visual explanation of cyclic module
 *                   records:
 *                   https://tc39.es/ecma262/#sec-example-cyclic-module-record-graphs
 *   - SourceTextModuleRecord: This is the only concrete ModuleRecord class defined in
 *                   the ECMAScript specification, and as such is the class type of
 *                   every module produced by the ECMAScript spec (though they are
 *                   quite hand-wavy with module creation)
 *
 * This ModuleRecord class (as in the one under this comment) represents all three
 * of the above ECMA module record types. If a use case for multiple types of module
 * records arises (like JSON modules, for example!), then perhaps this class will be
 * split up.
 *
 * ModuleRecords have two phases: linking and evaluation. During linking, modules are
 * associated with the modules they require (i.e. import from), and they collect all
 * the import names they'll need during evaluation. Additionally, modules also set up
 * their ModuleEnvRecord during link time. This is possible because the bindings are
 * all simply initialized with JSEmpty, so no runtime evaluation is necessary.
 */
@ECMAImpl("16.2.1.4")
abstract class ModuleRecord(val realm: Realm) : Executable {
    // Serves as a unique module identifier across a given Realm.
    abstract val uri: URI

    @ECMAImpl("16.2.1.4")
    lateinit var env: ModuleEnvRecord
        protected set

    @ECMAImpl("16.2.1.4")
    private var namespace: JSObject? = null

    abstract fun link()

    abstract fun evaluate(): JSValue

    abstract fun getExportedNames(exportStarSet: MutableSet<SourceTextModuleRecord> = mutableSetOf()): List<String>

    protected abstract fun makeNamespaceImport(): JSObject

    fun getNamespaceObject(): JSObject {
        if (namespace == null)
            namespace = makeNamespaceImport()
        return namespace!!
    }

    /**
     * Non-standard function which lets a module know what imports are being
     * requested from it. This is necessary for JVM modules, as we don't want
     * to have to reflect the requested package and load every single class
     * into the env if we only want one or two.
     */
    open fun notifyImportedNames(names: Set<String>) {}

    /**
     * This method treats this module as the top-level module in the module
     * tree.
     */
    abstract override fun execute(): JSValue

    companion object {
        // These names don't really matter, as long as it isn't a valid
        // identifier. Might as well choose something descriptive.
        const val DEFAULT_SPECIFIER = "*default*"
        const val NAMESPACE_SPECIFIER = "*namespace*"
    }
}

