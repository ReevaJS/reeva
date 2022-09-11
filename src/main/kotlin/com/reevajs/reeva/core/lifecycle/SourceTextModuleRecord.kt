package com.reevajs.reeva.core.lifecycle

import com.reevajs.reeva.ast.*
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.interpreter.NormalInterpretedFunction
import com.reevajs.reeva.parsing.ParsedSource
import com.reevajs.reeva.parsing.Parser
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.other.JSModuleNamespaceObject
import com.reevajs.reeva.runtime.primitives.JSEmpty
import com.reevajs.reeva.utils.Result
import com.reevajs.reeva.utils.expect

@ECMAImpl("16.2.1.6")
class SourceTextModuleRecord(realm: Realm, val parsedSource: ParsedSource) : CyclicModuleRecord(realm) {
    override val uri by parsedSource.sourceInfo::uri

    override val requestedModules = parsedSource.node.let {
        expect(it is ModuleNode)
        it.requestedModules()
    }

    override fun execute(): JSValue {
        link()
        return Agent.activeAgent.withRealm(realm, realm.globalEnv) {
            evaluate().also {
                if (evaluationError != null)
                    throw evaluationError!!
            }
        }
    }

    override fun initializeEnvironment() {
        environment = ModuleEnvRecord(realm, realm.globalEnv)

        val node = parsedSource.node as ModuleNode

        // For each import, we have to tie the identifier to the module which it comes
        // from.
        node.body.filterIsInstance<ImportDeclarationNode>().forEach { decl ->
            val requestedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, decl.moduleName)

            // Note that we have to store the ModuleRecord in the indirect binding
            // instead of directly storing the ModuleEnvRecord. This is because the
            // requestedModule's env field may not have been initialized due to
            // circular (but legal) imports.
            decl.imports?.forEach {
                when (it) {
                    is DefaultImport ->
                        environment.createImportBinding(DEFAULT_SPECIFIER, requestedModule, DEFAULT_SPECIFIER)
                    is NamespaceImport -> {
                        environment.createImmutableBinding(NAMESPACE_SPECIFIER, isStrict = true)
                        environment.initializeBinding(
                            NAMESPACE_SPECIFIER,
                            requestedModule.getNamespaceObject(),
                        )
                    }
                    is NormalImport -> environment.createImportBinding(
                        it.identifierNode.processedName,
                        requestedModule,
                        it.alias.processedName,
                    )
                }
            }
        }

        // For each export, we need to resolve the specifier and set the appropriate
        // binding in the ModuleEnvRecord to JSEmpty. JSEmpty is used as a placeholder
        // for a binding that has not been initialized. If the runtime ever encounters
        // an import which is JSEmpty, it is a sign of circularly-dependent imports,
        // and an error is thrown accordingly.
        node.body.filterIsInstance<ExportNode>().forEach { export ->
            when (export) {
                is DefaultClassExportNode,
                is DefaultExpressionExportNode,
                is DefaultFunctionExportNode -> {
                    environment.createImmutableBinding(DEFAULT_SPECIFIER, isStrict = true)
                    environment.initializeBinding(DEFAULT_SPECIFIER, JSEmpty)
                }
                is NamedExport -> {
                    val name = export.alias?.processedName ?: export.identifierNode.processedName
                    environment.createImmutableBinding(name, isStrict = true)
                    environment.initializeBinding(name, JSEmpty)
                }
                is NamedExports -> export.exports.forEach {
                    val name = it.alias?.processedName ?: it.identifierNode.processedName
                    environment.createImmutableBinding(name, isStrict = true)
                    environment.initializeBinding(name, JSEmpty)
                }
                is DeclarationExportNode -> export.declaration.declarations.flatMap { it.names() }.forEach {
                    environment.createImmutableBinding(it, isStrict = true)
                    environment.initializeBinding(it, JSEmpty)
                }
                is ExportFromNode -> { /* handled in next loop */ }
            }
        }

        // Export from nodes are a bit special. Since they simply re-export values from a
        // different modules, they're treated more like imports. The environment is initialized
        // with indirect imports for each export from node.
        node.body.filterIsInstance<ExportFromNode>().forEach { export ->
            val requestedModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, export.moduleName)

            when (export) {
                is ExportAllAsFromNode -> environment.setMutableBinding(
                    export.identifierNode.processedName,
                    requestedModule.getNamespaceObject(),
                    isStrict = true,
                )
                is ExportAllFromNode -> requestedModule.getExportedNames().forEach {
                    environment.createImportBinding(it, requestedModule, it)
                }
                is ExportNamedFromNode -> export.exports.exports.forEach {
                    environment.createImportBinding(
                        it.alias?.processedName ?: it.identifierNode.processedName,
                        requestedModule,
                        it.identifierNode.processedName,
                    )
                }
            }
        }
    }

    override fun getImportedNames(specifier: String): Set<String> {
        val node = parsedSource.node as ModuleNode
        val modules = node.body.filterIsInstance<ImportDeclarationNode>().filter {
            it.moduleName == specifier
        }

        return modules.flatMap { module ->
            module.imports?.map {
                when (it) {
                    is DefaultImport -> DEFAULT_SPECIFIER
                    is NamespaceImport -> NAMESPACE_SPECIFIER
                    is NormalImport -> it.identifierNode.processedName
                }
            } ?: emptyList()
        }.toSet()
    }

    override fun getExportedNames(exportStarSet: MutableSet<SourceTextModuleRecord>): List<String> {
        if (this in exportStarSet)
            return emptyList()

        exportStarSet.add(this)

        val node = parsedSource.node as ModuleNode
        val names = mutableListOf<String>()

        node.body.filterIsInstance<ExportNode>().forEach { export ->
            when (export) {
                is DefaultClassExportNode,
                is DefaultExpressionExportNode,
                is DefaultFunctionExportNode -> names.add(DEFAULT_SPECIFIER)
                is ExportAllAsFromNode -> names.add(export.identifierNode.processedName)
                is ExportAllFromNode -> {
                    val requiredModule = Agent.activeAgent.hostHooks.resolveImportedModule(this, export.moduleName)
                    if (requiredModule === this) {
                        // This export declaration won't introduce any other name bindings, as
                        // they'll all already exist in an export declaration in the same module
                    } else {
                        names.addAll(requiredModule.getExportedNames(exportStarSet))
                    }
                }
                is ExportNamedFromNode -> names.addAll(export.exports.exports.map {
                    it.alias?.processedName ?: it.identifierNode.processedName
                })
                is NamedExport -> names.add(export.alias?.processedName ?: export.identifierNode.processedName)
                is NamedExports -> names.addAll(export.exports.map {
                    it.alias?.processedName ?: it.identifierNode.processedName
                })
                is DeclarationExportNode -> names.addAll(export.declaration.declarations.flatMap { it.names() })
            }
        }

        return names
    }

    override fun makeNamespaceImport() = JSModuleNamespaceObject.create(this, getExportedNames(), realm)

    override fun executeModule() {
        val sourceInfo = parsedSource.sourceInfo
        expect(sourceInfo.isModule)
        val transformedSource = Executable.transform(parsedSource)

        val agent = Agent.activeAgent
        val context = ExecutionContext(null, realm, environment, this, null)
        agent.pushExecutionContext(context)

        try {
            val function = NormalInterpretedFunction.create(transformedSource)
            Operations.call(function, realm.globalObject, emptyList())
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
