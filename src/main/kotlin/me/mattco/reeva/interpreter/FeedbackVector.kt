package me.mattco.reeva.interpreter

import me.mattco.reeva.ir.opcodes.IrOpcodeType
import me.mattco.reeva.ir.opcodes.IrOpcodeType.*
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyLocation
import me.mattco.reeva.runtime.objects.Shape
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.utils.unreachable

class FeedbackVector {
    private val slots = mutableListOf<FeedbackSlot>()

    val numSlots: Int get() = slots.size

    fun addSlot(slot: FeedbackSlot) = slots.add(slot)

    fun slot(index: Int) = slots[index]

    @Suppress("UNCHECKED_CAST")
    fun <T : FeedbackSlot> slotAs(index: Int): T = slots[index] as T
}

sealed class FeedbackSlot {
    companion object {
        fun forOpcode(opcode: IrOpcodeType): FeedbackSlot = when (opcode) {
            LdaNamedProperty, LdaKeyedProperty,
            StaNamedProperty, StaKeyedProperty -> ObjectFeedback()
            Add, Sub, Mul, Div, Mod, Exp,
            BitwiseOr, BitwiseXor, BitwiseAnd,
            ShiftLeft, ShiftRight, ShiftRightUnsigned,
            Negate, BitwiseNot -> OpFeedback()
            else -> unreachable()
        }
    }
}

class OpFeedback : FeedbackSlot() {
    val typeTree = TypeTree()
}

class ObjectFeedback : FeedbackSlot() {
    val shapes = mutableMapOf<Shape, PropertyLocation>()
}

class TypeTree {
    var type: Type = NoneType
        private set

    fun recordType(t: Type) {
        if (type == NoneType) {
            type = t
            return
        }

        when (t) {
            BooleanType -> if (type != BooleanType) type = AnyType
            StringType -> if (type != StringType) type = AnyType
            OddballType -> when (type) {
                NumberType, is RangeType -> type = NumberOrOddballType
                is OddballType -> {}
                else -> type = AnyType
            }
            is RangeType -> when (type) {
                is RangeType -> type = (type as RangeType).merge(t)
                is NumberType, is NumberOrOddballType -> {}
                is BigIntType -> type = NumberOrBigIntType
                else -> type = AnyType
            }
            NumberType -> when (type) {
                OddballType -> type = NumberOrOddballType
                is RangeType -> type = NumberType
                BigIntType -> type = BigIntOrOddballType
                NumberType -> {}
                else -> type = AnyType
            }
            NumberOrOddballType -> when (type) {
                is RangeType, NumberType, NumberOrOddballType -> {}
                OddballType -> type = NumberOrOddballType
                else -> type = AnyType
            }
            BigIntType -> when (type) {
                OddballType -> type = BigIntOrOddballType
                BigIntType, BigIntOrOddballType -> {}
                else -> type = AnyType
            }
            BigIntOrOddballType -> when (type) {
                BigIntType, OddballType -> type = BigIntOrOddballType
                BigIntOrOddballType -> {}
                else -> type = AnyType
            }
            NumberOrBigIntType -> when (type) {
                NumberType, BigIntType -> type = NumberOrBigIntType
                NumberOrBigIntType -> {}
                else -> type = AnyType
            }
            ObjectType -> if (type != ObjectType) type = AnyType
            else -> unreachable()
        }
    }
}

sealed class Type {
    abstract fun isCompatibleWith(type: Type): Boolean

    companion object {
        fun fromValue(value: JSValue) = when (value) {
            is JSBoolean -> BooleanType
            is JSString -> StringType
            JSUndefined, JSNull -> OddballType
            is JSNumber -> RangeType(value.number, value.number)
            is JSBigInt -> BigIntType
            is JSObject -> ObjectType
            else -> AnyType
        }
    }
}

object NoneType : Type() {
    override fun isCompatibleWith(type: Type) = false
}

object BooleanType : Type() {
    override fun isCompatibleWith(type: Type) = type == BooleanType
}

object StringType : Type() {
    override fun isCompatibleWith(type: Type) = type == StringType
}

object OddballType : Type() {
    override fun isCompatibleWith(type: Type) = type == OddballType
}

class RangeType(val min: Double, val max: Double) : Type() {
    override fun isCompatibleWith(type: Type) = type is RangeType && type.min >= min && type.max <= max

    fun merge(other: RangeType) = RangeType(
        kotlin.math.min(min, other.min),
        kotlin.math.max(max, other.max),
    )
}

object NumberType : Type() {
    override fun isCompatibleWith(type: Type) = type == NumberType || type is RangeType
}

object NumberOrOddballType : Type() {
    override fun isCompatibleWith(type: Type) = NumberType.isCompatibleWith(type) || type == OddballType
}

object BigIntType : Type() {
    override fun isCompatibleWith(type: Type) = type == BigIntType
}

object BigIntOrOddballType : Type() {
    override fun isCompatibleWith(type: Type) = BigIntType.isCompatibleWith(type) || type == OddballType
}

object NumberOrBigIntType : Type() {
    override fun isCompatibleWith(type: Type) = NumberType.isCompatibleWith(type) || BigIntType.isCompatibleWith(type)
}

object ObjectType : Type() {
    override fun isCompatibleWith(type: Type) = type == ObjectType
}

object AnyType : Type() {
    override fun isCompatibleWith(type: Type) = true
}
