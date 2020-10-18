package me.mattco.jsthing.parser.ast

class Program : Scope(), Strictable {
    override var isStrict: Boolean = false

    override fun dump(indent: Int) = stringBuilder {
        appendIndent(indent)
        appendName()
        append("\n")
        append(dumpHelper(indent + 1))
    }
}
