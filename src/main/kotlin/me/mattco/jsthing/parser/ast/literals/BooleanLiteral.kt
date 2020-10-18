package me.mattco.jsthing.parser.ast.literals

class BooleanLiteral(val value: Boolean) : Literal() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(value)
        append(")\n")
    }
}
