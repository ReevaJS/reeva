package me.mattco.jsthing.parser.ast.expressions

class UnaryExpression(val target: Expression, val op: Operation) : Expression() {
    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append(" (")
        append(op.string)
        append(")\n")
        append(target.dump(indent + 1))
    }

    enum class Operation(val string: String) {
        BitwiseNot("~"),
        Not("!"),
        Plus("+"),
        Minus("-"),
        Typeof("typeof"),
        Void("void"),
        Delete("delete")
    }
}
