package me.mattco.jsthing.parser.ast.statements

class EmptyStatement : Statement() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        append(name)
        append("\n")
    }
}
