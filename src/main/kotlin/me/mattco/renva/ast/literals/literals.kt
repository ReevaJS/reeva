package me.mattco.renva.ast.literals

import me.mattco.renva.ast.ASTNode.Companion.appendIndent
import me.mattco.renva.ast.LiteralNode
import me.mattco.renva.ast.NodeBase
import me.mattco.renva.ast.PrimaryExpressionNode
import me.mattco.renva.utils.stringBuilder

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
