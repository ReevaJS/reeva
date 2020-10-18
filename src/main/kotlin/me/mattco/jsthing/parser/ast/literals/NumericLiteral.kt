package me.mattco.jsthing.parser.ast.literals

class NumericLiteral(val value: Double) : Literal() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(value)
        append(")\n")
    }
}
