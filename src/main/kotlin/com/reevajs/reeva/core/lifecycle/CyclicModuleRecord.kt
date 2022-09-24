package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.errors.ThrowException
import com.reevajs.reeva.core.errors.completion
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.ecmaAssert

@ECMAImpl("16.2.1.5")
abstract class CyclicModuleRecord(realm: Realm) : ModuleRecord(realm) {
    @ECMAImpl("16.2.1.5")
    var status = Status.Unlinked

    @ECMAImpl("16.2.1.5")
    var evaluationError: ThrowException? = null
        protected set

    /**
     * Tracks in what order this module was visited in the DFS module search,
     * both during linking and evaluation.
     */
    @ECMAImpl("16.2.1.5")
    var dfsIndex = 0

    /**
     * Tracks the highest dfsIndex of an ancestor in the current module graph
     * cycle. If there is no cycle in the module tree involving this module,
     * then this is equal to dfsIndex.
     */
    @ECMAImpl("16.2.1.5")
    var dfsAncestorIndex = 0

    /**
     * A list of all modules requested in import statements.
     */
    @ECMAImpl("16.2.1.5")
    abstract val requestedModules: List<String>

    /**
     * The module in this module's cycle which was visited first in the DFS
     * module search. For a module not in a cycle, it is simply this module.
     */
    @ECMAImpl("16.2.1.5")
    var cycleRoot: CyclicModuleRecord? = null

    /**
     * A promise which, when resolved, will indicate that both this module
     * and any child module in the module tree are done executing.
     */
    @ECMAImpl("16.2.1.5")
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
        // 1. Assert: module.[[Status]] is not linking or evaluating.
        // Note: status must not be Linking because this link() function is only ever invoked on the top-level module
        // (i.e. the module at the top of the module tree). All successive modules are called using innerModuleLinking.
        // status must not be Evaluating because all modules are linked together, and then after linking, are evaluated
        // together. The two phases do not mix.
        ecmaAssert(status != Status.Linking && status != Status.Evaluating)

        // 2. Let stack be a new empty List.
        val stack = mutableListOf<ModuleRecord>()

        try {
            // 3. Let result be Completion(InnerModuleLinking(module, stack, 0)).
            innerModuleLinking(stack, 0)

            // 5. Assert: module.[[Status]] is linked, evaluating-async, or evaluated.
            // Note: This will keep track of the modules we have visited during linking. This allows us to track cycles
            // in the module tree. "How could a module be evaluated after only linking?" Modules are cached, so when
            // you import the same module twice, you get the same ModuleRecord twice. The way status would be Evaluated
            // here is if you establish a module tree, evaluate it, and then afterwards start a new module tree which
            // overlaps with some modules which have already been evaluated. If status is Evaluated, then the above call
            // to innerModuleLinking will do nothing.
            ecmaAssert(status == Status.Linked || status == Status.Evaluated)

            // 6. Assert: stack is empty.
            // Note: Because this is the top level linking function, we better have cleared the stack. The exact meaning
            // of that is discussed more in innerModuleLinking.
            ecmaAssert(stack.isEmpty())

            // 7. Return unused.
        } catch (e: ThrowException) {
            // Note: The only way for the try block to throw is if resolveImportedModule throws, meaning the host could
            // not resolve the import path. We need to propagate this error to our linking module, but as the modules
            // didn't finish linking, we need to reset their statuses to Unlinked.

            // 4. If result is an abrupt completion, then
            //    a. For each Cyclic Module Record m of stack, do
            stack.filterIsInstance<CyclicModuleRecord>().forEach {
                // i.  Assert: m.[[Status]] is linking.
                ecmaAssert(it.status == Status.Linking)

                // ii. Set m.[[Status]] to unlinked.
                it.status = Status.Unlinked
            }

            // b. Assert: module.[[Status]] is unlinked.
            ecmaAssert(status == Status.Unlinked)

            // c. Return ? result.
            throw e
        }
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

        // 1. Assert: This call to Evaluate is not happening at the same time as another call to Evaluate within the
        //    surrounding agent.
        // 2. Assert: module.[[Status]] is linked, evaluating-async, or evaluated.
        ecmaAssert(module.status == Status.Linked || module.status == Status.Evaluated)

        // 3. If module.[[Status]] is evaluating-async or evaluated, set module to module.[[CycleRoot]].
        if (module.status == Status.Evaluated)
            module = module.cycleRoot!!


        // 4. If module.[[TopLevelCapability]] is not empty, then
        module.topLevelCapability?.also {
            // a. Return module.[[TopLevelCapability]].[[Promise]].
            return it.promise as JSObject
        }

        // 5. Let stack be a new empty List.
        val stack = mutableListOf<ModuleRecord>()

        // 6. Let capability be ! NewPromiseCapability(%Promise%).
        val capability = Operations.newPromiseCapability(module.realm.promiseCtor)

        // 7. Set module.[[TopLevelCapability]] to capability.
        module.topLevelCapability = capability

        // 8. Let result be Completion(InnerModuleEvaluation(module, stack, 0)).
        val result = completion {
            module.innerModuleEvaluation(stack, 0)
        }

        // 9. If result is an abrupt completion, then
        if (result.hasError) {
            // a. For each Cyclic Module Record m of stack, do
            stack.filterIsInstance<CyclicModuleRecord>().forEach {
                // i.   Assert: m.[[Status]] is evaluating.
                ecmaAssert(it.status == Status.Evaluating)

                // ii.  Set m.[[Status]] to evaluated.
                it.status = Status.Evaluated

                // iii. Set m.[[EvaluationError]] to result.
                it.evaluationError = result.error()
            }

            // b. Assert: module.[[Status]] is evaluated.
            ecmaAssert(module.status == Status.Evaluated)

            // c. Assert: module.[[EvaluationError]] is result.
            ecmaAssert(module.evaluationError == result.error())

            // d. Perform ! Call(capability.[[Reject]], undefined, « result.[[Value]] »).
            Operations.call(capability.reject!!, JSUndefined, listOf(result.error().value))
        }
        // 10. Else,
        else {
            // a. Assert: module.[[Status]] is evaluating-async or evaluated.
            ecmaAssert(module.status == Status.Evaluated)

            // b. Assert: module.[[EvaluationError]] is empty.
            ecmaAssert(module.evaluationError == null)

            // c. If module.[[AsyncEvaluation]] is false, then
            //    i.  Assert: module.[[Status]] is evaluated.
            //    ii. Perform ! Call(capability.[[Resolve]], undefined, « undefined »).
            Operations.call(capability.resolve!!, JSUndefined, listOf(JSUndefined))

            // d. Assert: stack is empty.
            ecmaAssert(stack.isEmpty())
        }

        // 11. Return capability.[[Promise]].
        return capability.promise
    }

    abstract fun getImportedNames(specifier: String): Set<String>

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
