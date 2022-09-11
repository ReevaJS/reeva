package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.other.JSModuleNamespaceObject
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import java.net.URI
import kotlin.math.min

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
    var environment: ModuleEnvRecord? = null
        protected set

    @ECMAImpl("16.2.1.4")
    private var namespace: JSObject? = null

    abstract fun getExportedNames(exportStarSet: MutableSet<SourceTextModuleRecord> = mutableSetOf()): List<String>

    abstract fun resolveExport(
        exportName: String,
        resolveSet: MutableList<ResolvedBinding> = mutableListOf(),
    ): ResolvedBinding

    abstract fun link()

    abstract fun evaluate(): JSValue

    // Non-standard function: Hook to allow JVMModuleRecord to override the creation of its namespace import
    protected open fun makeNamespaceImport(exports: List<String>): JSObject {
        return JSModuleNamespaceObject.create(this, exports, realm)
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

    @ECMAImpl("16.2.1.10")
    fun getModuleNamespace(): JSObject {
        // 1. Assert: If module is a Cyclic Module Record, then module.[[Status]] is not unlinked.
        if (this is CyclicModuleRecord)
            ecmaAssert(status != CyclicModuleRecord.Status.Unlinked)

        // 2. Let namespace be module.[[Namespace]].
        var namespace = this.namespace

        // 3. If namespace is empty, then
        if (namespace == null) {
            // a. Let exportedNames be ? module.GetExportedNames().
            val exportedNames = getExportedNames()

            // b. Let unambiguousNames be a new empty List.
            val unambiguousNames = mutableListOf<String>()

            // c. For each element name of exportedNames, do
            for (name in exportedNames) {
                // i.  Let resolution be ? module.ResolveExport(name).
                val resolution = resolveExport(name)

                // ii. If resolution is a ResolvedBinding Record, append name to unambiguousNames.
                if (resolution is ResolvedBinding.Record)
                    unambiguousNames.add(name)
            }

            // d. Set namespace to ModuleNamespaceCreate(module, unambiguousNames).
            namespace = makeNamespaceImport(unambiguousNames)
        }

        // 4. Return namespace.
        return namespace
    }

    @ECMAImpl("16.2.1.5.1.1")
    protected fun innerModuleLinking(stack: MutableList<ModuleRecord>, index_: Int): Int {
        var index = index_

        // 1. If module is not a Cyclic Module Record, then
        if (this !is CyclicModuleRecord) {
            // a. Perform ? module.Link().
            this.link()

            // b. Return index.
            return index
        }

        // 2. 2. If module.[[Status]] is linking, linked, evaluating-async, or evaluated, then
        if (status == CyclicModuleRecord.Status.Linking ||
            status == CyclicModuleRecord.Status.Linked ||
            status == CyclicModuleRecord.Status.Evaluated
        ) {
            // a. Return index.
            return index
        }

        // 3. Assert: module.[[Status]] is unlinked.
        ecmaAssert(status == CyclicModuleRecord.Status.Unlinked)

        // 4. Set module.[[Status]] to linking.
        status = CyclicModuleRecord.Status.Linking

        // 5. Set module.[[DFSIndex]] to index.
        dfsIndex = index

        // 6. Set module.[[DFSAncestorIndex]] to index.
        dfsAncestorIndex = index

        // 7. Set index to index + 1.
        index += 1

        // 8. Append module to stack.
        stack.add(this)

        // 9. For each String required of module.[[RequestedModules]], do
        for (required in requestedModules) {
            // a. Let requiredModule be ? HostResolveImportedModule(module, required).
            val requiredModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, required)

            // Non-standard step: tell the module which names we are attempting to import. This is
            // required for JVM modules.
            requiredModule.notifyImportedNames(getImportedNames(required))

            // b. Set index to ? InnerModuleLinking(requiredModule, stack, index).
            index = requiredModule.innerModuleLinking(stack, index)

            // c. If requiredModule is a Cyclic Module Record, then
            if (requiredModule is CyclicModuleRecord) {
                // i.   Assert: requiredModule.[[Status]] is either linking, linked, evaluating-async, or evaluated.
                ecmaAssert(requiredModule.status.let {
                    it == CyclicModuleRecord.Status.Linking ||
                        it == CyclicModuleRecord.Status.Linked ||
                        it == CyclicModuleRecord.Status.Evaluated
                })

                // ii.  Assert: requiredModule.[[Status]] is linking if and only if requiredModule is in stack.
                // iii. If requiredModule.[[Status]] is linking, then
                if (requiredModule.status == CyclicModuleRecord.Status.Linking) {
                    ecmaAssert(requiredModule in stack)

                    // 1. Set module.[[DFSAncestorIndex]] to min(module.[[DFSAncestorIndex]],
                    //    requiredModule.[[DFSAncestorIndex]]).
                    // Note: Let's say we have this module graph: A <---> B, where A is the top of the module tree. When
                    // we call innerModuleLinking on B from A, it's dfsAncestorIndex will be set to index, which will be
                    // 1. However, in this loop, requiredModule will evaluate to A, and thus this min expression will
                    // select requiredModule's dfsAncestorIndex, which will be zero. This is the dfsIndex of the
                    // "top-most" module in the current module cycle, which in this case _is_ A because it is at the top
                    // of the module tree.
                    dfsAncestorIndex = min(dfsAncestorIndex, requiredModule.dfsAncestorIndex)
                }
            }
        }

        // 10. Perform ? module.InitializeEnvironment().
        initializeEnvironment()

        // 11. Assert: module occurs exactly once in stack.
        ecmaAssert(stack.count { it === this } == 1)

        // 12. Assert: module.[[DFSAncestorIndex]] ≤ module.[[DFSIndex]].
        // Note: As the dfsAncestorIndex records the dfsIndex of an ancestor module, and the dfsIndex
        // is a measure of how early the module was visited in the module traversal, it makes
        // sense that the ancestor index must be equal to or less than the index itself. They
        // will be equal if this module does not participate in a cyclic dependency.
        ecmaAssert(dfsAncestorIndex <= dfsIndex)

        // 13. If module.[[DFSAncestorIndex]] = module.[[DFSIndex]], then
        // Note: If these two fields are equal, then we are at the top of the "local" module tree. Consider this module
        // graph: A ---> B ---> C, where we are currently in innerModuleLinking for B. From B's perspective, the module
        // graph is B ---> C, since it doesn't know or care about A. At this point in the method, B's dfsAncestorIndex
        // will be equal to its dfsIndex, since in the local tree, it is at the top.
        //
        // Now consider the case where a module isn't at the top of its local module tree, in other words, the case
        // where there are cyclic dependencies: A <---> B. Now, at this point for B, its dfsAncestorIndex is 0, whereas
        // its dfsIndex is 1. So this condition is not true for B, but _is_ true for A, since both fields are 0.
        if (dfsAncestorIndex == dfsIndex) {
            // a. Let done be false.
            // b. Repeat, while done is false,
            // Note: If we are at the top of our local module tree, then we need to set all of our dependent modules to
            // Linked. This current module appears somewhere in the stack (again, it may not be at the top of the global
            // tree, only the local one), so we only set the module at and after us to Linked.
            while (true) {
                // i.   Let requiredModule be the last element in stack.
                // ii.  Remove the last element of stack.
                val requiredModule = stack.removeLast()

                // iii. Assert: requiredModule is a Cyclic Module Record.
                ecmaAssert(requiredModule is CyclicModuleRecord)

                // iv.  Set requiredModule.[[Status]] to linked.
                requiredModule.status = CyclicModuleRecord.Status.Linked

                // v.   If requiredModule and module are the same Module Record, set done to true.
                if (requiredModule === this)
                    break
            }
        }

        // 14. Return index.
        // Note: Return this index to let the caller know how deep we have traversed in the DFS module search.
        return index
    }

    @ECMAImpl("16.2.1.5.2.1")
    fun innerModuleEvaluation(stack: MutableList<ModuleRecord>, index_: Int): Int {
        var index = index_

        // 1. If module is not a Cyclic Module Record, then
        if (this !is CyclicModuleRecord) {
            // a. Let promise be ! module.Evaluate().
            val promise = evaluate()
            expect(promise is JSObject)

            // b. Assert: promise.[[PromiseState]] is not pending.
            val promiseState = promise.getSlotAs<Operations.PromiseState>(SlotName.PromiseState)
            ecmaAssert(promiseState != Operations.PromiseState.Pending)

            // c. If promise.[[PromiseState]] is rejected, then
            if (promiseState == Operations.PromiseState.Rejected) {
                // i. Return ThrowCompletion(promise.[[PromiseResult]]).
                val result = promise.getSlotAs<JSValue>(SlotName.PromiseResult)
                throw ThrowException(result)
            }

            // d. Return index.
            return index
        }

        // 2. If module.[[Status]] is evaluating-async or evaluated, then
        if (status == CyclicModuleRecord.Status.Evaluated) {
            // a. If module.[[EvaluationError]] is empty, return index.
            if (evaluationError == null)
                return index

            // b. Otherwise, return ? module.[[EvaluationError]].
            throw evaluationError!!
        }

        // 3. If module.[[Status]] is evaluating, return index.
        if (status == CyclicModuleRecord.Status.Evaluating)
            return index

        // 4. Assert: module.[[Status]] is linked.
        ecmaAssert(status == CyclicModuleRecord.Status.Linked)

        // 5. Set module.[[Status]] to evaluating.
        status = CyclicModuleRecord.Status.Evaluating

        // 6. Set module.[[DFSIndex]] to index.
        dfsIndex = index

        // 7. Set module.[[DFSAncestorIndex]] to index.
        dfsAncestorIndex = index

        // 8. Set module.[[PendingAsyncDependencies]] to 0.

        // 9. Set index to index + 1.
        index += 1

        // 10. Append module to stack.
        stack.add(this)

        // 11. For each String required of module.[[RequestedModules]], do
        for (required in requestedModules) {
            // a. Let requiredModule be ! HostResolveImportedModule(module, required).
            // b. NOTE: Link must be completed successfully prior to invoking this method, so every requested module is
            //    guaranteed to resolve successfully.
            var requiredModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, required)

            // c. Set index to ? InnerModuleEvaluation(requiredModule, stack, index).
            index = requiredModule.innerModuleEvaluation(stack, index)

            // d. If requiredModule is a Cyclic Module Record, then
            if (requiredModule is CyclicModuleRecord) {
                // i.   Assert: requiredModule.[[Status]] is either evaluating, evaluating-async, or evaluated.
                ecmaAssert(requiredModule.status.let {
                    it == CyclicModuleRecord.Status.Evaluating || it == CyclicModuleRecord.Status.Evaluated
                })

                // ii.  Assert: requiredModule.[[Status]] is evaluating if and only if requiredModule is in stack.
                if (requiredModule.status == CyclicModuleRecord.Status.Evaluating)
                    ecmaAssert(requiredModule in stack)

                // iii. If requiredModule.[[Status]] is evaluating, then
                if (requiredModule.status == CyclicModuleRecord.Status.Evaluating) {
                    // 1. Set module.[[DFSAncestorIndex]] to min(module.[[DFSAncestorIndex]], requiredModule.[[DFSAncestorIndex]]).
                    dfsAncestorIndex = min(dfsAncestorIndex, requiredModule.dfsAncestorIndex)
                }
                // iv. Else,
                else {
                    // 1. Set requiredModule to requiredModule.[[CycleRoot]].
                    requiredModule = requiredModule.cycleRoot as CyclicModuleRecord

                    // 2. Assert: requiredModule.[[Status]] is evaluating-async or evaluated.
                    ecmaAssert(requiredModule.status == CyclicModuleRecord.Status.Evaluated)

                    // 3. If requiredModule.[[EvaluationError]] is not empty, return
                    //    ? requiredModule.[[EvaluationError]].
                    if (requiredModule.evaluationError != null)
                        throw requiredModule.evaluationError!!
                }

                // v. If requiredModule.[[AsyncEvaluation]] is true, then
                //    1. Set module.[[PendingAsyncDependencies]] to module.[[PendingAsyncDependencies]] + 1.
                //    2. Append module to requiredModule.[[AsyncParentModules]].
            }
        }

        // 12. If module.[[PendingAsyncDependencies]] > 0 or module.[[HasTLA]] is true, then
        //     a. Assert: module.[[AsyncEvaluation]] is false and was never previously set to true.
        //     b. Set module.[[AsyncEvaluation]] to true.
        //     c. NOTE: The order in which module records have their [[AsyncEvaluation]] fields transition to true is significant. (See 16.2.1.5.2.4.)
        //     d. If module.[[PendingAsyncDependencies]] is 0, perform ExecuteAsyncModule(module).

        // 13. Otherwise, perform ? module.ExecuteModule().
        executeModule()

        // 14. Assert: module occurs exactly once in stack.
        ecmaAssert(stack.count { it == this } == 1)

        // 15. Assert: module.[[DFSAncestorIndex]] ≤ module.[[DFSIndex]].
        ecmaAssert(dfsAncestorIndex <= dfsIndex)

        // 16. If module.[[DFSAncestorIndex]] = module.[[DFSIndex]], then
        if (dfsAncestorIndex == dfsIndex) {
            // a. Let done be false.
            // b. Repeat, while done is false,
            while (true) {
                // i.   Let requiredModule be the last element in stack.
                // ii.  Remove the last element of stack.
                val requiredModule = stack.removeLast()

                // iii. Assert: requiredModule is a Cyclic Module Record.
                ecmaAssert(requiredModule is CyclicModuleRecord)

                // iv.  If requiredModule.[[AsyncEvaluation]] is false, set requiredModule.[[Status]] to evaluated.
                // v.   Otherwise, set requiredModule.[[Status]] to evaluating-async.
                requiredModule.status = CyclicModuleRecord.Status.Evaluated

                // vi.  If requiredModule and module are the same Module Record, set done to true.
                // vii. Set requiredModule.[[CycleRoot]] to module.
                requiredModule.cycleRoot = this
                if (requiredModule == this)
                    break
            }
        }

        // 17. Return index.
        return index
    }

    sealed interface ResolvedBinding {
        data class Record(
            val module: ModuleRecord,
            val bindingName: String?, // null indicates "namespace" in spec
        ) : ResolvedBinding

        object Null : ResolvedBinding

        object Ambiguous : ResolvedBinding
    }

    companion object {
        // These names don't really matter, as long as it isn't a valid
        // identifier. Might as well choose something descriptive.
        const val DEFAULT_SPECIFIER = "*default*"
        const val NAMESPACE_SPECIFIER = "*namespace*"
    }
}

