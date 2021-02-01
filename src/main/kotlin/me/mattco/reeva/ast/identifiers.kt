package me.mattco.reeva.ast

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent

class BindingIdentifierNode(val identifierName: String) : VariableRefNode(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierNode(val identifierName: String) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierReferenceNode(val identifierName: String) : VariableRefNode(), ExpressionNode {
    override val isInvalidAssignmentTarget = false

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}
