package me.mattco.jsthing.parser.ast

import me.mattco.jsthing.parser.ast.statements.FunctionDeclaration
import me.mattco.jsthing.parser.ast.statements.Statement
import me.mattco.jsthing.parser.ast.statements.VariableDeclaration
import me.mattco.jsthing.utils.stringBuilder

abstract class Scope : Statement() {
    val children = mutableListOf<Statement>()
    val variables = mutableListOf<VariableDeclaration>()
    val functions = mutableListOf<FunctionDeclaration>()

    fun addStatement(statement: Statement) {
        children.add(statement)
    }

    fun addVariables(declarations: List<VariableDeclaration>) {
        variables.addAll(declarations)
    }

    fun addFunctions(declarations: List<FunctionDeclaration>) {
        functions.addAll(declarations)
    }

    protected fun dumpHelper(indent: Int) = stringBuilder {
        appendIndent(indent)
        append("Variables:\n")
        variables.forEach {
            it.dump(indent + 1)
        }
        appendIndent(indent)
        append("Functions:\n")
        functions.forEach {
            it.dump(indent + 1)
        }
        appendIndent(indent)
        append("Children:\n")
        children.forEach {
            append(it.dump(indent + 1))
        }
    }
}
