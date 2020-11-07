package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.literals.StringLiteralNode
import me.mattco.reeva.ast.statements.VariableStatementNode
import me.mattco.reeva.core.modules.ExportEntryRecord
import me.mattco.reeva.core.modules.ImportEntryRecord
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.newline
import me.mattco.reeva.utils.unreachable

class ModuleNode(val body: List<StatementListItemNode>) : NodeBase(body) {
    override fun containsDuplicateLabels(labelSet: Set<String>) = body.any { it.containsDuplicateLabels(labelSet) }

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = body.any { it.containsUndefinedBreakTarget(labelSet) }

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) =
        body.any { it.containsUndefinedContinueTarget(iterationSet, labelSet) }

    override fun exportedBindings() = body.flatMap(ASTNode::exportedBindings)

    override fun exportedNames() = body.flatMap(ASTNode::exportedNames)

    override fun exportEntries() = body.flatMap(ASTNode::exportEntries)

    override fun importEntries() = body.flatMap(ASTNode::importEntries)

    override fun lexicallyDeclaredNames() = body.flatMap(ASTNode::lexicallyDeclaredNames)

    override fun lexicallyScopedDeclarations() = body.flatMap(ASTNode::lexicallyScopedDeclarations)

    override fun moduleRequests() = body.flatMap(ASTNode::moduleRequests)

    override fun varDeclaredNames() = body.flatMap(ASTNode::varDeclaredNames)

    override fun varScopedDeclarations() = body.flatMap(ASTNode::varScopedDeclarations)
}

class ImportDeclarationNode(
    val first: ASTNode, // ImportClause if fromClause is non-null, StringLiteralNode otherwise
    val fromClause: StringLiteralNode?
) : NodeBase(listOfNotNull(first, fromClause)), StatementNode {
    override fun boundNames(): List<String> {
        if (fromClause == null)
            return emptyList()

        return (first as ImportClause).boundNames()
    }

    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun importEntries(): List<ImportEntryRecord> {
        if (fromClause == null)
            return emptyList()

        expect(first is ImportClause)
        return first.imports.filter {
            it.type != Import.Type.OnlyFile
        }.map { it.makeImportEntry(fromClause.value) }
    }

    override fun moduleRequests(): List<String> {
        if (fromClause == null)
            return emptyList()
        return listOf(fromClause.value)
    }

    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        append(first.dump(indent + 1))
        fromClause?.dump(indent + 1)?.also(::append)
    }
}

class ImportClause(val imports: List<Import>) : NodeBase(imports) {
    override fun boundNames() = imports.flatMap(ASTNode::boundNames)
}

class Import(
    val importName: String?,
    val localName: String?,
    val type: Type,
) : NodeBase() {
    init {
        when (type) {
            Type.Normal -> expect(importName != null && localName == null)
            Type.NormalAliased -> expect(importName != null && localName != null)
            Type.Default -> expect(importName == null && localName != null)
            Type.Namespace -> expect(importName == null && localName != null)
            Type.OnlyFile -> expect(importName == null && localName == null)
        }
    }

    override fun boundNames(): List<String> {
        return when (type) {
            Type.Normal -> listOf(importName!!)
            Type.NormalAliased -> listOf(localName!!)
            Type.Default, Type.Namespace, Type.OnlyFile -> emptyList()
        }
    }

    fun makeImportEntry(module: String): ImportEntryRecord {
        return when (type) {
            Type.Normal -> ImportEntryRecord(module, importName!!, importName)
            Type.NormalAliased -> ImportEntryRecord(module, importName!!, localName!!)
            Type.Default -> ImportEntryRecord(module, "default", localName!!)
            Type.Namespace -> ImportEntryRecord(module, "*", localName!!)
            Type.OnlyFile -> unreachable()
        }
    }

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (type=")
        append(type.name)
        append(")\n")
        if (importName != null) {
            appendIndent(indent + 1)
            append("importName=")
            append(importName)
            newline()
        }
        if (localName != null) {
            appendIndent(indent + 1)
            append("localName=")
            append(localName)
            newline()
        }
    }

    enum class Type {
        Normal,
        NormalAliased,
        Default,
        Namespace,
        OnlyFile,
    }
}

interface ExportDeclarationNode : StatementNode

class FromExport(
    val fromClause: StringLiteralNode,
    val node: ASTNode?, // Null if Wildcard, IdentifierNode if NamedWildcard, NamedExports if NamedList
    val type: Type,
) : NodeBase(listOfNotNull(fromClause, node)), ExportDeclarationNode {
    override fun boundNames() = emptyList<String>()

    override fun exportedBindings() = emptyList<String>()

    override fun exportedNames() = when (type) {
        Type.Wildcard -> emptyList()
        Type.NamedWildcard -> listOf((node as IdentifierNode).identifierName)
        Type.NamedList -> (node as NamedExports).exportedNames()
    }

    override fun exportEntries() = when (type) {
        Type.Wildcard -> listOf(ExportEntryRecord(fromClause.value, null, "*", null))
        Type.NamedWildcard -> listOf(ExportEntryRecord(fromClause.value, (node as IdentifierNode).identifierName, "*", null))
        Type.NamedList -> {
            (node as NamedExports).exports.map {
                ExportEntryRecord(fromClause.value, it.exportName ?: it.localName, it.localName, null)
            }
        }
    }

    override fun isConstantDeclaration() = false

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = emptyList<NodeBase>()

    override fun moduleRequests() = listOf(fromClause.value)

    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        appendIndent(indent + 1)
        append("from: ")
        append(fromClause.value)
        newline()
        node?.dump(indent + 1)?.also(::append)
    }

    enum class Type {
        Wildcard,
        NamedWildcard,
        NamedList,
    }
}

class NamedExports(val exports: List<Export>) : NodeBase(listOf()), ExportDeclarationNode {
    override fun boundNames() = emptyList<String>()

    override fun exportedBindings() = exports.map { it.localName }

    override fun exportedNames() = exports.map { it.exportName ?: it.localName }

    override fun exportEntries() = exports.map {
        ExportEntryRecord(null, it.exportName ?: it.localName, null, it.localName)
    }

    override fun isConstantDeclaration() = false

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = emptyList<NodeBase>()

    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        exports.forEach {
            appendIndent(indent + 1)
            append("NamedExport (localName=")
            append(it.localName)
            if (it.exportName != null) {
                append(", exportName=")
                append(it.exportName)
            }
            append(")\n")
        }
    }

    data class Export(val localName: String, val exportName: String?)
}

class VariableExport(val variableStatement: VariableStatementNode) : NodeBase(listOf(variableStatement)), ExportDeclarationNode {
    override fun boundNames() = variableStatement.boundNames()

    override fun exportedBindings() = boundNames()

    override fun exportedNames() = boundNames()

    override fun exportEntries() = variableStatement.boundNames().map {
        ExportEntryRecord(null, it, null, it)
    }

    override fun lexicallyDeclaredNames() = emptyList<String>()

    override fun lexicallyScopedDeclarations() = emptyList<NodeBase>()

    override fun varDeclaredNames() = variableStatement.varDeclaredNames()

    override fun varScopedDeclarations() = variableStatement.varScopedDeclarations()
}

class DeclarationExport(val declaration: DeclarationNode) : NodeBase(listOf(declaration)), ExportDeclarationNode {
    override fun boundNames() = declaration.boundNames()

    override fun exportedBindings() = boundNames()

    override fun exportedNames() = boundNames()

    override fun exportEntries() = declaration.boundNames().map {
        ExportEntryRecord(null, it, null, it)
    }

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(declaration.declarationPart())
}

class DefaultFunctionExport(val declaration: FunctionDeclarationNode) : NodeBase(listOf(declaration)), ExportDeclarationNode {
    override fun boundNames(): List<String> {
        val declarationNames = declaration.boundNames()
        if ("*default*" !in declarationNames)
            return declarationNames + listOf("*default*")
        return declarationNames
    }

    override fun exportedBindings() = boundNames()

    override fun exportedNames() = listOf("default")

    override fun exportEntries() = declaration.boundNames().map {
        ExportEntryRecord(null, "default", null, it)
    }.also {
        expect(it.size == 1)
    }

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(declaration)
}

class DefaultClassExport(val classNode: ClassDeclarationNode) : NodeBase(listOf(classNode)), ExportDeclarationNode {
    override fun boundNames(): List<String> {
        val declarationNames = classNode.boundNames()
        if ("*default*" !in declarationNames)
            return declarationNames + listOf("*default*")
        return declarationNames
    }

    override fun exportedBindings() = boundNames()

    override fun exportedNames() = listOf("default")

    override fun exportEntries() = classNode.boundNames().map {
        ExportEntryRecord(null, "default", null, it)
    }.also {
        expect(it.size == 1)
    }

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(classNode)
}

class DefaultExpressionExport(val expression: ExpressionNode) : NodeBase(listOf(expression)), ExportDeclarationNode {
    override fun boundNames() = listOf("*default*")

    override fun exportedBindings() = boundNames()

    override fun exportedNames() = listOf("default")

    override fun exportEntries() = listOf(ExportEntryRecord(null, "default", null, "*default*"))

    override fun isConstantDeclaration() = false

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(this as NodeBase)
}
