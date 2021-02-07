package me.mattco.reeva.parser

import me.mattco.reeva.ast.ASTNode

abstract class Reporter {
    protected abstract val start: TokenLocation
    protected abstract val end: TokenLocation

    fun expected(expected: Any, actual: Any? = null): Nothing {
        if (actual != null) {
            error("expected $expected, but found $actual")
        } else error("expected $expected")
    }

    fun unexpectedToken(token: TokenType): Nothing = error("unexpected token \"$token\"")
    fun invalidLhsInAssignment(): Nothing = error("invalid left-hand-side in assignment expression")
    fun strictAssignToEval(): Nothing = error("cannot assign to \"eval\" in strict-mode code")
    fun strictAssignToArguments(): Nothing = error("cannot assign to \"arguments\" in strict-mode code")
    fun functionStatementNoName(): Nothing = error("function statement requires a name")
    fun functionInExpressionContext(): Nothing = error("function declarations are not allowed in single-statement contexts")
    fun throwStatementNewLine(): Nothing = error("throw keyword cannot be separated from it's expression by a line terminator")
    fun arrowFunctionNewLine(): Nothing = error("arrow function cannot be separated from it's arrow by a line terminator")
    fun classDeclarationNoName(): Nothing = error("class declaration must have an identifier")
    fun paramAfterRest(): Nothing = error("function rest parameter must be the last parameter")
    fun strictImplicitOctal(): Nothing = error("implicit octal literals are not allowed in strict-mode code")
    fun identifierAfterNumericLiteral(): Nothing = error("numeric literal cannot be directly followed by an identifier")
    fun invalidBreakTarget(target: String): Nothing = error("invalid break target \"$target\"")
    fun invalidContinueTarget(target: String): Nothing = error("invalid continue target \"$target\"")
    fun functionMissingParameter(): Nothing = error("missing function parameter")
    fun constMissingInitializer(): Nothing = error("const variable declaration must have an initializer")
    fun continueOutsideOfLoop(): Nothing = error("continue statement must be inside of a loop")
    fun breakOutsideOfLoopOrSwitch(): Nothing = error("break statement must be inside of a loop or switch statement")
    fun forEachMultipleDeclarations(): Nothing = error("for-in/of statement cannot contain multiple variable declarations")
    fun emptyTemplateLiteralExpr(): Nothing = error("empty template literal expression")
    fun unterminatedTemplateLiteralExpr(): Nothing = error("unterminated template literal expression")
    fun unterminatedTemplateLiteral(): Nothing = error("unterminated template literal")
    fun restParamInitializer(): Nothing = error("rest parameter cannot have an initializer")

    fun error(message: String): Nothing {
        throw Parser.ParsingException(message, start, end)
    }
}

class ErrorReporter(private val parser: Parser) : Reporter() {
    override val start: TokenLocation
        get() = parser.sourceStart
    override val end: TokenLocation
        get() = parser.sourceEnd

    fun at(
        sourceStart: TokenLocation = start,
        sourceEnd: TokenLocation = end
    ) = object : Reporter() {
        override val start = sourceStart
        override val end = sourceEnd
    }

    fun at(node: ASTNode) = object : Reporter() {
        override val start = node.sourceStart
        override val end = node.sourceEnd
    }
}
