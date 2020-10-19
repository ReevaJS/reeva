package me.mattco.jsthing.ast.literals

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ExpressionNode
import me.mattco.jsthing.utils.stringBuilder

class ArrayLiteralNode(val elements: List<ExpressionNode>) : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        elements.forEach {
            append(it.dump(indent + 1))
        }
    }
}

object ElisionNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}
