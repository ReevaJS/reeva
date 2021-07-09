package me.mattco.reeva.parsing

import me.mattco.reeva.ast.ASTNode
import me.mattco.reeva.utils.expect

abstract class Reporter {
    protected abstract val start: TokenLocation
    protected abstract val end: TokenLocation

    fun expected(expected: Any, actual: Any? = null): Nothing {
        if (actual != null) {
            error("expected $expected, but found $actual")
        } else error("expected $expected")
    }

    fun variableRedeclaration(name: String, oldType: String, newType: String): Nothing =
        error("cannot redeclare $oldType declaration \"$name\" as a $newType declaration")
    fun duplicateDeclaration(name: String, type: String): Nothing = error("duplicate $type declaration of \"$name\"")

    fun arrowFunctionNewLine(): Nothing = error("arrow function cannot be separated from it's arrow by a line terminator")
    fun breakOutsideOfLoopOrSwitch(): Nothing = error("break statement must be inside of a loop or switch statement")
    fun classDeclarationNoName(): Nothing = error("class declaration must have an identifier")
    fun classAsyncAccessor(isGetter: Boolean): Nothing = error("class ${if (isGetter) "getter" else "setter"} cannot be async")
    fun classGeneratorAccessor(isGetter: Boolean): Nothing = error("class ${if (isGetter) "getter" else "setter"} cannot be a generator")
    fun classGeneratorField(): Nothing = error("class declaration field cannot be a generator")
    fun constMissingInitializer(): Nothing = error("const variable declaration must have an initializer")
    fun continueOutsideOfLoop(): Nothing = error("continue statement must be inside of a loop")
    fun emptyTemplateLiteralExpr(): Nothing = error("empty template literal expression")
    fun emptyParenthesizedExpression(): Nothing = error("parenthesized expression cannot be empty")
    fun forEachMultipleDeclarations(): Nothing = error("for-in/of statement cannot contain multiple variable declarations")
    fun functionInExpressionContext(): Nothing = error("function declarations are not allowed in single-statement contexts")
    fun functionStatementNoName(): Nothing = error("function statement requires a name")
    fun identifierAfterNumericLiteral(): Nothing = error("numeric literal cannot be directly followed by an identifier")
    fun invalidBreakTarget(target: String): Nothing = error("invalid break target \"$target\"")
    fun invalidContinueTarget(target: String): Nothing = error("invalid continue target \"$target\"")
    fun invalidLhsInAssignment(): Nothing = error("invalid left-hand-side in assignment expression")
    fun invalidShorthandProperty(): Nothing = error("object shorthand property must be an identifier name")
    fun paramAfterRest(): Nothing = error("function rest parameter must be the last parameter")
    fun restParamInitializer(): Nothing = error("rest parameter cannot have an initializer")
    fun strictAssignToArguments(): Nothing = error("cannot assign to \"arguments\" in strict-mode code")
    fun strictAssignToEval(): Nothing = error("cannot assign to \"eval\" in strict-mode code")
    fun strictImplicitOctal(): Nothing = error("implicit octal literals are not allowed in strict-mode code")
    fun throwStatementNewLine(): Nothing = error("throw keyword cannot be separated from it's expression by a line terminator")
    fun unexpectedToken(token: TokenType): Nothing = error("unexpected token \"$token\"")
    fun unterminatedTemplateLiteral(): Nothing = error("unterminated template literal")
    fun unterminatedTemplateLiteralExpr(): Nothing = error("unterminated template literal expression")

    fun error(message: String): Nothing {
        throw Parser.ParsingException(message, start, end)
    }
}

class ErrorReporter(private val parser: Parser) : Reporter() {
    override val start: TokenLocation
        get() = parser.sourceStart
    override val end: TokenLocation
        get() = parser.sourceEnd

    fun at(sourceStart: TokenLocation, sourceEnd: TokenLocation) = object : Reporter() {
        override val start = sourceStart
        override val end = sourceEnd
    }

    fun at(node: ASTNode) = at(node.sourceStart, node.sourceEnd)

    fun at(token: Token) = at(token.start, token.end)

    companion object {
        fun prettyPrintError(sourceCode: String, error: Parser.ParsingException) {
            val (_, lineIndex, lineColumnStart) = error.start
            val (_, endLine, endColumn) = error.end

            val lines = sourceCode.lines()
            val lineColumnEnd = if (lineIndex == endLine) endColumn else lines[lineIndex].length - 1
            expect(lineColumnEnd <= lines[lineIndex].length)

            val linesToPrint = (lineIndex - 3)..(lineIndex + 3)
            val lineWidth = linesToPrint.maxOf { it.toString().length }

            println("\u001b[31mSyntaxError: ${error.message}\u001B[0m\n")

            for ((index, line) in lines.withIndex()) {
                if (index !in linesToPrint)
                    continue

                print("\u001B[38;5;240m")
                print(index.toString().padStart(lineWidth))
                print(".    ")
                print("\u001B[0m")

                if (index != lineIndex) {
                    println(line)
                    continue
                }

                buildString {
                    append(line)
                    append("\n")
                    repeat(lineColumnStart + lineWidth + 5) {
                        append(' ')
                    }
                    append("\u001B[31m")
                    repeat(lineColumnEnd - lineColumnStart) {
                        append('^')
                    }
                    append("\u001B[0m")
                }.also(::println)
            }

            // TODO: Eventually remove the stack trace
            println()
            val trace = error.stackTrace.toMutableList().drop(0)
            for (part in trace) {
                print("\u001B[31m")
                print("\tat ")
                print(part)
                println("\u001B[0m")
            }
        }
    }
}
