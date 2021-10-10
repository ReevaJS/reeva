package com.reevajs.reeva.interpreter.transformer

import com.reevajs.reeva.ast.ASTVisitor
import com.reevajs.reeva.ast.ArgumentList
import com.reevajs.reeva.ast.IdentifierNode
import com.reevajs.reeva.ast.IdentifierReferenceNode
import com.reevajs.reeva.ast.expressions.*
import com.reevajs.reeva.ast.literals.BooleanLiteralNode
import com.reevajs.reeva.ast.literals.NumericLiteralNode
import com.reevajs.reeva.ast.literals.StringLiteralNode
import com.reevajs.reeva.ast.statements.IfStatementNode
import com.reevajs.reeva.core.lifecycle.Executable
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.unreachable

class Transformer(val executable: Executable) : ASTVisitor {
    private val builder = IRBuilder()

    fun transform(): TransformerResult {
        expect(executable.script != null)

        return try {
            unsupported("TODO")
        } catch (e: NotImplementedError) {
            TransformerResult.UnsupportedError(e.message!!)
        } catch (e: Throwable) {
            TransformerResult.InternalError(e)
        }
    }

    override fun visitIfStatement(node: IfStatementNode) {
        visitExpression(node.condition)
        if (node.falseBlock == null) {
            builder.ifHelper(::JumpIfToBooleanFalse) {
                visit(node.trueBlock)
            }
        } else {
            builder.ifElseHelper(
                ::JumpIfToBooleanFalse,
                { visit(node.trueBlock) },
                { visit(node.falseBlock) },
            )
        }
    }

    override fun visitCommaExpression(node: CommaExpressionNode) {
        for (expression in node.expressions) {
            visitExpression(expression)
            +Pop
        }
    }

    override fun visitBinaryExpression(node: BinaryExpressionNode) {
        val op = when (node.operator) {
            BinaryOperator.Add -> Add
            BinaryOperator.Sub -> Sub
            BinaryOperator.Mul -> Mul
            BinaryOperator.Div -> Div
            BinaryOperator.Exp -> Exp
            BinaryOperator.Mod -> Mod
            BinaryOperator.BitwiseAnd -> BitwiseAnd
            BinaryOperator.BitwiseOr -> BitwiseOr
            BinaryOperator.BitwiseXor -> BitwiseXor
            BinaryOperator.Shl -> ShiftLeft
            BinaryOperator.Shr -> ShiftRight
            BinaryOperator.UShr -> ShiftRightUnsigned
            BinaryOperator.StrictEquals -> TestEqualStrict
            BinaryOperator.StrictNotEquals -> TestNotEqualStrict
            BinaryOperator.SloppyEquals -> TestEqual
            BinaryOperator.SloppyNotEquals -> TestNotEqual
            BinaryOperator.LessThan -> TestLessThan
            BinaryOperator.LessThanEquals -> TestLessThanOrEqual
            BinaryOperator.GreaterThan -> TestGreaterThan
            BinaryOperator.GreaterThanEquals -> TestGreaterThanOrEqual
            BinaryOperator.Instanceof -> TestInstanceOf
            BinaryOperator.In -> TestIn
            BinaryOperator.And -> {
                visit(node.lhs)
                builder.ifHelper(::JumpIfToBooleanTrue) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Or -> {
                visit(node.lhs)
                builder.ifHelper(::JumpIfToBooleanFalse) {
                    visit(node.rhs)
                }
                return
            }
            BinaryOperator.Coalesce -> {
                visit(node.lhs)
                builder.ifHelper(::JumpIfNotNullish) {
                    visit(node.rhs)
                }
                return
            }
        }

        visitExpression(node.lhs)
        visitExpression(node.rhs)
        +op
    }

    override fun visitUnaryExpression(node: UnaryExpressionNode) {
        if (node.op == UnaryOperator.Delete) {
            when (val expr = node.expression) {
                is IdentifierReferenceNode -> +PushConstant(false)
                !is MemberExpressionNode -> +PushConstant(true)
                else -> if (expr.type == MemberExpressionNode.Type.Tagged) {
                    +PushConstant(true)
                } else {
                    visitExpression(expr.lhs)

                    if (expr.type == MemberExpressionNode.Type.Computed) {
                        visit(expr.rhs)
                    } else {
                        +PushConstant((expr.rhs as IdentifierNode).name)
                    }

                    +if (node.scope.isStrict) DeletePropertyStrict else DeletePropertySloppy
                }
            }

            return
        }

        visitExpression(node.expression)

        when (node.op) {
            UnaryOperator.Void -> {
                +Pop
                +PushUndefined
            }
            UnaryOperator.Typeof -> +TypeOf
            UnaryOperator.Plus -> +ToNumber
            UnaryOperator.Minus -> +Negate
            UnaryOperator.BitwiseNot -> +BitwiseNot
            UnaryOperator.Not -> +ToBooleanLogicalNot
            else -> unreachable()
        }
    }

    override fun visitUpdateExpression(node: UpdateExpressionNode) {
        val op = if (node.isIncrement) Inc else Dec

        when (val target = node.target) {
            is IdentifierReferenceNode -> {
                visitExpression(target)
                +ToNumber

            }
        }
    }

    override fun visitMemberExpression(node: MemberExpressionNode) {
        pushMemberExpression(node, pushReceiver = false)
    }

    private fun pushMemberExpression(node: MemberExpressionNode, pushReceiver: Boolean) {
        visitExpression(node.lhs)

        if (node.isOptional) {
            builder.ifHelper(::JumpIfNotNullish) {
                +PushUndefined
            }
        }

        if (pushReceiver)
            +Dup

        when (node.type) {
            MemberExpressionNode.Type.Computed -> {
                visitExpression(node.rhs)
                +GetKeyedProperty
            }
            MemberExpressionNode.Type.NonComputed -> {
                +GetNamedProperty((node.rhs as IdentifierNode).name)
            }
            MemberExpressionNode.Type.Tagged -> TODO()
        }

        if (pushReceiver)
            +Swap
    }

    enum class ArgumentsMode {
        Spread,
        Normal,
    }

    private fun argumentsMode(arguments: ArgumentList): ArgumentsMode {
        return if (arguments.any { it.isSpread }) {
            ArgumentsMode.Spread
        } else ArgumentsMode.Normal
    }

    private fun pushArguments(arguments: ArgumentList): ArgumentsMode {
        val mode = argumentsMode(arguments)
        when (mode) {
            ArgumentsMode.Spread -> TODO()
            ArgumentsMode.Normal -> {
                for (argument in arguments)
                    visitExpression(argument)
            }
        }
        return mode
    }

    override fun visitCallExpression(node: CallExpressionNode) {
        if (node.target is MemberExpressionNode) {
            pushMemberExpression(node.target, pushReceiver = true)
        } else {
            visitExpression(node.target)
            +PushUndefined
        }

        fun buildCall() {
            if (pushArguments(node.arguments) == ArgumentsMode.Normal) {
                +Call(node.arguments.size)
            } else {
                +CallArray
            }
        }

        if (node.isOptional) {
            builder.ifElseHelper(
                ::JumpIfNotNullish,
                { +PushUndefined },
                { buildCall() },
            )
        } else {
            buildCall()
        }
    }

    override fun visitNewExpression(node: NewExpressionNode) {
        visitExpression(node.target)
        // TODO: Property new.target
        +Dup

        if (pushArguments(node.arguments) == ArgumentsMode.Normal) {
            +Construct(node.arguments.size)
        } else {
            +ConstructArray
        }
    }

    override fun visitStringLiteral(node: StringLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visitBooleanLiteral(node: BooleanLiteralNode) {
        +PushConstant(node.value)
    }

    override fun visitNullLiteral() {
        +PushNull
    }

    override fun visitNumericLiteral(node: NumericLiteralNode) {
        +PushConstant(node.value)
    }

    private fun unsupported(message: String): Nothing {
        throw NotImplementedError(message)
    }

    private operator fun Opcode.unaryPlus() {
        builder.add(this)
    }
}
