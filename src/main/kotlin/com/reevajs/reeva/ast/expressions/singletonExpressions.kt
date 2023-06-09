package com.reevajs.reeva.ast.expressions

import com.reevajs.reeva.ast.AstNode
import com.reevajs.reeva.ast.AstVisitor
import com.reevajs.reeva.ast.VariableRefNode
import com.reevajs.reeva.parsing.lexer.SourceLocation

class ImportMetaExpressionNode(override val sourceLocation: SourceLocation) : AstNode {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class NewTargetNode(sourceLocation: SourceLocation) : VariableRefNode(sourceLocation) {
    override val children get() = emptyList<AstNode>()

    override fun accept(visitor: AstVisitor) = visitor.visit(this)

    override fun name() = "*new.target"
}
