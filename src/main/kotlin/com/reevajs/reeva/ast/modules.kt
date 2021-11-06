package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.statements.ASTListNode
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.StatementList
import com.reevajs.reeva.core.lifecycle.ModuleRecord

typealias ImportList = ASTListNode<Import>
typealias ExportList = ASTListNode<NamedExport>

class ModuleNode(val body: StatementList) : RootNode(body) {
    fun requestedModules() = body.filterIsInstance<ImportDeclarationNode>().map { it.moduleName }
}

class ImportDeclarationNode(
    val imports: ImportList?, // null indicates `import 'file'` syntax
    val moduleName: String,
) : ASTNodeBase(imports ?: emptyList()), StatementNode {
    init {
        imports?.forEach { it.parentDeclNode = this }
    }

    override fun dump(indent: Int) = buildString {
        dumpSelf(indent)
        imports?.forEach {
            append(it.dump(indent + 1))
        }
    }
}

sealed class Import(children: List<ASTNode>) : VariableSourceNode(children) {
    lateinit var parentDeclNode: ImportDeclarationNode

    abstract fun sourceModuleName(): String
}

class NormalImport(
    val identifierNode: IdentifierNode,
    val alias: IdentifierNode,
) : Import(listOf(identifierNode, alias)) {
    override fun name() = alias.processedName

    override fun sourceModuleName() = identifierNode.processedName
}

class DefaultImport(val identifierNode: IdentifierNode) : Import(listOf(identifierNode)) {
    override fun name() = identifierNode.processedName

    override fun sourceModuleName() = ModuleRecord.DEFAULT_SPECIFIER
}

class NamespaceImport(val identifierNode: IdentifierNode) : Import(listOf(identifierNode)) {
    override fun name() = identifierNode.processedName

    override fun sourceModuleName() = ModuleRecord.NAMESPACE_SPECIFIER
}

sealed class ExportNode(children: List<ASTNode>) : ASTNodeBase(children), StatementNode

sealed class ExportFromNode(val moduleName: String, children: List<ASTNode>) : ExportNode(children)

class ExportAllFromNode(moduleName: String) : ExportFromNode(moduleName, emptyList())

class ExportAllAsFromNode(
    val identifierNode: IdentifierNode,
    moduleName: String,
) : ExportFromNode(moduleName, listOf(identifierNode))

class ExportNamedFromNode(
    val exports: NamedExports,
    moduleName: String,
) : ExportFromNode(moduleName, exports.exports)

class NamedExport(
    val identifierNode: IdentifierReferenceNode,
    val alias: IdentifierNode?,
) : ExportNode(listOfNotNull(identifierNode, alias))

class NamedExports(val exports: ExportList) : ExportNode(exports)

class DeclarationExportNode(val declaration: DeclarationNode) : ExportNode(listOf(declaration))

class DefaultFunctionExportNode(val declaration: FunctionDeclarationNode) : ExportNode(listOf(declaration))

class DefaultClassExportNode(val classNode: ClassDeclarationNode) : ExportNode(listOf(classNode))

class DefaultExpressionExportNode(val expression: ExpressionNode) : ExportNode(listOf(expression))
