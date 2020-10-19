package me.mattco.jsthing.ast

import me.mattco.jsthing.ast.statements.StatementNode
import me.mattco.jsthing.utils.stringBuilder

class ScriptNode(val statements: List<StatementNode>) : ASTNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
        statements.forEach {
            append(it.dump(indent + 1))
        }
    }
}
