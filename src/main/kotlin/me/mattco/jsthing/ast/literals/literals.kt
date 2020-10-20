package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.ast.literals.NullNode.dumpSelf
import me.mattco.jsthing.utils.stringBuilder

abstract class LiteralNode : ASTNode()

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

object NullNode : LiteralNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}

object ThisNode : LiteralNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}
