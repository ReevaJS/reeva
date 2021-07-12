package me.mattco.reeva.parsing.lexer

enum class TokenType(val string: String, val category: Category = Category.None) {
    Add("+", Category.Operator),
    AddEquals("+=", Category.Operator),
    And("&&", Category.Operator),
    AndEquals("&&=", Category.Operator),
    Arrow("=>", Category.Operator),
    BitwiseAnd("&", Category.Operator),
    BitwiseAndEquals("&=", Category.Operator),
    BitwiseNot("~", Category.Operator),
    BitwiseOr("|", Category.Operator),
    BitwiseOrEquals("|=", Category.Operator),
    BitwiseXor("^", Category.Operator),
    BitwiseXorEquals("^=", Category.Operator),
    QuestionMark("?", Category.Operator),
    Dec("--", Category.Operator),
    Div("/", Category.Operator),
    DivEquals("/=", Category.Operator),
    Equals("=", Category.Operator),
    Exp("**", Category.Operator),
    ExpEquals("**=", Category.Operator),
    GreaterThan(">", Category.Operator),
    GreaterThanEquals(">=", Category.Operator),
    Hash("#", Category.Operator),
    Inc("++", Category.Operator),
    LessThan("<", Category.Operator),
    LessThanEquals("<=", Category.Operator),
    Mod("%", Category.Operator),
    ModEquals("%=", Category.Operator),
    Mul("*", Category.Operator),
    MulEquals("*=", Category.Operator),
    Not("!", Category.Operator),
    Coalesce("??", Category.Operator),
    CoalesceEquals("??=", Category.Operator),
    OptionalChain("?.", Category.Operator),
    Or("||", Category.Operator),
    OrEquals("||=", Category.Operator),
    Period(".", Category.Operator),
    Shl("<<", Category.Operator),
    ShlEquals("<<=", Category.Operator),
    Shr(">>", Category.Operator),
    ShrEquals(">>=", Category.Operator),
    SloppyEquals("==", Category.Operator),
    SloppyNotEquals("!=", Category.Operator),
    StrictEquals("===", Category.Operator),
    StrictNotEquals("!==", Category.Operator),
    Sub("-", Category.Operator),
    SubEquals("-=", Category.Operator),
    TriplePeriod("...", Category.Operator),
    UShr(">>>", Category.Operator),
    UShrEquals(">>>=", Category.Operator),

    CloseBracket("]", Category.Symbol),
    CloseCurly("}", Category.Symbol),
    CloseParen(")", Category.Symbol),
    Colon(":", Category.Symbol),
    Comma(",", Category.Symbol),
    OpenBracket("[", Category.Symbol),
    OpenCurly("{", Category.Symbol),
    OpenParen("(", Category.Symbol),
    Semicolon(";", Category.Symbol),

    Async("async", Category.Keyword),
    Await("await", Category.Keyword),
    Break("break", Category.Keyword),
    Case("case", Category.Keyword),
    Catch("catch", Category.Keyword),
    Class("class", Category.Keyword),
    Const("const", Category.Keyword),
    Continue("continue", Category.Keyword),
    Debugger("debugger", Category.Keyword),
    Default("default", Category.Keyword),
    Delete("delete", Category.Keyword),
    Do("do", Category.Keyword),
    Else("else", Category.Keyword),
    Enum("enum", Category.Keyword),
    Export("export", Category.Keyword),
    Extends("extends", Category.Keyword),
    False("false", Category.Keyword),
    Finally("finally", Category.Keyword),
    For("for", Category.Keyword),
    Function("function", Category.Keyword),
    If("if", Category.Keyword),
    Import("import", Category.Keyword),
    Implements("implements", Category.Keyword),
    In("in", Category.Keyword),
    Instanceof("instanceof", Category.Keyword),
    Interface("interface", Category.Keyword),
    Let("let", Category.Keyword),
    New("new", Category.Keyword),
    NullLiteral("null", Category.Keyword),
    Package("package", Category.Keyword),
    Private("private", Category.Keyword),
    Protected("protected", Category.Keyword),
    Public("public", Category.Keyword),
    Return("return", Category.Keyword),
    Super("super", Category.Keyword),
    Static("static", Category.Keyword),
    Switch("switch", Category.Keyword),
    This("this", Category.Keyword),
    Throw("throw", Category.Keyword),
    True("true", Category.Keyword),
    Try("try", Category.Keyword),
    Typeof("typeof", Category.Keyword),
    Var("var", Category.Keyword),
    Void("void", Category.Keyword),
    While("while", Category.Keyword),
    With("with", Category.Keyword),
    Yield("yield", Category.Keyword),

    NumericLiteral("number"),
    BigIntLiteral("BigInt literal"),

    TemplateLiteralStart("template literal"),
    TemplateLiteralEnd("THIS SHOULD NEVER BE USED"),
    TemplateLiteralExprStart("template literal expression"),
    TemplateLiteralExprEnd("THIS SHOULD NEVER BE USED"),
    TemplateLiteralString("THIS SHOULD NEVER BE USED"),

    RegexFlags("THIS SHOULD NEVER BE USED"),
    RegExpLiteral("regex literal"),
    StringLiteral("string literal"),
    UnterminatedStringLiteral("THIS SHOULD NEVER BE USED"),
    UnterminatedTemplateLiteral("THIS SHOULD NEVER BE USED"),
    UnterminatedRegexLiteral("THIS SHOULD NEVER BE USED"),

    Identifier("identifier"),

    Eof("THIS SHOULD NEVER BE USED"),
    Invalid("THIS SHOULD NEVER BE USED");

    var isUnaryToken = false
        private set
    var isExpressionToken = false
        private set
    var isSecondaryToken = false
        private set
    var isStatementToken = false
        private set
    var isDeclarationToken = false
        private set
    var isVariableDeclarationToken = false
        private set
    var isIdentifierNameToken = false
        private set
    var isPropertyKeyToken = false
        private set
    var operatorPrecedence = -1
        private set
    var leftAssociative = false
        private set

    override fun toString() = string

    enum class Category {
        Operator,
        Keyword,
        Symbol,
        None,
    }

    companion object {
        private val unaryPrefixTokens = setOf(
            Inc,
            Dec,
            Not,
            BitwiseNot,
            Add,
            Sub,
            Typeof,
            Void,
            Delete,
        )

        private val expressionTokens = setOf(
            True,
            False,
            NumericLiteral,
            BigIntLiteral,
            StringLiteral,
            TemplateLiteralStart,
            NullLiteral,
            Identifier,
            New,
            OpenCurly,
            OpenBracket,
            OpenParen,
            Function,
            Async,
            Await,
            Class,
            This,
            Super,
            RegExpLiteral,
            Yield,
            *unaryPrefixTokens.toTypedArray(),
        )

        private val secondaryTokens = setOf(
            Add,
            Sub,
            Mul,
            Div,
            Mod,
            Exp,
            BitwiseAnd,
            BitwiseOr,
            BitwiseXor,
            Shl,
            Shr,
            UShr,
            And,
            Or,
            Coalesce,
            Equals,
            AddEquals,
            SubEquals,
            MulEquals,
            DivEquals,
            ModEquals,
            ExpEquals,
            BitwiseAndEquals,
            BitwiseOrEquals,
            BitwiseXorEquals,
            ShlEquals,
            ShrEquals,
            UShrEquals,
            AndEquals,
            OrEquals,
            CoalesceEquals,
            SloppyEquals,
            SloppyNotEquals,
            StrictEquals,
            StrictNotEquals,
            GreaterThan,
            GreaterThanEquals,
            LessThan,
            LessThanEquals,
            OpenParen,
            Period,
            OpenBracket,
            Inc,
            Dec,
            In,
            Instanceof,
            QuestionMark,
            OptionalChain,
        )

        private val statementTokens = setOf(
            Return,
            Do,
            If,
            Throw,
            Try,
            While,
            With,
            For,
            OpenCurly,
            Switch,
            Break,
            Continue,
            Var,
            Let,
            Const,
            Debugger,
            Semicolon,
            *expressionTokens.toTypedArray(),
        )

        private val declarationTokens = setOf(
            Function,
            Class,
            Const,
            Let,
        )

        private val variableDeclarationTokens = setOf(
            Var,
            Let,
            Const,
        )

        private val identifierNameTokens = setOf(
            Await,
            Break,
            Case,
            Catch,
            Class,
            Const,
            Continue,
            Debugger,
            Default,
            Delete,
            Do,
            Else,
            Enum,
            Export,
            Extends,
            False,
            Finally,
            For,
            Function,
            Identifier,
            If,
            Import,
            In,
            Instanceof,
            Interface,
            Let,
            New,
            NullLiteral,
            Return,
            Super,
            Switch,
            This,
            Throw,
            True,
            Try,
            Typeof,
            Var,
            Void,
            While,
            Yield,
        )

        private val propertyKeyTokens = setOf(
            OpenBracket,
            StringLiteral,
            NumericLiteral,
            BigIntLiteral,
            *identifierNameTokens.toTypedArray()
        )

        private val operatorPrecedences = mutableMapOf(
            OpenParen to 21,
            Period to 20,
            OpenBracket to 20,
            OptionalChain to 20,
            New to 19,
            Inc to 18,
            Dec to 18,
            Not to 17,
            BitwiseNot to 17,
            Typeof to 17,
            Void to 17,
            Delete to 17,
            Await to 17,
            Exp to 16,
            Mul to 15,
            Div to 15,
            Mod to 15,
            Add to 14,
            Sub to 14,
            Shl to 13,
            Shr to 13,
            UShr to 13,
            LessThan to 12,
            LessThanEquals to 12,
            GreaterThan to 12,
            GreaterThanEquals to 12,
            In to 12,
            Instanceof to 12,
            SloppyEquals to 11,
            SloppyNotEquals to 11,
            StrictEquals to 11,
            StrictNotEquals to 11,
            BitwiseAnd to 10,
            BitwiseXor to 9,
            BitwiseOr to 8,
            Coalesce to 7,
            And to 6,
            Or to 5,
            QuestionMark to 4,
            Equals to 3,
            AddEquals to 3,
            SubEquals to 3,
            MulEquals to 3,
            ExpEquals to 3,
            MulEquals to 3,
            DivEquals to 3,
            ModEquals to 3,
            ShlEquals to 3,
            ShrEquals to 3,
            UShrEquals to 3,
            BitwiseAndEquals to 3,
            BitwiseOrEquals to 3,
            BitwiseXorEquals to 3,
            AndEquals to 3,
            OrEquals to 3,
            CoalesceEquals to 3,
            Yield to 2,
            Comma to 1,
        )

        private val leftAssociativeTokens = setOf(
            Period,
            OpenBracket,
            OpenParen,
            OptionalChain,
            Mul,
            Div,
            Mod,
            Add,
            Sub,
            Shl,
            Shr,
            UShr,
            LessThan,
            LessThanEquals,
            GreaterThan,
            GreaterThanEquals,
            In,
            Instanceof,
            StrictEquals,
            StrictNotEquals,
            SloppyEquals,
            SloppyNotEquals,
            Typeof,
            Void,
            Delete,
            BitwiseAnd,
            BitwiseOr,
            BitwiseXor,
            Coalesce,
            And,
            Or,
            Comma
        )

        init {
            values().forEach {
                it.isUnaryToken = it in unaryPrefixTokens
                it.isExpressionToken = it in expressionTokens
                it.isSecondaryToken = it in secondaryTokens
                it.isStatementToken = it in statementTokens
                it.isDeclarationToken = it in declarationTokens
                it.isVariableDeclarationToken = it in variableDeclarationTokens
                it.isIdentifierNameToken = it in identifierNameTokens
                it.isPropertyKeyToken = it in propertyKeyTokens
                it.operatorPrecedence = operatorPrecedences[it] ?: -1
                it.leftAssociative = (it in leftAssociativeTokens) ?: false
            }
        }
    }
}
