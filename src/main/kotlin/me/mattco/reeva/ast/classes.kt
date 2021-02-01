package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.literals.PropertyNameNode

class ClassDeclarationNode(val classNode: ClassNode) : ASTNodeBase(listOf(classNode)), StatementNode

class ClassExpressionNode(val classNode: ClassNode) : ASTNodeBase(listOf(classNode)), ExpressionNode

class ClassNode(
    val identifier: BindingIdentifierNode?,
    val heritage: ExpressionNode?,
    val body: List<ClassElementNode>
) : ASTNodeBase(listOfNotNull(identifier, heritage) + body)

sealed class ClassElementNode(
    children: List<ASTNode>,
    val isStatic: Boolean,
) : ASTNodeBase(children)

object EmptyClassElementNode : ClassElementNode(emptyList(), false)

class ClassFieldNode(
    val identifier: PropertyNameNode,
    val initializer: ExpressionNode?,
    isStatic: Boolean,
) : ClassElementNode(listOfNotNull(identifier, initializer), isStatic)

class ClassMethodNode(
    val method: MethodDefinitionNode,
    isStatic: Boolean,
) : ClassElementNode(listOf(method), isStatic) {
    fun isConstructor() = !isStatic && method.isConstructor()
}
