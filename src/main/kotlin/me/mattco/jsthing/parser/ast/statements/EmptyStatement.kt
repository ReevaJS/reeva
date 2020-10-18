package me.mattco.jsthing.parser.ast.statements

import me.mattco.jsthing.utils.stringBuilder

class EmptyStatement : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(name)
        append("\n")
    }
}
