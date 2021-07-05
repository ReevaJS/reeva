package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.literals.PropertyName
import me.mattco.reeva.parser.Variable

class ClassDeclarationNode(
    val identifier: BindingIdentifierNode?, // can be omitted in default exports
    val classNode: ClassNode,
) : VariableSourceNode(listOfNotNull(identifier, classNode)), StatementNode {
    override var variable: Variable
        get() = identifier!!.variable
        set(value) { identifier!!.variable = value }
}

class ClassExpressionNode(
    val identifier: BindingIdentifierNode?, // can always be omitted
    val classNode: ClassNode,
) : ASTNodeBase(listOf(classNode)), ExpressionNode

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
