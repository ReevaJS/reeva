package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.literals.PropertyName
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.VariableSourceProvider

class ClassDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    val classNode: ClassNode,
) : VariableSourceNode(listOfNotNull(identifier, classNode)), DeclarationNode, VariableSourceProvider {
    override fun name() = identifier?.processedName ?: TODO()

    override val declarations = listOf(this)

    override fun sources() = listOf(this)
}

class ClassExpressionNode(
    val identifier: IdentifierNode?, // can always be omitted
    val classNode: ClassNode,
) : AstNodeBase(listOfNotNull(identifier, classNode)), ExpressionNode

class ClassNode(
    val heritage: ExpressionNode?,
    val body: List<ClassElementNode>
) : NodeWithScope(listOfNotNull(heritage) + body)

sealed class ClassElementNode(
    children: List<AstNode>,
    val isStatic: Boolean,
) : AstNodeBase(children)

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
