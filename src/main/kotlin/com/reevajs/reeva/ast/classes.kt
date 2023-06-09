package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.literals.PropertyName
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.VariableSourceProvider
import com.reevajs.reeva.parsing.lexer.SourceLocation

class ClassDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    val classNode: ClassNode,
    sourceLocation: SourceLocation,
) : VariableSourceNode(sourceLocation), DeclarationNode, VariableSourceProvider {
    override val children get() = listOfNotNull(identifier, classNode)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun name() = identifier?.processedName ?: TODO()

    override val declarations = listOf(this)

    override fun sources() = listOf(this)
}

class ClassExpressionNode(
    val identifier: IdentifierNode?, // can always be omitted
    val classNode: ClassNode,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = listOfNotNull(identifier, classNode)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ClassNode(
    val heritage: AstNode?,
    val body: List<ClassElementNode>,
    sourceLocation: SourceLocation,
) : NodeWithScope(sourceLocation) {
    override val children get() = listOfNotNull(heritage) + body

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

sealed class ClassElementNode(override val sourceLocation: SourceLocation, val isStatic: Boolean) : AstNode

class ClassFieldNode(
    val identifier: PropertyName,
    val initializer: AstNode?,
    isStatic: Boolean,
    sourceLocation: SourceLocation,
) : ClassElementNode(sourceLocation, isStatic) {
    override val children get() = listOfNotNull(identifier, initializer)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ClassMethodNode(
    val method: MethodDefinitionNode,
    isStatic: Boolean,
    sourceLocation: SourceLocation,
) : ClassElementNode(sourceLocation, isStatic) {
    override val children get() = listOf(method)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    fun isConstructor() = !isStatic && method.isConstructor()
}
