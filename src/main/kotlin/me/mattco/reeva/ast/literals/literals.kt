package me.mattco.reeva.ast.literals

import me.mattco.reeva.ast.ASTNode.Companion.appendIndent
import me.mattco.reeva.ast.LiteralNode
import me.mattco.reeva.ast.ASTNodeBase
import me.mattco.reeva.ast.PrimaryExpressionNode

sealed class BooleanLiteralNode (val value: Boolean) : ASTNodeBase(), LiteralNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        append(" (value=")
        append(value)
        append(")\n")
    }
}

object TrueNode : BooleanLiteralNode (true)

object FalseNode : BooleanLiteralNode (false)

class StringLiteralNode(val value: String) : ASTNodeBase(), LiteralNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class NumericLiteralNode(val value: Double) : ASTNodeBase(), LiteralNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class BigIntLiteralNode(val value: String, val type: Type) : ASTNodeBase(), LiteralNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }

    enum class Type {
        Normal,
        Hex,
        Octal,
        Binary,
    }
}

object NullLiteralNode : ASTNodeBase(), LiteralNode

object ThisLiteralNode : ASTNodeBase(), PrimaryExpressionNode
