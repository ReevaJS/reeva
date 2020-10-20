package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

abstract class LiteralNode : ExpressionNode()

class BooleanNode(val value: Boolean) : LiteralNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class StringLiteralNode(val value: String) : LiteralNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class NumericLiteralNode(val value: Double) : LiteralNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

object NullNode : LiteralNode()

object ThisNode : LiteralNode()
