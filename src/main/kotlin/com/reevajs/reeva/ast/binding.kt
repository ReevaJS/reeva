package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.PropertyName
import com.reevajs.reeva.ast.statements.VariableSourceProvider
import com.reevajs.reeva.utils.expect

@Suppress("UNCHECKED_CAST")
class BindingPatternNode(
    val kind: BindingKind,
    private val entries: List<BindingEntry>,
) : ASTNodeBase(entries), VariableSourceProvider {
    val bindingProperties: List<BindingProperty>
        get() = entries as List<BindingProperty>

    val bindingElements: List<BindingElement>
        get() = entries as List<BindingElement>

    override fun sources(): List<VariableSourceNode> {
        return if (kind == BindingKind.Object) {
            bindingProperties.flatMap { it.sources() }
        } else bindingElements.flatMap { it.sources() }
    }

    init {
        when (kind) {
            BindingKind.Object -> expect(entries.all { it is BindingProperty })
            BindingKind.Array -> expect(entries.all { it is BindingElement })
        }
    }
}

enum class BindingKind {
    Object,
    Array,
}

class BindingDeclaration(
    val identifier: IdentifierNode,
) : VariableSourceNode(listOf(identifier)) {
    override fun name() = identifier.processedName
}

class BindingDeclarationOrPattern(
    val node: ASTNode,
) : ASTNodeBase(listOf(node)), VariableSourceProvider {
    val isBindingPattern: Boolean get() = node is BindingPatternNode

    val asBindingPattern: BindingPatternNode
        get() = node as BindingPatternNode

    val asBindingDeclaration: BindingDeclaration
        get() = node as BindingDeclaration

    override fun sources() = if (isBindingPattern) {
        asBindingPattern.sources()
    } else listOf(asBindingDeclaration)

    init {
        expect(node is BindingPatternNode || node is BindingDeclaration)
    }
}

sealed class BindingEntry(children: List<ASTNode>) : ASTNodeBase(children), VariableSourceProvider

sealed class BindingProperty(children: List<ASTNode>) : BindingEntry(children), VariableSourceProvider

class BindingRestProperty(val declaration: BindingDeclaration) : BindingProperty(listOf(declaration)) {
    override fun sources() = listOf(declaration)
}

class SimpleBindingProperty(
    val declaration: BindingDeclaration,
    val alias: BindingDeclarationOrPattern?,
    val initializer: ExpressionNode?,
) : BindingProperty(listOfNotNull(declaration, alias, initializer)) {
    override fun sources() = alias?.sources() ?: listOf(declaration)
}

class ComputedBindingProperty(
    val name: PropertyName,
    val alias: BindingDeclarationOrPattern,
    val initializer: ExpressionNode?,
) : BindingProperty(listOfNotNull(name, alias, initializer)) {
    override fun sources() = alias.sources()
}

sealed class BindingElement(children: List<ASTNode>) : BindingEntry(children), VariableSourceProvider

class BindingRestElement(val declaration: BindingDeclarationOrPattern) : BindingElement(listOf(declaration)) {
    override fun sources() = declaration.sources()
}

class SimpleBindingElement(
    val alias: BindingDeclarationOrPattern,
    val initializer: ExpressionNode?,
) : BindingElement(listOfNotNull(alias, initializer)) {
    override fun sources() = alias.sources()
}

class BindingElisionElement : BindingElement(listOf()) {
    override fun sources() = emptyList<VariableSourceNode>()
}
