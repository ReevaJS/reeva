package com.reevajs.reeva.core

import com.reevajs.reeva.Reeva
import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
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
        Reeva.activeAgent.microtaskQueue.addMicrotask(job)
    }

    @ECMAImpl("9.6")
    open fun initializeHostDefinedRealm(): Realm {
        val realm = Realm()
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
        val sourceInfo = resolveImportedModuleImpl(referencingModule, specifier)
        val existingModule = referencingModule.realm.moduleTree.resolveImportedModule(sourceInfo)
        if (existingModule != null)
            return existingModule

        return ModuleRecord.parseModule(referencingModule.realm, sourceInfo).valueOrElse { TODO() }
    }

    open fun resolveImportedModuleImpl(referencingModule: ModuleRecord, specifier: String): SourceInfo {
        val file = resolveImportedFilePath(referencingModule, specifier)
        return FileSourceInfo(file)
    }

    open fun resolveImportedFilePath(referencingModule: ModuleRecord, specifier: String): File {
        val sourceInfo = referencingModule.parsedSource.sourceInfo
        val resolvedFile = sourceInfo.resolveImportedFilePath(specifier)
        if (!resolvedFile.exists())
            Errors.NonExistentImport(specifier, sourceInfo.name).throwInternalError(referencingModule.realm)
        return resolvedFile
    }
}
