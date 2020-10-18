package me.mattco.jsthing.parser.ast.statements

abstract class Declaration : Statement() {
    enum class Type {
        Var,
        Let,
        Const
    }
}
