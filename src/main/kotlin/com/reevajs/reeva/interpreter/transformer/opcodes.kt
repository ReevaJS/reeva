package com.reevajs.reeva.interpreter.transformer

sealed class Opcode(val stackHeightModifier: Int)

// Stack manipulation

object PushNull : Opcode(1)

object PushUndefined : Opcode(1)

// literal can be: String, Int, Double, Boolean
class PushConstant(val literal: Any) : Opcode(1)

object Pop : Opcode(-1)

object Dup : Opcode(1)

object Swap : Opcode(1)

// Locals

class IntStore(val slot: Int) : Opcode(-1)

class IntLoad(val slot: Int) : Opcode(1)

class IntInc(val slot: Int) : Opcode(0)

class ValueStore(val slot: Int) : Opcode(-1)

class ValueLoad(val slot: Int) : Opcode(1)

// Operations

object Add : Opcode(-1)

object Sub : Opcode(-1)

object Mul : Opcode(-1)

object Div : Opcode(-1)

object Exp : Opcode(-1)

object Mod : Opcode(-1)

object BitwiseAnd : Opcode(-1)

object BitwiseOr : Opcode(-1)

object BitwiseXor : Opcode(-1)

object ShiftLeft : Opcode(-1)

object ShiftRight : Opcode(-1)

object ShiftRightUnsigned : Opcode(-1)

object TestEqualStrict : Opcode(-1)
object TestNotEqualStrict : Opcode(-1)
object TestEqual : Opcode(-1)
object TestNotEqual : Opcode(-1)
object TestLessThan : Opcode(-1)
object TestLessThanOrEqual : Opcode(-1)
object TestGreaterThan : Opcode(-1)
object TestGreaterThanOrEqual : Opcode(-1)
object TestInstanceOf : Opcode(-1)
object TestIn : Opcode(-1)

object TypeOf : Opcode(0)

object ToNumber : Opcode(0)

object ToNumeric : Opcode(0)

object Negate : Opcode(0)

object BitwiseNot : Opcode(0)

object LogicalNot : Opcode(0)

object ToBooleanLogicalNot : Opcode(0)

object Inc : Opcode(0)

object Dec : Opcode(0)

// Objects

object GetKeyedProperty : Opcode(-1)

class GetNamedProperty(val name: String) : Opcode(0)

object CreateObject : Opcode(1)

object CreateArray : Opcode(1)

object StoreArray : Opcode(-2)

object DeletePropertyStrict : Opcode(-2)

object DeletePropertySloppy : Opcode(-2)

// Iterators

object GetIterator : Opcode(0)

object IteratorNext : Opcode(0)

object IteratorResultDone : Opcode(0)

object IteratorResultValue : Opcode(0)

// Call/construct

class Call(val argCount: Int) : Opcode(-1 - argCount)

object CallArray : Opcode(-2)

class Construct(val argCount : Int) : Opcode(-1 - argCount)

object ConstructArray : Opcode(-2)

// Jumps

sealed class JumpInstr(var to: Int) : Opcode(-1)

class Jump(to: Int) : JumpInstr(to)

class JumpIfTrue(to: Int) : JumpInstr(to)

class JumpIfFalse(to: Int) : JumpInstr(to)

class JumpIfToBooleanTrue(to: Int) : JumpInstr(to)

class JumpIfToBooleanFalse(to: Int) : JumpInstr(to)

class JumpIfNotNullish(to: Int) : JumpInstr(to)
