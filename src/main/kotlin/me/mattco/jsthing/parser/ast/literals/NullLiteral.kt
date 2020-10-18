package me.mattco.jsthing.parser.ast.literals

object NullLiteral : Literal() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
    }
}
