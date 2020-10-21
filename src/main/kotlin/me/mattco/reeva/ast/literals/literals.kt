package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.LiteralNode
import me.mattco.reeva.ast.NodeBase
import me.mattco.reeva.ast.PrimaryExpressionNode
import me.mattco.reeva.utils.stringBuilder

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
