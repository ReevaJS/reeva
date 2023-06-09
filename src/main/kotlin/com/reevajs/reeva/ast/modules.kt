package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.core.lifecycle.ModuleRecord
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect

data class ImportEntry(
    val moduleRequest: String,
    val importName: String,
    val localName: String,
)

data class ExportEntry(
    val exportName: String?,
    val moduleRequest: String?,
    val importName: ImportName?,
    val localName: String?,
) {
    sealed class ImportName

    class StringImportName(val name: String) : ImportName()
    object AllImportName : ImportName()
    object AllButDefaultImportName : ImportName()
}

class ModuleNode(val body: List<AstNode>, sourceLocation: SourceLocation) : RootNode(sourceLocation) {
    override val children get() = body

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    val importEntries: List<ImportEntry>
    val localExportEntries: List<ExportEntry>
    val indirectExportEntries: List<ExportEntry>
    val starExportEntries: List<ExportEntry>
    val requestedModules: List<String>

    // This partially implements the algorithm described in 16.2.1.6.1
    init {
        val importEntries = body.filterIsInstance<ImportNode>().flatMap { it.importEntries }
        val exportEntries = body.filterIsInstance<ExportNode>().flatMap { it.exportEntries }

        // 1. Let body be ParseText(sourceText, Module).
        // 2. If body is a List of errors, return body.
        // 3. Let requestedModules be the ModuleRequests of body.
        val requestedModules = importEntries.map(ImportEntry::moduleRequest) +
            exportEntries.mapNotNull(ExportEntry::moduleRequest)

        // 4. Let importEntries be ImportEntries of body.
        // 5. Let importedBoundNames be ImportedLocalNames(importEntries).
        val importedBoundNames = importEntries.map(ImportEntry::localName).toSet()

        // 6. Let indirectExportEntries be a new empty List.
        val indirectExportEntries = mutableListOf<ExportEntry>()

        // 7. Let localExportEntries be a new empty List.
        val localExportEntries = mutableListOf<ExportEntry>()

        // 8. Let starExportEntries be a new empty List.
        val starExportEntries = mutableListOf<ExportEntry>()

        // 9. Let exportEntries be ExportEntries of body.

        // 10. For each ExportEntry Record ee of exportEntries, do
        for (ee in exportEntries) {
            // a. If ee.[[ModuleRequest]] is null, then
            if (ee.moduleRequest == null) {
                // i. If ee.[[LocalName]] is not an element of importedBoundNames, then
                if (ee.localName !in importedBoundNames) {
                    // 1. Append ee to localExportEntries.
                    localExportEntries.add(ee)
                }
                // ii. Else,
                else {
                    // 1. Let ie be the element of importEntries whose [[LocalName]] is the same as ee.[[LocalName]].
                    val ie = importEntries.first { it.localName == ee.localName }

                    // 2. If ie.[[ImportName]] is namespace-object, then
                    if (ie.importName == "namespace-object") {
                        // a. NOTE: This is a re-export of an imported module namespace object.
                        // b. Append ee to localExportEntries.
                        localExportEntries.add(ee)
                    }
                    // 3. Else,
                    else {
                        // a. NOTE: This is a re-export of a single name.
                        // b. Append the ExportEntry Record { [[ModuleRequest]]: ie.[[ModuleRequest]], [[ImportName]]:
                        //    ie.[[ImportName]], [[LocalName]]: null, [[ExportName]]: ee.[[ExportName]] } to
                        //    indirectExportEntries.
                        indirectExportEntries.add(
                            ExportEntry(
                                ee.exportName,
                                ie.moduleRequest,
                                ExportEntry.StringImportName(ie.importName),
                                null,
                            )
                        )
                    }
                }
            }
            // b. Else if ee.[[ImportName]] is all-but-default, then
            else if (ee.importName == ExportEntry.AllButDefaultImportName) {
                // i. Assert: ee.[[ExportName]] is null.
                ecmaAssert(ee.exportName == null)

                // ii. Append ee to starExportEntries.
                starExportEntries.add(ee)
            }
            // c. Else,
            else {
                // i. Append ee to indirectExportEntries.
                indirectExportEntries.add(ee)
            }
        }

        this.importEntries = importEntries
        this.localExportEntries = localExportEntries
        this.indirectExportEntries = indirectExportEntries
        this.starExportEntries = starExportEntries
        this.requestedModules = requestedModules.distinct()
    }
}

class ImportNode(
    val imports: List<Import>,
    val moduleName: String,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = imports

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    val importEntries: List<ImportEntry>
        get() = imports.map { it.makeEntry(moduleName) }

    constructor(
        import: Import,
        moduleName: String,
        sourceLocation: SourceLocation,
    ) : this(listOf(import), moduleName, sourceLocation)

    constructor(
        moduleName: String,
        sourceLocation: SourceLocation,
    ) : this(emptyList(), moduleName, sourceLocation)
}

sealed class Import(sourceLocation: SourceLocation) : VariableSourceNode(sourceLocation) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    abstract fun sourceModuleName(): String

    abstract fun makeEntry(moduleName: String): ImportEntry

    class Namespace(val identifier: IdentifierNode, sourceLocation: SourceLocation) : Import(sourceLocation) {
        override val children get() = listOf(identifier)

        override fun name() = identifier.processedName

        override fun sourceModuleName() = ModuleRecord.NAMESPACE_SPECIFIER

        override fun makeEntry(moduleName: String) =
            ImportEntry(moduleName, "namespace-object", identifier.processedName)
    }

    class Named(
        val importIdent: IdentifierNode,
        val localIdent: IdentifierNode = importIdent,
        sourceLocation: SourceLocation,
    ) : Import(sourceLocation) {
        override val children get() = listOf(importIdent, localIdent).distinct()

        override fun name() = localIdent.processedName

        override fun sourceModuleName() = importIdent.processedName

        override fun makeEntry(moduleName: String) =
            ImportEntry(moduleName, importIdent.processedName, localIdent.processedName)
    }

    class Default(val identifier: IdentifierNode) : Import(identifier.sourceLocation) {
        override val children get() = listOf(identifier)

        override fun name() = identifier.processedName

        override fun sourceModuleName() = "default"

        override fun makeEntry(moduleName: String) = ImportEntry(moduleName, "default", identifier.processedName)
    }
}

class ExportNode(val exports: List<Export>, sourceLocation: SourceLocation) : AstNodeBase(sourceLocation) {
    override val children get() = exports

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    val exportEntries: List<ExportEntry>
        get() = exports.flatMap { it.makeEntries() }

    constructor(export: Export, sourceLocation: SourceLocation) : this(listOf(export), sourceLocation)
}

sealed class Export(sourceLocation: SourceLocation) : AstNodeBase(sourceLocation) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    abstract fun makeEntries(): List<ExportEntry>

    class Named(
        val exportIdent: IdentifierNode,
        val localIdent: IdentifierNode = exportIdent,
        var moduleName: String? = null,
        sourceLocation: SourceLocation,
    ) : Export(sourceLocation) {
        override val children get() = listOf(exportIdent, localIdent).distinct()

        override fun makeEntries() = listOf(
            ExportEntry(
                exportIdent.processedName,
                moduleName,
                if (moduleName != null) ExportEntry.StringImportName(localIdent.processedName) else null,
                if (moduleName == null) localIdent.processedName else null,
            )
        )
    }

    class Namespace(
        val alias: IdentifierNode?,
        val moduleName: String?,
        sourceLocation: SourceLocation,
    ) : Export(sourceLocation) {
        override val children get() = listOfNotNull(alias)

        override fun makeEntries() = listOf(
            ExportEntry(
                alias?.processedName,
                moduleName,
                if (alias == null) ExportEntry.AllButDefaultImportName else ExportEntry.AllImportName,
                null,
            )
        )
    }

    class Node(val node: DeclarationNode, val default: Boolean) : Export(node.sourceLocation) {
        override val children get() = listOf(node)

        override fun makeEntries(): List<ExportEntry> {
            return node.declarations.flatMap { source ->
                source.names().map {
                    ExportEntry(
                        if (default) "default" else it,
                        null,
                        null,
                        if (it != null) it else { // TODO: .name() should return String?
                            expect(default)
                            "*default"
                        }
                    )
                }
            }
        }
    }

    class Expr(val expr: AstNode) : Export(expr.sourceLocation) {
        override val children get() = listOf(expr)

        override fun makeEntries() = listOf(
            ExportEntry(
                "default",
                null,
                null,
                "*default",
            )
        )
    }
}
