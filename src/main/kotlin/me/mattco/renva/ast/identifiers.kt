package me.mattco.renva.ast

import me.mattco.renva.ast.ASTNode.Companion.appendIndent
import me.mattco.renva.utils.stringBuilder

class BindingIdentifierNode(val identifierName: String) : NodeBase() {
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

class IdentifierNode(val identifierName: String) : NodeBase(), ExpressionNode {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class IdentifierReferenceNode(val identifierName: String) : NodeBase(), PrimaryExpressionNode {
    override fun assignmentTargetType(): ASTNode.AssignmentTargetType {
        // TODO: Strict mode check
        return ASTNode.AssignmentTargetType.Simple
    }

    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}

class LabelIdentifierNode(val identifierName: String) : NodeBase() {
    override fun stringValue() = identifierName

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (identifier=")
        append(identifierName)
        append(")\n")
    }
}
