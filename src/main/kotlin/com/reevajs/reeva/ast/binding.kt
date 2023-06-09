package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.PropertyName
import com.reevajs.reeva.ast.statements.VariableSourceProvider
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.reeva.utils.expect

@Suppress("UNCHECKED_CAST")
class BindingPatternNode(
    val kind: BindingKind,
    private val entries: List<BindingEntry>,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation), VariableSourceProvider {
    override val children get() = entries

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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

class BindingDeclaration(val identifier: IdentifierNode) : VariableSourceNode(identifier.sourceLocation) {
    override val children get() = listOf(identifier)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun name() = identifier.processedName
}

class BindingDeclarationOrPattern(val node: AstNode) : AstNodeBase(node.sourceLocation), VariableSourceProvider {
    override val children get() = listOf(node)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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

sealed class BindingEntry(sourceLocation: SourceLocation) : AstNodeBase(sourceLocation), VariableSourceProvider

sealed class BindingProperty(sourceLocation: SourceLocation) : BindingEntry(sourceLocation), VariableSourceProvider

class BindingRestProperty(
    val declaration: BindingDeclaration,
    sourceLocation: SourceLocation,
) : BindingProperty(sourceLocation) {
    override val children get() = listOf(declaration)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = listOf(declaration)
}

class SimpleBindingProperty(
    val declaration: BindingDeclaration,
    val alias: BindingDeclarationOrPattern?,
    val initializer: AstNode?,
    sourceLocation: SourceLocation,
) : BindingProperty(sourceLocation) {
    override val children get() = listOfNotNull(declaration, alias, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = alias?.sources() ?: listOf(declaration)
}

class ComputedBindingProperty(
    val name: PropertyName,
    val alias: BindingDeclarationOrPattern,
    val initializer: AstNode?,
    sourceLocation: SourceLocation,
) : BindingProperty(sourceLocation) {
    override val children get() = listOfNotNull(name, alias, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = alias.sources()
}

sealed class BindingElement(
    sourceLocation: SourceLocation,
) : BindingEntry(sourceLocation), VariableSourceProvider

class BindingRestElement(
    val declaration: BindingDeclarationOrPattern,
    sourceLocation: SourceLocation,
) : BindingElement(sourceLocation) {
    override val children get() = listOf(declaration)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = declaration.sources()
}

class SimpleBindingElement(
    val alias: BindingDeclarationOrPattern,
    val initializer: AstNode?,
    sourceLocation: SourceLocation,
) : BindingElement(sourceLocation) {
    override val children get() = listOfNotNull(alias, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = alias.sources()
}

class BindingElisionElement(sourceLocation: SourceLocation,) : BindingElement(sourceLocation) {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun sources() = emptyList<VariableSourceNode>()
}
