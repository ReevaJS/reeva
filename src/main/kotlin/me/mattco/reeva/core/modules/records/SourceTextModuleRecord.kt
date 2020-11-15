package me.mattco.reeva.core.modules.records

import me.mattco.reeva.Reeva
import me.mattco.reeva.ast.FunctionDeclarationNode
import me.mattco.reeva.ast.ModuleNode
import me.mattco.reeva.ast.ScriptOrModuleNode
import me.mattco.reeva.ast.statements.StatementListNode
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.Realm
import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.ModuleEnvRecord
import me.mattco.reeva.core.modules.ExportEntryRecord
import me.mattco.reeva.core.modules.ImportEntryRecord
import me.mattco.reeva.core.modules.ResolvedBindingRecord
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.jvmcompat.JVMPackageModuleRecord
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.expect

class SourceTextModuleRecord(
    realm: Realm,
    environment: EnvRecord?,
    requestedModules: List<String>,
    val scriptCode: ModuleNode,
    var context: ExecutionContext?,
    val importMeta: JSObject?,
    val importEntries: List<ImportEntryRecord>,
    val localExportEntries: List<ExportEntryRecord>,
    val indirectExportEntries: List<ExportEntryRecord>,
    val starExportEntries: List<ExportEntryRecord>,
) : CyclicModuleRecord(realm, environment, requestedModules) {
    @ECMAImpl("15.2.1.17.2")
    override fun getExportedNames(exportStarSet: MutableSet<ModuleRecord>): List<String> {
        if (this in exportStarSet) {
            // "Assert: We've reached the starting point of an 'export *' circularity."
            return emptyList()
        }

        exportStarSet.add(this)
        val exportedNames = mutableListOf<String>()
        localExportEntries.forEach {
            // TODO?: "Assert: module provides the direct binding for this export."
            exportedNames.add(it.exportName!!)
        }

        indirectExportEntries.forEach {
            // TODO?: "Assert: module imports a specific binding for this export."
            exportedNames.add(it.exportName!!)
        }

        starExportEntries.forEach { e ->
            val requestedModule = realm.moduleResolver!!.hostResolveImportedModule(this, e.moduleRequest!!)
            expect(requestedModule !is JVMPackageModuleRecord)
            requestedModule.getExportedNames(exportStarSet).forEach { name ->
                if (name != "default" && name !in exportedNames)
                    exportedNames.add(name)
            }
        }

        return exportedNames
    }

    @ECMAImpl("15.2.1.17.3")
    override fun resolveExport(exportName: String, resolveSet: MutableList<ResolvedBindingRecord>): ResolvedBindingRecord? {
        resolveSet.forEach {
            if (this == it.module && exportName == it.bindingName) {
                // TODO: Error?
                return null
            }
        }

        resolveSet.add(ResolvedBindingRecord(this, exportName))
        localExportEntries.forEach { e ->
            if (exportName == e.exportName) {
                return ResolvedBindingRecord(this, e.localName!!)
            }
        }

        indirectExportEntries.forEach { e ->
            if (exportName == e.exportName) {
                val importedModule = realm.moduleResolver!!.hostResolveImportedModule(this, e.moduleRequest!!)
                if (e.importName == "*")
                    return ResolvedBindingRecord(importedModule, "*namespace*")

                return importedModule.resolveExport(e.importName!!, resolveSet)
            }
        }

        if (exportName == "default") {
            // TODO: "Assert: A default export was not explicitly defined by this module"
            return null
        }

        var starResolution: ResolvedBindingRecord? = null
        starExportEntries.forEach { e ->
            val importedModule = realm.moduleResolver!!.hostResolveImportedModule(this, e.moduleRequest!!)
            val resolution = importedModule.resolveExport(exportName, resolveSet)
            if (resolution == ResolvedBindingRecord.AMBIGUOUS)
                return resolution
            if (resolution != null) {
                if (starResolution == null) {
                    starResolution = resolution
                } else {
                    // TODO?: "Assert: There is more than one * import that includes the requested name"
                    if (resolution.module != starResolution!!.module || resolution.bindingName != starResolution!!.bindingName)
                        return ResolvedBindingRecord.AMBIGUOUS
                }
            }
        }

        return starResolution
    }

    @ECMAImpl("15.2.1.17.4")
    override fun initializeEnvironment() {
        indirectExportEntries.forEach { e ->
            val resolution = resolveExport(e.exportName!!)
            if (resolution == null || resolution == ResolvedBindingRecord.AMBIGUOUS)
                Errors.TODO("SourceTextModuleRecord initializeEnvironment 1").throwSyntaxError()
        }

        // TODO: "Assert: All named exports from module are resolvable."
        val env = ModuleEnvRecord(realm.globalEnv)
        environment = env

        importEntries.forEach { ie ->
            val importedModule = realm.moduleResolver!!.hostResolveImportedModule(this, ie.moduleRequest)
            if (ie.importName == "*") {
                env.createImmutableBinding(ie.localName, true)
                env.initializeBinding(ie.localName, importedModule.namespaceObject)
            } else {
                val resolution = importedModule.resolveExport(ie.importName)
                if (resolution == null || resolution == ResolvedBindingRecord.AMBIGUOUS)
                    Errors.TODO("SourceTextModuleRecord initializeEnvironment 2").throwSyntaxError()
                if (resolution.bindingName == "*namespace*") {
                    env.createImmutableBinding(ie.localName, true)
                    env.initializeBinding(ie.localName, resolution.module.namespaceObject)
                } else {
                    env.createImportBinding(ie.localName, resolution.module, resolution.bindingName)
                }
            }
        }

        val moduleContext = ExecutionContext(realm, null)
        moduleContext.variableEnv = environment
        moduleContext.lexicalEnv = environment
        context = moduleContext
        Agent.pushContext(moduleContext)

        val varDeclarations = scriptCode.varScopedDeclarations()
        val declaredVarNames = mutableListOf<String>()
        varDeclarations.forEach { decl ->
            decl.boundNames().forEach { name ->
                if (name !in declaredVarNames) {
                    env.createMutableBinding(name, false)
                    env.initializeBinding(name, JSUndefined)
                    declaredVarNames.add(name)
                }
            }
        }

        val interpreter = Interpreter(realm, ScriptOrModuleNode(scriptCode))

        scriptCode.lexicallyScopedDeclarations().forEach { decl ->
            decl.boundNames().forEach { name ->
                if (decl.isConstantDeclaration()) {
                    env.createImmutableBinding(name, true)
                } else {
                    env.createMutableBinding(name, false)
                }

                if (decl is FunctionDeclarationNode) {
                    val function = interpreter.instantiateFunctionObject(decl, env)
                    env.initializeBinding(name, function)
                }
            }
        }

        Agent.popContext()
    }

    override fun executeModule(interpreter: Interpreter): JSValue {
        return Reeva.getAgent().runTask(object : Task<JSValue>() {
            override fun makeContext() = context!!

            override fun execute(): JSValue {
                return interpreter.interpretStatementList(StatementListNode(scriptCode.body))
            }
        })
    }
}
