package me.mattco.reeva.ast

import me.mattco.reeva.ast.literals.PropertyNameNode

sealed class BindingPattern(children: List<ASTNode>) : NodeBase(children), ExpressionNode

class ObjectBindingPattern(
    val bindingProperties: List<BindingProperty>,
    val restProperty: BindingRestProperty?,
) : BindingPattern(bindingProperties + listOfNotNull(restProperty)) {
    override fun boundNames(): List<String> {
        return bindingProperties.flatMap(ASTNode::boundNames) + (restProperty?.boundNames() ?: emptyList())
    }

    override fun containsExpression(): Boolean {
        return bindingProperties.any(ASTNode::containsExpression)
    }
}

class ArrayBindingPattern(
    val bindingElements: List<BindingElisionElement>,
    val restProperty: BindingRestElement?,
) : BindingPattern(bindingElements + listOfNotNull(restProperty)) {
    override fun boundNames(): List<String> {
        return bindingElements.filter { it !is BindingElisionNode }.flatMap(ASTNode::boundNames) +
            (restProperty?.boundNames() ?: emptyList())
    }

    override fun containsExpression(): Boolean {
        return bindingElements.any(ASTNode::containsExpression) || restProperty?.containsExpression() == true
    }
}

sealed class BindingProperty(children: List<ASTNode>) : NodeBase(children)

class SingleNameBindingProperty(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?,
) : BindingProperty(listOfNotNull(identifier, initializer)) {
    override fun boundNames() = identifier.boundNames()

    override fun containsExpression() = initializer != null

    override fun hasInitializer() = initializer != null

    override fun isSimpleParameterList() = initializer == null
}

class ComplexBindingProperty(
    val propertyName: PropertyNameNode,
    val element: BindingElementNode,
) : BindingProperty(listOfNotNull(propertyName, element)) {
    override fun boundNames() = element.boundNames()

    override fun containsExpression(): Boolean {
        return propertyName.containsExpression() || element.containsExpression()
    }
}

sealed class BindingElisionElement(children: List<ASTNode>) : NodeBase(children)

object BindingElisionNode : BindingElisionElement(emptyList()) {
    override fun containsExpression() = false
}

sealed class BindingElementNode(children: List<ASTNode>) : BindingElisionElement(children)

class SingleNameBindingElement(
    val identifier: BindingIdentifierNode,
    val initializer: InitializerNode?,
) : BindingElementNode(listOfNotNull(identifier, initializer)) {
    override fun boundNames() = identifier.boundNames()

    override fun containsExpression() = initializer != null

    override fun hasInitializer() = initializer != null

    override fun isSimpleParameterList() = initializer == null
}

class PatternBindingElement(
    val pattern: BindingPattern,
    val initializer: InitializerNode?,
) : BindingElementNode(listOfNotNull(pattern, initializer)) {
    override fun boundNames() = pattern.boundNames()

    override fun containsExpression() = initializer != null || pattern.containsExpression()

    override fun hasInitializer() = initializer != null

    override fun isSimpleParameterList() = false
}

class BindingRestProperty(val target: BindingIdentifierNode) : NodeBase(listOf(target))

class BindingRestElement(
    val target: ASTNode // BindingIdentifierNode or BindingPattern
) : NodeBase(listOf(target)) {
    override fun containsExpression() = target is BindingPattern && target.containsExpression()
}
