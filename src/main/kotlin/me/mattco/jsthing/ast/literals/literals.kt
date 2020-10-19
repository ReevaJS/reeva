package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.ast.literals.NullNode.dumpSelf
import me.mattco.jsthing.utils.stringBuilder

class BooleanNode(val value: Boolean) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class StringLiteralNode(val value: String) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class NumericLiteralNode(val value: Double) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

object NullNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}

object ThisNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}
