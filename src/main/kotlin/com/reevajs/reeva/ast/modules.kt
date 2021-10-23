package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.statements.ASTListNode

typealias ImportList = ASTListNode<Import>

class ImportDeclarationNode(
    val imports: ImportList?, // null indicates `import 'file'` syntax
    val moduleName: String,
) : ASTNodeBase(imports ?: emptyList()), StatementNode {
    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        imports?.forEach {
            append(it.dump(indent + 1))
        }
    }
}

sealed class Import(children: List<ASTNode>) : VariableSourceNode(children)

class NormalImport(val identifierNode: IdentifierNode) : Import(listOf(identifierNode)) {
    override fun name() = identifierNode.processedName
}

class AliasedImport(
    val identifierNode: IdentifierNode,
    val alias: IdentifierNode,
) : Import(listOf(identifierNode, alias)) {
    override fun name() = alias.processedName
}

class DefaultImport(val identifierNode: IdentifierNode) : Import(listOf(identifierNode)) {
    override fun name() = identifierNode.processedName
}

class NamespaceImport(val identifierNode: IdentifierNode) : Import(listOf(identifierNode)) {
    override fun name() = identifierNode.processedName
}

sealed class ExportNode(children: List<ASTNode>) : ASTNodeBase(children), StatementNode

sealed class ExportFromNode(children: List<ASTNode>) : ExportNode(children)

object ExportAllFromNode : ExportFromNode(emptyList())

class ExportAllAsFromNode(val identifierNode: IdentifierNode) : ExportFromNode(listOf(identifierNode))

class ExportNamedFromNode(val exports: List<NamedExport>) : ExportFromNode(exports)

class NamedExport(
    val identifierNode: IdentifierNode,
    val alias: IdentifierNode?,
) : ExportNode(listOfNotNull(identifierNode, alias))

class DeclarationExportNode(val declaration: StatementNode) : ExportNode(listOf(declaration))

class DefaultFunctionExportNode(val declaration: FunctionDeclarationNode) : ExportNode(listOf(declaration))

class DefaultClassExportNode(val classNode: ClassDeclarationNode) : ExportNode(listOf(classNode))

class DefaultExpressionExportNode(val expression: ExpressionNode) : ExportNode(listOf(expression))
