package me.mattco.jsthing.parser.ast.literals

class StringLiteral(val value: String) : Literal() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(value)
        append(")\n")
    }
}
