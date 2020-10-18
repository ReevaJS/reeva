package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.parser.ast.ASTNode.Companion.appendIndent
import me.mattco.jsthing.parser.ast.Scope

class BlockStatement : Scope() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        append(dumpHelper(indent + 1))
    }
}
