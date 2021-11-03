package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.other.JSModuleNamespaceObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import kotlin.math.min

@ECMAImpl("16.2.1.5", name = "Cyclic Module Records")
abstract class CyclicModuleRecord(realm: Realm) : ModuleRecord(realm) {
    @ECMAImpl("16.2.1.5", name = "[[Status]]")
    protected var status = Status.Unlinked

    @ECMAImpl("16.2.1.5", name = "[[EvaluationError]]")
    protected var evaluationError: ThrowException? = null

    /**
     * Tracks in what order this module was visited in the DFS module search,
     * both during linking and evaluation.
     */
    @ECMAImpl("16.2.1.5", name = "[[DFSIndex]]")
    protected var dfsIndex = 0

    /**
     * Tracks the highest dfsIndex of an ancestor in the current module graph
     * cycle. If there is no cycle in the module tree involving this module,
     * then this is equal to dfsIndex.
     */
    @ECMAImpl("16.2.1.5", name = "[[DFSAncestorIndex]]")
    protected var dfsAncestorIndex = 0

    /**
     * A list of all modules requested in import statements.
     */
    @ECMAImpl("16.2.1.5", name = "[[RequestedModules]]")
    abstract val requestedModules: List<String>

    /**
     * The module in this module's cycle which was visited first in the DFS
     * module search. For a module not in a cycle, it is simply this module.
     */
    @ECMAImpl("16.2.1.5", name = "[[CycleRoot]]")
    protected var cycleRoot: CyclicModuleRecord? = null

    /**
     * A promise which, when resolved, will indicate that both this module
     * and any child module in the module tree are done executing.
     */
    @ECMAImpl("16.2.1.5", name = "[[TopLevelCapability]]")
    protected var topLevelCapability: Operations.PromiseCapability? = null

    /**
     * Links the current module with any required modules and initialized the environment.
     *
     * This method is responsible for transitioning this module's status from Unlinked to
     * Linked. It performs module resolution via delegating to HostResolveImportedModule.
     * The overall goal of this method is to call initialEnvironment for every module in
     * the module tree in the correct order (depth-first search). Most of the work is
     * done by innerModuleLinking. Note that this method is only ever called on the module
     * at the top of the module tree. Child modules have their innerModuleLinking method
     * called directly.
     */
    @ECMAImpl("16.2.1.5.1")
    override fun link() {
        // status must not be Linking because this link() function is only ever invoked on the
        // top-level module (i.e. the module at the top of the module tree). All successive
        // modules are called using innerModuleLinking. status must not be Evaluating because
        // all modules are linked together, and then after linking, are evaluated together. The
        // two phases do not mix.
        ecmaAssert(status != Status.Linking && status != Status.Evaluating)

        // This will keep track of the modules we have visited during linking. This allows us
        // to track cycles in the module tree.
        val stack = mutableListOf<ModuleRecord>()

        try {
            // This function is responsible for doing most of the work
            innerModuleLinking(stack, 0)

            // "How could a module be evaluated after only linking?" Modules are cached, so when
            // you import the same module twice, you get the same ModuleRecord twice. The way
            // status would be Evaluated here is if you establish a module tree, evaluate it,
            // and then afterwards start a new module tree which overlaps with some modules which
            // have already been evaluated. If status is Evaluated, then the above call to
            // innerModuleLinking will do nothing.
            ecmaAssert(status == Status.Linked || status == Status.Evaluated)

            // Because this is the top level linking function, we better have cleared the stack.
            // The exact meaning of that is discussed more in innerModuleLinking.
            ecmaAssert(stack.isEmpty())
        } catch (e: ThrowException) {
            // The only way for the try block to throw is if resolveImportedModule throws, meaning
            // the host could not resolve the import path. We need to propagate this error to our
            // linking module, but as the modules didn't finish linking, we need to reset their
            // statuses to Unlinked.

            stack.filterIsInstance<CyclicModuleRecord>().forEach {
                ecmaAssert(it.status == Status.Linking)
                it.status = Status.Unlinked
            }
            ecmaAssert(status == Status.Unlinked)
            throw e
        }
    }

    private fun innerModuleLinking(stack: MutableList<ModuleRecord>, index_: Int): Int {
        var index = index_
        if (status != Status.Unlinked)
            return index

        status = Status.Linking

        // dfsIndex stores the position in which it was visited. For example, if this is the
        // 3rd module linked, then dfsIndex would be 2.
        dfsIndex = index

        // dfsAncestorIndex, on the other hand, tells us the earliest dfsIndex of this module's
        // parent modules (modules which directly or indirectly require this module).
        dfsAncestorIndex = index

        index += 1
        stack.add(this)

        for (required in requestedModules) {
            // Delegate resolving the module to the host, then link.
            val requiredModule = Reeva.activeAgent.hostHooks.resolveImportedModule(this, required)

            // We keep this index around to correctly track how many modules we have visited.
            if (requiredModule !is CyclicModuleRecord) {
                requiredModule.link()
                continue
            }

            index = requiredModule.innerModuleLinking(stack, index)

            val status = requiredModule.status
            ecmaAssert(status == Status.Linking || status == Status.Linked || status == Status.Evaluated)

            if (status == Status.Linking) {
                // Let's say we have this module graph: A <---> B, where A is the top of the module
                // tree. When we call innerModuleLinking on B from A, it's dfsAncestorIndex will be
                // set to index, which will be 1. However, in this loop, requiredModule will evaluate
                // to A, and thus this min expression will select requiredModule's dfsAncestorIndex,
                // which will be zero. This is the dfsIndex of the "top-most" module in the current
                // module cycle, which in this case _is_ A because it is at the top of the module
                // tree.
                dfsAncestorIndex = min(dfsAncestorIndex, requiredModule.dfsAncestorIndex)
            }
        }

        initializeEnvironment()

        // Modules should never appear in the module stack twice
        ecmaAssert(stack.count { it == this } == 1)

        // As the dfsAncestorIndex records the dfsIndex of an ancestor module, and the dfsIndex
        // is a measure of how early the module was visited in the module traversal, it makes
        // sense that the ancestor index must be equal to or less than the index itself. They
        // will be equal if this module does not participate in a cyclic dependency.
        ecmaAssert(dfsAncestorIndex <= dfsIndex)

        // If these two fields are equal, then we are at the top of the "local" module tree.
        // Consider this module graph: A ---> B ---> C, where we are currently in
        // innerModuleLinking for B. From B's perspective, the module graph is B ---> C, since
        // it doesn't know or care about A. At this point in the method, B's dfsAncestorIndex
        // will be equal to its dfsIndex, since in the local tree, it is at the top.
        //
        // Now consider the case where a module isn't at the top of its local module tree,
        // in other words, the case where there are cyclic dependencies: A <---> B. Now, at
        // this point for B, its dfsAncestorIndex is 0, whereas its dfsIndex is 1. So this
        // condition is not true for B, but _is_ true for A, since both fields are 0.
        if (dfsAncestorIndex == dfsIndex) {
            // If we are at the top of our local module tree, then we need to set all of our
            // dependent modules to Linked. This current module appears somewhere in the stack
            // (again, it may not be at the top of the global tree, only the local one), so
            // we only set the module at and after us to Linked.
            while (true) {
                val requiredModule = stack.removeLast()
                expect(requiredModule is CyclicModuleRecord)
                requiredModule.status = Status.Linked
                if (requiredModule == this)
                    break
            }
        }

        // Return this index to let the caller know how deep we have traversed in the DFS
        // module search.
        return index
    }

    /**
     * Responsible for constructing and initializing this module's ModuleEnvRecord.
     */
    abstract fun initializeEnvironment()

    /**
     * Responsible for running the code associated with this module. Most of the work is
     * done by innerModuleEvaluation. This structure of this method is very similar to
     * that of link; storing dfsIndexes, maintaining the module stack, etc. Like Link,
     * this method is only ever called on the module at the top of the module graph.
     * Child modules have innerModuleEvaluation called on them directly.
     */
    override fun evaluate(): JSValue {
        var module = this

        ecmaAssert(module.status == Status.Linked || module.status == Status.Evaluated)

        // If this module has already been evaluated and is participating in a module
        // cycle, then we want to work with the topLevelCapability of its cycleRoot
        if (module.status == Status.Evaluated)
            module = module.cycleRoot!!

        // This module is the top of a cycle (or simply does not participate in one), and
        // it has already been evaluated. Returns the promise that has already been set up.
        module.topLevelCapability?.also {
            return it.promise as JSObject
        }

        // Similarly to Link, we keep a stack of modules which have been visited in DFS order.
        val stack = mutableListOf<ModuleRecord>()
        val capability = Operations.newPromiseCapability(module.realm, module.realm.promiseCtor)
        module.topLevelCapability = capability

        try {
            // This function is responsible for doing most of the work
            module.innerModuleEvaluation(stack, 0)

            ecmaAssert(module.status == Status.Evaluated)
            ecmaAssert(module.evaluationError == null)

            // We have successfully evaluated this module and its children, so we can resolve
            // the promise.
            Operations.call(module.realm, capability.resolve!!, JSUndefined, listOf(JSUndefined))

            // We better have visited all the modules. Otherwise, there are some modules which
            // have yet to be evaluated.
            ecmaAssert(stack.isEmpty())
        } catch (e: ThrowException) {
            // The only way for the try block to throw is if resolveImportedModule throws, meaning
            // that some sort of runtime error occurred. At this point in Link(), we transition the
            // modules back to unlinked. This is because Link() has no side effects, and can be
            // freely relinked at any time in the future. This is not the has for Evaluate().
            // Evaluate() has side effects, and thus must only ever be run one. If an error is
            // thrown anywhere in the module tree, any modules which depend on it cache that error
            // and return it for any future calls to Evaluate(). This means that even if an error is
            // encountered, the module is still considered to have evaluated.

            stack.filterIsInstance<CyclicModuleRecord>().forEach {
                ecmaAssert(it.status == Status.Evaluating)
                it.status = Status.Evaluated
                it.evaluationError = e
            }

            ecmaAssert(module.status == Status.Evaluated)
            ecmaAssert(module.evaluationError == e)

            // Reject the promise with the cause of failure.
            Operations.call(module.realm, capability.reject!!, JSUndefined, listOf(e.value))
        }

        // Return the promise to the caller.
        return capability.promise
    }

    fun innerModuleEvaluation(stack: MutableList<ModuleRecord>, index_: Int): Int {
        var index = index_

        // We have already evaluated, then we can either simply return the dfs index
        // or, if we evaluated to an error, rethrow that error.
        if (status == Status.Evaluated) {
            if (evaluationError == null)
                return index
            throw evaluationError!!
        }

        if (status == Status.Evaluating)
            return index

        ecmaAssert(status == Status.Linked)

        status = Status.Evaluating
        dfsIndex = index
        dfsAncestorIndex = index
        index += 1
        stack.add(this)

        for (required in requestedModules) {
            val requiredModule = Reeva.activeAgent.hostHooks.resolveImportedModule(this, required)

            if (requiredModule !is CyclicModuleRecord) {
                val promise = requiredModule.evaluate()
                ecmaAssert(promise is JSObject)
                val promiseState = promise.getSlotAs<Operations.PromiseState>(SlotName.PromiseState)
                ecmaAssert(promiseState != Operations.PromiseState.Pending)
                if (promiseState == Operations.PromiseState.Rejected)
                    throw ThrowException(promise.getSlotAs(SlotName.PromiseResult))
                continue
            }

            index = requiredModule.innerModuleEvaluation(stack, index)
            val status = requiredModule.status
            ecmaAssert(status == Status.Evaluating || status == Status.Evaluated)

            if (status == Status.Evaluating) {
                ecmaAssert(requiredModule in stack)
                dfsAncestorIndex = min(dfsAncestorIndex, requiredModule.dfsAncestorIndex)
            } else {
                val roortModule = requiredModule.cycleRoot!!
                ecmaAssert(roortModule.status == Status.Evaluated)
                if (roortModule.evaluationError != null)
                    throw roortModule.evaluationError!!
            }
        }

        executeModule()

        ecmaAssert(stack.count { it == this } == 1)
        ecmaAssert(dfsAncestorIndex <= dfsIndex)

        if (dfsAncestorIndex == dfsIndex) {
            while (true) {
                val requiredModule = stack.removeLast()
                ecmaAssert(requiredModule is CyclicModuleRecord)
                requiredModule.status = Status.Evaluated
                requiredModule.cycleRoot = this
                if (requiredModule == this)
                    break
            }
        }

        return index
    }

    /**
     * Responsible for actually running the code of the module.
     */
    abstract fun executeModule()

    enum class Status {
        Unlinked,
        Linking,
        Linked,
        Evaluating,
        Evaluated,
    }
}