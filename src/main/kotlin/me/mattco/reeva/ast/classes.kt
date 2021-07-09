package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.literals.PropertyName

class ClassDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    val classNode: ClassNode,
) : VariableSourceNode(listOfNotNull(identifier, classNode)), StatementNode {
    override fun name() = identifier?.name ?: TODO()
}

class ClassExpressionNode(
    val identifier: IdentifierNode?, // can always be omitted
    val classNode: ClassNode,
) : ASTNodeBase(listOfNotNull(identifier, classNode)), ExpressionNode

class ClassNode(
    val heritage: ExpressionNode?,
    val body: List<ClassElementNode>
) : NodeWithScope(listOfNotNull( heritage) + body)

sealed class ClassElementNode(
    children: List<ASTNode>,
    val isStatic: Boolean,
) : ASTNodeBase(children)

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
