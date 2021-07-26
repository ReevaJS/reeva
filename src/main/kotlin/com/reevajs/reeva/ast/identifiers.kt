package com.reevajs.reeva.ast

import com.reevajs.reeva.ast.ASTNode.Companion.appendIndent

class IdentifierNode(val name: String) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(name)
        append(")\n")
    }

    override fun toString() = name
}

class IdentifierReferenceNode(val identifierName: String) : VariableRefNode(), ExpressionNode {
    override val isInvalidAssignmentTarget = false

    override fun name() = identifierName

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }

    override fun toString() = identifierName
}
