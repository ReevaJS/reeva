package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.ASTNode.Companion.appendIndent
import me.mattco.jsthing.ast.ExpressionNode
import me.mattco.jsthing.ast.LiteralNode
import me.mattco.jsthing.ast.NodeBase
import me.mattco.jsthing.ast.PrimaryExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class BooleanNode(val value: Boolean) : NodeBase(), LiteralNode {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class StringLiteralNode(val value: String) : NodeBase(), LiteralNode {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class NumericLiteralNode(val value: Double) : NodeBase(), LiteralNode {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

object NullNode : NodeBase(), LiteralNode

object ThisNode : NodeBase(), PrimaryExpressionNode
