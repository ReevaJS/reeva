package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.literals.PropertyName

class ClassDeclarationNode(
    val identifier: BindingIdentifierNode?, // can be omitted in default exports
    val classNode: ClassNode,
) : ASTNodeBase(listOfNotNull(identifier, classNode)), StatementNode

class ClassExpressionNode(
    val identifier: BindingIdentifierNode?, // can always be omitted
    val classNode: ClassNode,
) : ASTNodeBase(listOf(classNode)), ExpressionNode

class ClassNode(
    val heritage: ExpressionNode?,
    val body: List<ClassElementNode>
) : ASTNodeBase(listOfNotNull( heritage) + body)

sealed class ClassElementNode(
    children: List<ASTNode>,
    val isStatic: Boolean,
) : ASTNodeBase(children)

class EmptyClassElementNode : ClassElementNode(emptyList(), false)

class ClassFieldNode(
    val identifier: PropertyName,
    val initializer: ExpressionNode?,
    isStatic: Boolean,
) : ClassElementNode(listOfNotNull(identifier, initializer), isStatic)

class ClassMethodNode(
    val method: MethodDefinitionNode,
    isStatic: Boolean,
) : ClassElementNode(listOf(method), isStatic) {
    fun isConstructor() = !isStatic && method.isConstructor()
}
