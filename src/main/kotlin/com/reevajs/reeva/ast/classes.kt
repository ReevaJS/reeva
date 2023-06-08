package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.ast.literals.PropertyName
import com.reevajs.reeva.ast.statements.DeclarationNode
import com.reevajs.reeva.ast.statements.VariableSourceProvider

class ClassDeclarationNode(
    val identifier: IdentifierNode?, // can be omitted in default exports
    val classNode: ClassNode,
) : VariableSourceNode(), DeclarationNode, VariableSourceProvider {
    override val children get() = listOfNotNull(identifier, classNode)

    override fun name() = identifier?.processedName ?: TODO()

    override val declarations = listOf(this)

    override fun sources() = listOf(this)
}

class ClassExpressionNode(
    val identifier: IdentifierNode?, // can always be omitted
    val classNode: ClassNode,
) : AstNodeBase() {
    override val children get() = listOfNotNull(identifier, classNode)
}

class ClassNode(
    val heritage: AstNode?,
    val body: List<ClassElementNode>
) : NodeWithScope() {
    override val children get() = listOfNotNull(heritage) + body
}

sealed class ClassElementNode(val isStatic: Boolean) : AstNodeBase()

class ClassFieldNode(
    val identifier: PropertyName,
    val initializer: AstNode?,
    isStatic: Boolean,
) : ClassElementNode(isStatic) {
    override val children get() = listOfNotNull(identifier, initializer)
}

class ClassMethodNode(val method: MethodDefinitionNode, isStatic: Boolean) : ClassElementNode(isStatic) {
    override val children get() = listOf(method)

    fun isConstructor() = !isStatic && method.isConstructor()
}
