package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.ExpressionNode

sealed class BooleanLiteralNode (val value: Boolean) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        append(" (value=")
        append(value)
        append(")\n")
    }
}

object TrueNode : BooleanLiteralNode (true)

object FalseNode : BooleanLiteralNode (false)

class StringLiteralNode(val value: String) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class NumericLiteralNode(val value: Double) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class BigIntLiteralNode(val value: String, val type: Type) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }

    enum class Type(val radix: Int) {
        Normal(10),
        Hex(16),
        Octal(8),
        Binary(2),
    }
}

object NullLiteralNode : ASTNodeBase(), ExpressionNode

object ThisLiteralNode : ASTNodeBase(), ExpressionNode
