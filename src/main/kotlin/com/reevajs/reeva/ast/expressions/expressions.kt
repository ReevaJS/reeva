package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode.Companion.appendIndent
import com.reevajs.reeva.ast.AstNodeBase
import com.reevajs.reeva.ast.ArgumentNode
import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.parsing.lexer.SourceLocation
import com.reevajs.regexp.RegExp

class AssignmentExpressionNode(
    val lhs: AstNode,
    val rhs: AstNode,
    val op: BinaryOperator?, // Null indicates regular assignment
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(lhs, rhs)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (")
        append("${op?.symbol ?: ""}=")
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }
}

class AwaitExpressionNode(val expression: AstNode, sourceLocation: SourceLocation) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

// TODO: This isn't exactly to spec
class CallExpressionNode(
    val target: AstNode,
    val arguments: List<ArgumentNode>,
    val isOptional: Boolean,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = arguments + target

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

// Note that this name deviates from the spec because I think this is
// a much better name. It is not clear from the name "ExpressionNode"
// that the inner expression are separated by comma operators, and only
// the last one should be returned.
class CommaExpressionNode(
    val expressions: List<AstNode>,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = expressions

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ConditionalExpressionNode(
    val predicate: AstNode,
    val ifTrue: AstNode,
    val ifFalse: AstNode,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(predicate, ifTrue, ifFalse)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class MemberExpressionNode(
    val lhs: AstNode,
    val rhs: AstNode,
    val type: Type,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(lhs, rhs)

    override val isInvalidAssignmentTarget = false

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (type=")
        append(type.name)
        append(")\n")
        append(lhs.dump(indent + 1))
        append(rhs.dump(indent + 1))
    }

    enum class Type {
        Computed,
        NonComputed,
        Tagged,
    }
}

class NewExpressionNode(
    val target: AstNode,
    val arguments: List<ArgumentNode>,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = arguments + target

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class SuperPropertyExpressionNode(
    val target: AstNode,
    val isComputed: Boolean,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(target)

    override val isInvalidAssignmentTarget: Boolean = false

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (computed=")
        append(isComputed)
        append(")\n")
        append(target.dump(indent + 1))
    }
}

class SuperCallExpressionNode(
    val arguments: List<ArgumentNode>,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = arguments

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ImportCallExpressionNode(val expression: AstNode, sourceLocation: SourceLocation) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class YieldExpressionNode(
    val expression: AstNode?,
    val generatorYield: Boolean,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = listOfNotNull(expression)

    init {
        if (expression == null && generatorYield)
            throw IllegalArgumentException("Cannot have a generatorYield expression without a target expression")
    }

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun dump(indent: Int) = buildString {
        appendIndent(indent)
        appendName()
        append(" (generatorYield=")
        append(generatorYield)
        append(")\n")
        expression?.dump(indent + 1)?.also(::append)
    }
}

class ParenthesizedExpressionNode(
    val expression: AstNode,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = listOf(expression)

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class TemplateLiteralNode(val parts: List<AstNode>, sourceLocation: SourceLocation) : AstNodeBase(sourceLocation) {
    override val children get() = parts

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class RegExpLiteralNode(
    val source: String,
    val flags: String,
    val regexp: RegExp,
    sourceLocation: SourceLocation,
) : AstNodeBase(sourceLocation) {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
