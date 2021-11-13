package com.reevajs.reeva.parsing

import com.reevajs.reeva.ast.ASTNode
import com.reevajs.reeva.parsing.lexer.Token
import com.reevajs.reeva.parsing.lexer.TokenLocation
import com.reevajs.reeva.parsing.lexer.TokenType
import com.reevajs.reeva.utils.expect

abstract class Reporter {
    protected abstract val start: TokenLocation
    protected abstract val end: TokenLocation

    fun expected(expected: Any, actual: Any? = null): Nothing {
        if (actual != null) {
            error("expected $expected, but found $actual")
        } else error("expected $expected")
    }

    fun arrowFunctionNewLine(): Nothing =
        error("arrow function cannot be separated from it's arrow by a line terminator")
    fun baseClassSuperCall(): Nothing = error("base class cannot contain a call to super")
    fun breakOutsideOfLoopOrSwitch(): Nothing = error("break statement must be inside of a loop or switch statement")
    fun classAsyncAccessor(isGetter: Boolean): Nothing =
        error("class ${if (isGetter) "getter" else "setter"} cannot be async")
    fun classDeclarationNoName(): Nothing = error("class declaration must have an identifier")
    fun classInExpressionContext(): Nothing =
        error("class declarations are not allowed in single-statement contexts")
    fun classGeneratorAccessor(isGetter: Boolean): Nothing =
        error("class ${if (isGetter) "getter" else "setter"} cannot be a generator")
    fun classGeneratorField(): Nothing = error("class declaration field cannot be a generator")
    fun classFieldInvalidName(isStatic: Boolean, name: String): Nothing =
        error("${if (isStatic) "static " else ""}class field cannot be named \"$name\"")
    fun constMissingInitializer(): Nothing = error("const variable declaration must have an initializer")
    fun continueOutsideOfLoop(): Nothing = error("continue statement must be inside of a loop")
    fun duplicateClassConstructor(): Nothing = error("class cannot have multiple constructors")
    fun duplicateDeclaration(name: String, type: String): Nothing = error("duplicate $type declaration of \"$name\"")
    fun duplicateExport(name: String): Nothing = error("duplicate export of \"$name\"")
    fun emptyParenthesizedExpression(): Nothing = error("parenthesized expression cannot be empty")
    fun emptyTemplateLiteralExpr(): Nothing = error("empty template literal expression")
    fun expressionNotAssignable(): Nothing = error("invalid expression in assignment context")
    fun forEachMultipleDeclarations(): Nothing =
        error("for-in/of statement cannot contain multiple variable declarations")
    fun functionInExpressionContext(): Nothing =
        error("function declarations are not allowed in single-statement contexts")
    fun functionStatementNoName(): Nothing = error("function statement requires a name")
    fun identifierAfterNumericLiteral(): Nothing = error("numeric literal cannot be directly followed by an identifier")
    fun identifierStrictReservedWord(identifier: String): Nothing =
        error("\"$identifier\" is a reserved word in strict-mode code and cannot be used as an identifier")
    fun identifierReservedWord(identifier: String): Nothing =
        error("\"$identifier\" is a reserved word and cannot be used as an identifier")
    fun identifierInvalidEscapeSequence(identifier: String): Nothing =
        error("\"$identifier\" contains an invalid unicode escape sequence")
    fun invalidBreakTarget(target: String): Nothing = error("invalid break target \"$target\"")
    fun invalidContinueTarget(target: String): Nothing = error("invalid continue target \"$target\"")
    fun invalidNewMetaProperty(): Nothing = error("new.target is the only valid \"new\" meta-property")
    fun invalidShorthandProperty(): Nothing = error("object shorthand property must be an identifier name")
    fun invalidUseStrict(): Nothing =
        error("invalid \"use strict\" directive in function with non-simple parameter list")
    fun missingBindingElement(): Nothing = error("missing binding element")
    fun newTargetOutsideOfFunction(): Nothing = error("new.target is only valid inside of functions")
    fun paramAfterRest(): Nothing = error("function rest parameter must be the last parameter")
    fun restParamInitializer(): Nothing = error("rest parameter cannot have an initializer")
    fun strictAssignToArguments(): Nothing = error("cannot assign to \"arguments\" in strict-mode code")
    fun strictAssignToEval(): Nothing = error("cannot assign to \"eval\" in strict-mode code")
    fun strictImplicitOctal(): Nothing = error("implicit octal literals are not allowed in strict-mode code")
    fun stringBigUnicodeCodepointEscape(): Nothing =
        error("unicode codepoint escape sequence exceeds the maximum range (0x10ffff)")
    fun stringEmptyUnicodeEscape(): Nothing = error("invalid empty unicode escape sequence")
    fun stringInvalidHexEscape(): Nothing = error("invalid hex escape sequence in string literal")
    fun stringInvalidOctalEscape(): Nothing = error("invalid octal escape sequence in string literal")
    fun stringInvalidUnicodeNumericSeparator(): Nothing =
        error("numeric separators are not allowed in unicode escape sequences")
    fun stringUnescapedLineBreak(): Nothing = error("invalid unescaped line break in string literal")
    fun stringUnicodeCodepointMissingBrace(): Nothing =
        error("unicode codepoint escape sequence missing closing curly brace")
    fun stringUnicodeMissingDigits(): Nothing =
        error("unicode escape sequence without curly braces must contain four digits")
    fun templateLiteralAfterOptionalChain(): Nothing =
        error("unexpected template literal after optional chain")
    fun throwStatementNewLine(): Nothing =
        error("throw keyword cannot be separated from it's expression by a line terminator")
    fun unexpectedToken(token: TokenType): Nothing = error("unexpected token \"$token\"")
    fun unsupportedFeature(feature: String): Nothing = error("unsupported feature: $feature")
    fun unterminatedTemplateLiteral(): Nothing = error("unterminated template literal")
    fun unterminatedTemplateLiteralExpr(): Nothing = error("unterminated template literal expression")
    fun variableRedeclaration(name: String, oldType: String, newType: String): Nothing =
        error("cannot redeclare $oldType declaration \"$name\" as a $newType declaration")

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

    fun at(node: ASTNode) = at(node.sourceLocation.start, node.sourceLocation.end)

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
