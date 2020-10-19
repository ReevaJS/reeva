package me.mattco.jsthing.ast.statements

import me.mattco.jsthing.utils.stringBuilder

object DebuggerNode : StatementNode() {
    override fun dump(indent: Int) = stringBuilder {
        dumpSelf(indent)
    }
}
