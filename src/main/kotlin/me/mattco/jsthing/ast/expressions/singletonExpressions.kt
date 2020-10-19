package me.mattco.jsthing.ast.expressions

import me.mattco.jsthing.ast.ASTNode
import me.mattco.jsthing.ast.expressions.ImportMetaNode.dumpSelf
import me.mattco.jsthing.utils.stringBuilder

object ImportMetaNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}

object NewTargetNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}

object TrueNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}

object FalseNode : ExpressionNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}
