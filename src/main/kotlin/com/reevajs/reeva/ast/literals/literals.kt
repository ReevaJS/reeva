package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.ASTNode.Companion.appendIndent
import com.reevajs.reeva.ast.ASTNodeBase
import com.reevajs.reeva.ast.ExpressionNode
import com.reevajs.reeva.ast.VariableRefNode

sealed class BooleanLiteralNode(val value: Boolean) : ASTNodeBase(), ExpressionNode {
    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class TrueNode : BooleanLiteralNode(true)

class FalseNode : BooleanLiteralNode(false)

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

class NullLiteralNode : ASTNodeBase(), ExpressionNode

class ThisLiteralNode : ASTNodeBase(), ExpressionNode
