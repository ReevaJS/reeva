package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class BindingIdentifierNode(val identifierName: String) : ASTNode() {
    override fun stringValue() = identifierName

    override fun boundNames() = listOf(identifierName)

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierNode(val identifierName: String) : ASTNode() {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierReferenceNode(val identifierName: String) : ExpressionNode() {
    override fun stringValue() = identifierName

    override fun assignmentTargetType(): AssignmentTargetType {
        // TODO: Strict mode check
        return AssignmentTargetType.Simple
    }

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class LabelIdentifierNode(val identifierName: String) : ASTNode() {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}
