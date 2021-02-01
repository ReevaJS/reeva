package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.literals.StringLiteralNode
import me.mattco.reeva.ast.statements.LexicalDeclarationNode
import me.mattco.reeva.ast.statements.StatementList
import me.mattco.reeva.ast.statements.VariableDeclarationNode
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.newline

class ModuleNode(val body: StatementList) : GlobalScopeNode(body)

// TODO: Make this a VariableSourceNode
class ImportDeclarationNode(
    val imports: List<Import>,
    val fromClause: StringLiteralNode?,
) : ASTNodeBase(imports + listOfNotNull(fromClause)), StatementNode {
    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        imports.forEach {
            append(it.dump(indent + 1))
        }
        fromClause?.dump(indent + 1)?.also(::append)
    }
}

class Import(val importName: String?, val localName: String?, val type: Type) : ASTNodeBase() {
    init {
        when (type) {
            Type.Normal -> expect(importName != null && localName == null)
            Type.NormalAliased -> expect(importName != null && localName != null)
            Type.Default -> expect(importName == null && localName != null)
            Type.Namespace -> expect(importName == null && localName != null)
            Type.OnlyFile -> expect(importName == null && localName == null)
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

sealed class ExportNode(children: List<ASTNode>) : ASTNodeBase(children), StatementNode

class FromExportNode(
    val fromClause: StringLiteralNode,
    val node: ASTNode?, // Null if Wildcard, IdentifierNode if NamedWildcard, NamedExports if NamedList
    val type: Type,
) : ExportNode(listOfNotNull(fromClause, node)) {
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

class NamedExports(exports: List<NamedExport>) : ExportNode(emptyList())

data class NamedExport(val localName: String, val exportName: String?)

class DeclarationExportNode(val declaration: StatementNode) : ExportNode(listOf(declaration))

class DefaultFunctionExportNode(val declaration: FunctionDeclarationNode) : ExportNode(listOf(declaration))

class DefaultClassExportNode(val classNode: ClassDeclarationNode) : ExportNode(listOf(classNode))

class DefaultExpressionExportNode(val expression: ExpressionNode) : ExportNode(listOf(expression))
