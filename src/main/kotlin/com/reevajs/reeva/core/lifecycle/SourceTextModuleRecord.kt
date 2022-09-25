package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.ExportEntry
import com.reevajs.reeva.ast.ImportEntry
import com.reevajs.reeva.ast.ModuleNode
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.interpreter.Interpreter
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.other.JSModuleNamespaceObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect

@ECMAImpl("16.2.1.6")
class SourceTextModuleRecord(realm: Realm, val parsedSource: ParsedSource) : CyclicModuleRecord(realm) {
    private val moduleNode = parsedSource.node as ModuleNode

    override val uri by parsedSource.sourceInfo::uri
    override val requestedModules = moduleNode.requestedModules

    lateinit var context: ExecutionContext

    override fun execute(): JSValue {
        link()
        return evaluate().also {
            if (evaluationError != null)
                throw evaluationError!!
        }
    }

    @ECMAImpl("16.2.1.6.4")
    override fun initializeEnvironment() {
        // 1. For each ExportEntry Record e of module.[[IndirectExportEntries]], do
        for (e in moduleNode.indirectExportEntries) {
            // a. Let resolution be ? module.ResolveExport(e.[[ExportName]]).
            val resolution = resolveExport(e.exportName!!)

            // b. If resolution is null or ambiguous, throw a SyntaxError exception.
            if (resolution !is ResolvedBinding.Record)
                Errors.TODO("SourceTextModuleRecord::initializeEnvironment 1").throwSyntaxError(realm)

            // c. Assert: resolution is a ResolvedBinding Record.
            // Note: This is by definition true
        }

        // 2. Assert: All named exports from module are resolvable.
        // TODO: How to assert this?

        // 3. Let realm be module.[[Realm]].
        // 4. Assert: realm is not undefined.
        // TODO: Why would this be undefined?

        // 5. Let env be NewModuleEnvironment(realm.[[GlobalEnv]]).
        val env = ModuleEnvRecord(realm, realm.globalEnv)

        // 6. Set module.[[Environment]] to env.
        this.environment = env

        // 7. For each ImportEntry Record in of module.[[ImportEntries]], do
        for (ie in moduleNode.importEntries) {
            // a. Let importedModule be ! HostResolveImportedModule(module, in.[[ModuleRequest]]).
            // b. NOTE: The above call cannot fail because imported module requests are a subset of
            //    module.[[RequestedModules]], and these have been resolved earlier in this algorithm.
            val importedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, ie.moduleRequest)

            // c. If in.[[ImportName]] is namespace-object, then
            if (ie.importName == "namespace-object") {
                // i.   Let namespace be ? GetModuleNamespace(importedModule).
                val namespace = importedModule.getModuleNamespace()

                // ii.  Perform ! env.CreateImmutableBinding(in.[[LocalName]], true).
                env.createImmutableBinding(ie.localName, isStrict = true)

                // iii. Perform ! env.InitializeBinding(in.[[LocalName]], namespace).
                env.initializeBinding(ie.localName, namespace)
            }
            // d. Else,
            else {
                // i.   Let resolution be ? importedModule.ResolveExport(in.[[ImportName]]).
                val resolution = importedModule.resolveExport(ie.importName)

                // ii.  If resolution is null or ambiguous, throw a SyntaxError exception.
                if (resolution !is ResolvedBinding.Record)
                    Errors.TODO("SourceTextModuleRecord::initializeEnvironment 2").throwSyntaxError(realm)

                // iii. If resolution.[[BindingName]] is namespace, then
                if (resolution.bindingName == null) {
                    // 1. Let namespace be ? GetModuleNamespace(resolution.[[Module]]).
                    val namespace = resolution.module.getModuleNamespace()

                    // 2. Perform ! env.CreateImmutableBinding(in.[[LocalName]], true).
                    env.createImmutableBinding(ie.localName, isStrict = true)

                    // 3. Perform ! env.InitializeBinding(in.[[LocalName]], namespace).
                    env.initializeBinding(ie.localName, namespace)
                }
                // iv. Else,
                else {
                    // 1. Perform env.CreateImportBinding(in.[[LocalName]], resolution.[[Module]], resolution.[[BindingName]]).
                    env.createImportBinding(ie.localName, resolution.module, resolution.bindingName)
                }
            }
        }

        // TODO: Figure out if all this is necessary. `code` will probably have a DeclareGlobals opcode at the top,
        //       so I doubt it
        // TODO: Why does this push an execution context only to remove it right after?
        // 8. Let moduleContext be a new ECMAScript code execution context.
        // 9. Set the Function of moduleContext to null.
        // 10. Assert: module.[[Realm]] is not undefined.
        // 11. Set the Realm of moduleContext to module.[[Realm]].
        // 12. Set the ScriptOrModule of moduleContext to module.
        // 13. Set the VariableEnvironment of moduleContext to module.[[Environment]].
        // 14. Set the LexicalEnvironment of moduleContext to module.[[Environment]].
        // 15. Set the PrivateEnvironment of moduleContext to null.
        // 16. Set module.[[Context]] to moduleContext.
        // 17. Push moduleContext onto the execution context stack; moduleContext is now the running execution context.
        // 18. Let code be module.[[ECMAScriptCode]].
        // 19. Let varDeclarations be the VarScopedDeclarations of code.
        // 20. Let declaredVarNames be a new empty List.
        // 21. For each element d of varDeclarations, do
        //     a. For each element dn of the BoundNames of d, do
        //        i. If dn is not an element of declaredVarNames, then
        //           1. Perform ! env.CreateMutableBinding(dn, false).
        //           2. Perform ! env.InitializeBinding(dn, undefined).
        //           3. Append dn to declaredVarNames.
        // 22. Let lexDeclarations be the LexicallyScopedDeclarations of code.
        // 23. Let privateEnv be null.
        // 24. For each element d of lexDeclarations, do
        //     a. For each element dn of the BoundNames of d, do
        //        i.   If IsConstantDeclaration of d is true, then
        //             1. Perform ! env.CreateImmutableBinding(dn, true).
        //        ii.  Else,
        //             1. Perform ! env.CreateMutableBinding(dn, false).
        //        iii. If d is a FunctionDeclaration, a GeneratorDeclaration, an AsyncFunctionDeclaration, or an AsyncGeneratorDeclaration, then
        //             1. Let fo be InstantiateFunctionObject of d with arguments env and privateEnv.
        //             2. Perform ! env.InitializeBinding(dn, fo).
        // 25. Remove moduleContext from the execution context stack.
        // 26. Return unused.
    }

    override fun resolveExport(
        exportName: String,
        resolveSet: MutableList<ResolvedBinding>,
    ): ResolvedBinding {
        // 1. If resolveSet is not present, set resolveSet to a new empty List.
        // 2. For each Record { [[Module]], [[ExportName]] } r of resolveSet, do
        for (binding in resolveSet) {
            // a. If module and r.[[Module]] are the same Module Record and SameValue(exportName, r.[[ExportName]]) is
            //    true, then
            if (binding is ResolvedBinding.Record && binding.module === this && binding.bindingName == exportName) {
                // i.  Assert: This is a circular import request.
                // ii. Return null.
                return ResolvedBinding.Null
            }
        }

        // 3. Append the Record { [[Module]]: module, [[ExportName]]: exportName } to resolveSet.
        resolveSet.add(ResolvedBinding.Record(this, exportName))

        // 4. For each ExportEntry Record e of module.[[LocalExportEntries]], do
        for (e in moduleNode.localExportEntries) {
            // a. If SameValue(exportName, e.[[ExportName]]) is true, then
            if (exportName == e.exportName) {
                // i.  Assert: module provides the direct binding for this export.
                // ii. Return ResolvedBinding Record { [[Module]]: module, [[BindingName]]: e.[[LocalName]] }.
                return ResolvedBinding.Record(this, e.localName)
            }
        }

        // 5. For each ExportEntry Record e of module.[[IndirectExportEntries]], do
        for (e in moduleNode.indirectExportEntries) {
            // a. If SameValue(exportName, e.[[ExportName]]) is true, then
            if (exportName == e.exportName) {
                // i.   Let importedModule be ? HostResolveImportedModule(module, e.[[ModuleRequest]]).
                val importedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, e.moduleRequest!!)

                // ii.  If e.[[ImportName]] is all, then
                return if (e.importName == ExportEntry.AllImportName) {
                    // 1. Assert: module does not provide the direct binding for this export.
                    // 2. Return ResolvedBinding Record { [[Module]]: importedModule, [[BindingName]]: namespace }.
                    ResolvedBinding.Record(importedModule, null)
                }
                // iii.  Else,
                else {
                    // 1. Assert: module imports a specific binding for this export.
                    // 2. Return ? importedModule.ResolveExport(e.[[ImportName]], resolveSet).
                    importedModule.resolveExport((e.importName as ExportEntry.StringImportName).name, resolveSet)
                }
            }
        }

        // 6. If SameValue(exportName, "default") is true, then
        if (exportName == "default") {
            // a. Assert: A default export was not explicitly defined by this module.
            // b. Return null.
            // c. NOTE: A default export cannot be provided by an export * from "mod" declaration.
            return ResolvedBinding.Null
        }

        // 7. Let starResolution be null.
        var starResolution: ResolvedBinding = ResolvedBinding.Null

        // 8. For each ExportEntry Record e of module.[[StarExportEntries]], do
        for (e in moduleNode.starExportEntries) {
            // a. Let importedModule be ? HostResolveImportedModule(module, e.[[ModuleRequest]]).
            val importedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, e.moduleRequest!!)

            // b. Let resolution be ? importedModule.ResolveExport(exportName, resolveSet).
            val resolution = importedModule.resolveExport(exportName, resolveSet)

            // c. If resolution is ambiguous, return ambiguous.
            if (resolution == ResolvedBinding.Ambiguous)
                return ResolvedBinding.Ambiguous

            // d. If resolution is not null, then
            if (resolution != ResolvedBinding.Null) {
                // i.   Assert: resolution is a ResolvedBinding Record.
                ecmaAssert(resolution is ResolvedBinding.Record)

                // ii.  If starResolution is null, set starResolution to resolution.
                if (starResolution == ResolvedBinding.Null) {
                    starResolution = resolution
                }
                // iii. Else,
                else {
                    // starResolution cannot be ambiguous, therefore it must be Record
                    expect(starResolution is ResolvedBinding.Record)

                    // 1. Assert: There is more than one * import that includes the requested name.

                    // 2. If resolution.[[Module]] and starResolution.[[Module]] are not the same Module Record, return
                    //    ambiguous.
                    if (resolution.module !== starResolution.module)
                        return ResolvedBinding.Ambiguous

                    // 3. If resolution.[[BindingName]] is namespace and starResolution.[[BindingName]] is not
                    //    namespace, or if resolution.[[BindingName]] is not namespace and
                    //    starResolution.[[BindingName]] is namespace, return ambiguous.
                    if ((resolution.bindingName == null) != (starResolution.bindingName == null))
                        return ResolvedBinding.Ambiguous

                    // 4. If resolution.[[BindingName]] is a String, starResolution.[[BindingName]] is a String, and
                    //    SameValue(resolution.[[BindingName]], starResolution.[[BindingName]]) is false, return
                    //    ambiguous.
                    if (resolution.bindingName != null && resolution.bindingName != starResolution.bindingName)
                        return ResolvedBinding.Ambiguous
                }
            }
        }

        // 9. Return starResolution.
        return starResolution
    }

    override fun getImportedNames(specifier: String): Set<String> {
        return moduleNode.importEntries.filter {
            it.moduleRequest == specifier
        }.map(ImportEntry::importName).toSet()
    }

    @ECMAImpl("16.2.1.6.2")
    override fun getExportedNames(exportStarSet: MutableSet<SourceTextModuleRecord>): List<String> {
        // 1. If exportStarSet is not present, set exportStarSet to a new empty List.

        // 2. If exportStarSet contains module, then
        if (this in exportStarSet) {
            // a. Assert: We've reached the starting point of an export * circularity.
            // b. Return a new empty List.
            return emptyList()
        }

        // 3. Append module to exportStarSet.
        exportStarSet.add(this)

        // 4. Let exportedNames be a new empty List.
        val exportedNames = mutableListOf<String>()

        // 5. For each ExportEntry Record e of module.[[LocalExportEntries]], do
        for (e in moduleNode.localExportEntries) {
            // a. Assert: module provides the direct binding for this export.
            // b. Append e.[[ExportName]] to exportedNames.
            exportedNames.add(e.exportName!!)
        }

        // 6. For each ExportEntry Record e of module.[[IndirectExportEntries]], do
        for (e in moduleNode.indirectExportEntries) {
            // a. Assert: module imports a specific binding for this export.
            // b. Append e.[[ExportName]] to exportedNames.
            exportedNames.add(e.exportName!!)
        }

        // 7. For each ExportEntry Record e of module.[[StarExportEntries]], do
        for (e in moduleNode.starExportEntries) {
            // a. Let requestedModule be ? HostResolveImportedModule(module, e.[[ModuleRequest]]).
            val requestedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, e.moduleRequest!!)

            // b. Let starNames be ? requestedModule.GetExportedNames(exportStarSet).
            val starNames = requestedModule.getExportedNames(exportStarSet)

            // c. For each element n of starNames, do
            for (n in starNames) {
                // i. If SameValue(n, "default") is false, then
                //    1. If n is not an element of exportedNames, then
                if (n == "default" && n !in exportedNames) {
                    // a. Append n to exportedNames.
                    exportedNames.add(n)
                }
            }
        }

        // 8. Return exportedNames.
        return exportedNames
    }

    override fun makeNamespaceImport(exports: List<String>) = JSModuleNamespaceObject.create(this, exports, realm)

    override fun executeModule() {
        val sourceInfo = parsedSource.sourceInfo
        expect(sourceInfo.isModule)
        val transformedSource = Executable.transform(parsedSource)

        val agent = Agent.activeAgent
        val context = ExecutionContext(realm, envRecord = environment, executable = this)
        agent.pushExecutionContext(context)

        try {
            Interpreter(transformedSource, listOf(JSUndefined, JSUndefined)).interpret()
        } finally {
            agent.popExecutionContext()
        }
    }

    companion object {
        fun parseModule(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, ModuleRecord> {
            return Parser(sourceInfo).parseModule().mapValue { result ->
                SourceTextModuleRecord(realm, result)
            }
        }
    }
}
