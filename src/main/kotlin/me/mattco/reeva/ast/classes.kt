package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.MethodDefinitionNode
import me.mattco.reeva.ast.literals.PropertyNameNode

class ClassDeclarationNode(val classNode: ClassNode) : NodeBase(listOf(classNode)), DeclarationNode {
    override fun boundNames() = classNode.identifier?.boundNames() ?: listOf("*default*")

    override fun declarationPart() = this

    override fun isConstantDeclaration() = false

    override fun containsDuplicateLabels(labelSet: Set<String>) = false

    override fun containsUndefinedBreakTarget(labelSet: Set<String>) = false

    override fun containsUndefinedContinueTarget(iterationSet: Set<String>, labelSet: Set<String>) = false

    override fun lexicallyDeclaredNames() = boundNames()

    override fun lexicallyScopedDeclarations() = listOf(declarationPart())

    override fun topLevelLexicallyDeclaredNames() = boundNames()

    override fun topLevelLexicallyScopedDeclarations() = listOf(this)

    override fun topLevelVarDeclaredNames() = emptyList<String>()

    override fun topLevelVarScopedDeclarations() = emptyList<NodeBase>()

    override fun varDeclaredNames() = emptyList<String>()

    override fun varScopedDeclarations() = emptyList<NodeBase>()
}

class ClassExpressionNode(val classNode: ClassNode) : NodeBase(listOf(classNode)), PrimaryExpressionNode {
    override fun hasName() = classNode.identifier != null

    override fun isFunctionDefinition() = true
}

data class ClassTailNode(val heritage: ExpressionNode?, val body: ClassElementList)

class ClassNode(
    val identifier: BindingIdentifierNode?,
    val heritage: ExpressionNode?,
    val body: ClassElementList
) : NodeBase(listOfNotNull(identifier, heritage) + body) {
    override fun constructorMethod() = body.constructorMethod()
}

class ClassElementList(val elements: List<ClassElementNode>) : NodeBase(elements) {
    override fun constructorMethod(): ASTNode? {
        return elements.firstOrNull { it.classElementKind() == ASTNode.ClassElementKind.ConstructorMethod }
    }
}

class ClassElementNode(
    val node: ASTNode?,
    val initializer: InitializerNode?,
    val isStatic: Boolean,
    val type: Type,
) : NodeBase(listOfNotNull(node)) {
    override fun propName(): String? {
        return when (type) {
            Type.Method -> null
            Type.Field -> node!!.propName()
            Type.Empty -> null
        }
    }

    override fun classElementKind(): ASTNode.ClassElementKind {
        if (type == Type.Method && !isStatic && node?.propName() == "constructor" &&
            (node as MethodDefinitionNode).type == MethodDefinitionNode.Type.Normal
        )
            return ASTNode.ClassElementKind.ConstructorMethod
        if (type == Type.Empty)
            return ASTNode.ClassElementKind.Empty
        return ASTNode.ClassElementKind.NonConstructorMethod
    }

    enum class Type {
        Method,
        Field,
        Empty
    }
}
