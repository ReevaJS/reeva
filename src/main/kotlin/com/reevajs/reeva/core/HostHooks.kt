package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
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
        Reeva.activeAgent.microtaskQueue.addMicrotask(job)
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
            Reeva.activeAgent.microtaskQueue.addMicrotask {
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
        val existingModule = referencingModule.realm.moduleTree.resolveImportedModule(referencingModule, specifier)
        if (existingModule != null)
            return existingModule

        return resolveImportedModuleImpl(referencingModule, specifier)
    }

    open fun resolveImportedModuleImpl(referencingModule: ModuleRecord, specifier: String): ModuleRecord {
        val existingModule = referencingModule.realm.moduleTree.resolveImportedModule(referencingModule, specifier)
        if (existingModule != null)
            return existingModule

        if (JVMModuleRecord.isJVMSpecifier(specifier)) {
            // JVM modules should be the same regardless of the referencing module, so the only thing that
            // matters for caching is the specifier. We need to access the module tree directly in order to
            // see if we have already loaded this JVM module.
            val moduleTree = referencingModule.realm.moduleTree.getAllLoadedModules()
            val existingJVMModule = moduleTree.values.firstOrNull { specifier in it }?.get(specifier)
            if (existingJVMModule != null)
                return existingJVMModule

            return JVMModuleRecord(referencingModule.realm, specifier).also {
                referencingModule.realm.moduleTree.setImportedModule(referencingModule, specifier, it)
            }
        }

        // TODO: Get rid of this expect somehow
        expect(referencingModule is SourceTextModuleRecord)

        val referencingSourceInfo = referencingModule.parsedSource.sourceInfo
        val resolvedFile = referencingSourceInfo.resolveImportedFilePath(specifier)
        if (!resolvedFile.exists())
            Errors.NonExistentImport(specifier, referencingSourceInfo.name).throwInternalError(referencingModule.realm)

        val sourceInfo = FileSourceInfo(resolvedFile)

        return SourceTextModuleRecord
            .parseModule(referencingModule.realm, sourceInfo)
            .valueOrElse { TODO() }
            .also {
                referencingModule.realm.moduleTree.setImportedModule(referencingModule, specifier, it)
            }
    }
}
