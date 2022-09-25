package com.reevajs.reeva.core

import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import java.io.File

open class HostHooks {
    @ECMAImpl("9.5.2")
    open fun makeJobCallback(callback: JSFunction): AOs.JobCallback {
        return AOs.JobCallback(callback)
    }

    @ECMAImpl("9.5.3")
    open fun callJobCallback(handler: AOs.JobCallback, arguments: JSArguments): JSValue {
        return AOs.call(handler.callback, arguments)
    }

    @ECMAImpl("9.5.4")
    open fun enqueuePromiseJob(realm: Realm?, job: () -> Unit) {
        // There are three requirements for the job that is enqueued here:
        //   1. If realm is not null, each time job is invoked the implementation must perform implementation-defined
        //      steps such that execution is prepared to evaluate ECMAScript code at the time of job's invocation
        //   2. Let scriptOrModule be GetActiveScriptOrModule() at the time HostEnqueuePromiseJob is invoked. If realm
        //      is not null, each time job is invoked the implementation must perform implementation-defined steps such
        //      that scriptOrModule is the active script or module at the time of job's invocation
        //   3. Jobs must run in the same order as the HostEnqueuePromiseJob invocations that scheduled them.

        val agent = Agent.activeAgent

        // Same the active executable at the time of invocation to be used in the created ExecutionContext
        val scriptOrModule = agent.getActiveExecutable()

        agent.microtaskQueue.addMicrotask {
            expect(Agent.activeAgent == agent)

            if (realm != null)
                agent.pushExecutionContext(ExecutionContext(realm, executable = scriptOrModule))

            try {
                job()
            } finally {
                if (realm != null)
                    agent.popExecutionContext()
            }
        }
    }

    @ECMAImpl("9.6")
    open fun initializeHostDefinedRealm(): Realm {
        // 1. Let realm be CreateRealm().
        val realm = Realm.create()

        // 2. Let newContext be a new execution context.
        // 3. Set the Function of newContext to null.
        // 4. Set the Realm of newContext to realm.
        // 5. Set the ScriptOrModule of newContext to null.
        val newContext = ExecutionContext(realm)

        // 6. Push newContext onto the execution context stack; newContext is now the running execution context.
        Agent.activeAgent.pushExecutionContext(newContext)

        // 7. If the host requires use of an exotic object to serve as realm's global object, let global be such an
        //    object created in a host-defined manner. Otherwise, let global be undefined, indicating that an ordinary
        //    object should be created as the global object.
        val global = getHostDefinedGlobalObject(realm)

        // 8. If the host requires that the this binding in realm's global scope return an object other than the global
        //    object, let thisValue be such an object created in a host-defined manner. Otherwise, let thisValue be
        //    undefined, indicating that realm's global this binding should be the global object.
        val thisValue = getHostDefinedGlobalThisValue(realm)

        // 9. Perform SetRealmGlobalObject(realm, global, thisValue).
        realm.setGlobalObject(global, thisValue)

        // 10. Let globalObj be ? SetDefaultGlobalBindings(realm).
        val globalObj = JSGlobalObject.setDefaultGlobalBindings(realm)

        // 11. Create any host-defined global object properties on globalObj.
        createHostDefinedProperties(realm, globalObj)

        // 12. Return unused.
        return realm
    }

    /**
     * Non-standard function. Used for step 7 of InitializeHostDefinedRealm (9.6)
     */
    open fun getHostDefinedGlobalObject(realm: Realm): JSValue {
        return JSUndefined
    }

    /**
     * Non-standard function. Used for step 8 of InitializeHostDefinedRealm (9.6)
     */
    open fun getHostDefinedGlobalThisValue(realm: Realm): JSValue {
        return JSUndefined
    }

    /**
     * Non-standard function. Used for step 11 of InitializeHostDefinedRealm (9.6)
     */
    open fun createHostDefinedProperties(realm: Realm, globalObject: JSObject) {}

    /**
     * Used to allow the runtime-evaluation of user-provided strings. If such
     * behavior is not desired, this function may be overridden to throw an
     * exception, which will be reflected in eval and the various Function
     * constructors.
     */
    @ECMAImpl("19.2.1.2")
    open fun ensureCanCompileStrings(callerRealm: Realm, calleeRealm: Realm) {
    }

    @ECMAImpl("27.2.1.9")
    open fun promiseRejectionTracker(promise: JSObject, operation: String) {
        if (operation == "reject") {
            // Do essentially the same thing that we do in enqueuePromiseJob
            val agent = Agent.activeAgent
            val realm = agent.getActiveRealm()

            // Same the active executable at the time of invocation to be used in the created ExecutionContext
            val scriptOrModule = agent.getActiveExecutable()

            Agent.activeAgent.microtaskQueue.addMicrotask {
                expect(Agent.activeAgent == agent)
                agent.pushExecutionContext(ExecutionContext(realm, executable = scriptOrModule))

                try {
                    // If promise does not have any handlers by the time this microtask is ran, it
                    // will not have any handlers, and we can print a warning
                    if (!promise.getSlot(SlotName.PromiseIsHandled)) {
                        val result = promise.getSlot(SlotName.PromiseResult)
                        println("\u001b[31mUnhandled promise rejection: ${result.toJSString()}\u001B[0m")
                    }
                } finally {
                    agent.popExecutionContext()
                }
            }
        }
    }

    fun resolveImportedModule(referencingModule: ModuleRecord, specifier: String): ModuleRecord {
        val moduleTree = referencingModule.realm.moduleTree
        val existingModule = moduleTree.resolveImportedModule(referencingModule, specifier)
        if (existingModule != null)
            return existingModule

        return resolveImportedModuleImpl(referencingModule, specifier).also {
            val module = moduleTree.getModule(it.uri)
            if (module != null) {
                expect(module === it) {
                    "resolveImportedModuleImpl returned new ModuleRecord instance with a URI of an already loaded " +
                        "ModuleRecord (${module.uri})"
                }
            } else {
                moduleTree.setImportedModule(referencingModule, specifier, it)
            }
        }
    }

    /**
     * This function is responsible for turning a module specifier into a concrete ModuleRecord
     * instance. If the ModuleRecord returned by this function shares the same URI with a ModuleRecord
     * which has been loaded some time in the past, then the two ModuleRecords must be the same instance.
     */
    open fun resolveImportedModuleImpl(referencingModule: ModuleRecord, specifier: String): ModuleRecord {
        if (specifier.startsWith("jvm:")) {
            // JVM modules should be the same regardless of the referencing module, so the only thing that
            // matters for caching is the specifier. We need to access the module tree directly in order to
            // see if we have already loaded this JVM module.
            val existingJVMModule = referencingModule.realm.moduleTree
                .getAllLoadedModules()
                .filterIsInstance<JVMModuleRecord>()
                .firstOrNull { it.specifier == specifier }

            if (existingJVMModule != null)
                return existingJVMModule

            return JVMModuleRecord(referencingModule.realm, specifier)
        }

        expect(referencingModule is SourceTextModuleRecord)

        val referencingSourceInfo = referencingModule.parsedSource.sourceInfo
        val resolvedFile = File(referencingSourceInfo.resolveImportedSpecifier(specifier)).let {
            if (it.exists() && it.isDirectory) {
                for (extension in listOf("js", "mjs", "json")) {
                    val file = File(it, "index.$extension")
                    if (file.exists())
                        return@let file
                }
            }
            it
        }

        if (!resolvedFile.exists())
            Errors.NonExistentModule(specifier).throwInternalError(referencingModule.realm)

        val sourceInfo = makeSourceInfo(resolvedFile)

        // Check for existing modules with the same SourceInfo
        val existingModule = referencingModule.realm.moduleTree.getModule(sourceInfo.uri)
        if (existingModule != null)
            return existingModule

        val result = SourceTextModuleRecord
            .parseModule(referencingModule.realm, sourceInfo)

        if (result.hasError) {
            Agent.activeAgent.errorReporter.reportParseError(sourceInfo, result.error())
            TODO()
        }

        return result.value()
    }

    open fun makeSourceInfo(file: File): SourceInfo = FileSourceInfo(file, isModule = true)
}
