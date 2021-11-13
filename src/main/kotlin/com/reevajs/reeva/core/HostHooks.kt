package com.reevajs.reeva.core

import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.realm.RealmExtension
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import java.io.File

open class HostHooks {
    @ECMAImpl("9.5.2")
    open fun makeJobCallback(callback: JSFunction): Operations.JobCallback {
        return Operations.JobCallback(callback)
    }

    @ECMAImpl("9.5.3")
    open fun callJobCallback(realm: Realm, handler: Operations.JobCallback, arguments: JSArguments): JSValue {
        return Operations.call(realm, handler.callback, arguments)
    }

    @ECMAImpl("9.5.4")
    open fun enqueuePromiseJob(job: () -> Unit, realm: Realm?) {
        Agent.activeAgent.microtaskQueue.addMicrotask(job)
    }

    @ECMAImpl("9.6")
    open fun initializeHostDefinedRealm(realmExtensions: Map<Any, RealmExtension>): Realm {
        val realm = Realm(realmExtensions)
        realm.initObjects()
        val globalObject = initializeHostDefinedGlobalObject(realm)
        val thisValue = initializeHostDefinedGlobalThisValue(realm)
        realm.setGlobalObject(globalObject, thisValue)
        return realm
    }

    /**
     * Non-standard function. Used for step 7 of InitialHostDefinedRealm (9.6)
     */
    open fun initializeHostDefinedGlobalObject(realm: Realm): JSObject {
        return JSGlobalObject.create(realm)
    }

    /**
     * Non-standard function. Used for step 8 of InitialHostDefinedRealm (9.6)
     */
    open fun initializeHostDefinedGlobalThisValue(realm: Realm): JSValue {
        return JSUndefined
    }

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
    open fun promiseRejectionTracker(realm: Realm, promise: JSObject, operation: String) {
        if (operation == "reject") {
            Agent.activeAgent.microtaskQueue.addMicrotask {
                // If promise does not have any handlers by the time this microtask is ran, it
                // will not have any handlers, and we can print a warning
                if (!promise.getSlotAs<Boolean>(SlotName.PromiseIsHandled)) {
                    val result = promise.getSlotAs<JSValue>(SlotName.PromiseResult)
                    println("\u001b[31mUnhandled promise rejection: ${Operations.toString(realm, result)}\u001B[0m")
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
