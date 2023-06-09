package com.reevajs.reeva.ast.literals

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.ast.VariableRefNode
import com.reevajs.reeva.parsing.lexer.SourceLocation

sealed class BooleanLiteralNode(
    val value: Boolean,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class TrueNode(sourceLocation: SourceLocation) : BooleanLiteralNode(true, sourceLocation)

class FalseNode(sourceLocation: SourceLocation) : BooleanLiteralNode(false, sourceLocation)

class StringLiteralNode(val value: String, override val sourceLocation: SourceLocation) : AstNode {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class NumericLiteralNode(val value: Double, override val sourceLocation: SourceLocation) : AstNode {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (value=")
        append(value)
        append(")\n")
    }
}

class BigIntLiteralNode(
    val value: String,
    val type: Type,
    override val sourceLocation: SourceLocation,
) : AstNode {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

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

class NullLiteralNode(override val sourceLocation: SourceLocation) : AstNode {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ThisLiteralNode(sourceLocation: SourceLocation) : VariableRefNode(sourceLocation) {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun name() = "*this"
}
