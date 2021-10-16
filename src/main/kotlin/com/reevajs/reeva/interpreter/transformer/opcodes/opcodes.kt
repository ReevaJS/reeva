package com.reevajs.reeva.interpreter.transformer.opcodes

import com.reevajs.reeva.interpreter.transformer.FunctionInfo
import com.reevajs.reeva.interpreter.transformer.Local
import java.math.BigInteger

sealed class Opcode(val stackHeightModifier: Int) {
    override fun toString(): String {
        return this::class.simpleName!!
    }
}

// Stack manipulation

object PushEmpty : Opcode(1)

object PushNull : Opcode(1)

object PushUndefined : Opcode(1)

object PushJVMFalse : Opcode(1)

object PushJVMTrue : Opcode(1)

class PushJVMInt(val int: Int) : Opcode(1)

// literal can be: String, Int, Double, Boolean
class PushConstant(val literal: Any) : Opcode(1)

class PushBigInt(val bigint: BigInteger) : Opcode(1)

object Pop : Opcode(-1)

object Dup : Opcode(1)

object DupX1 : Opcode(1)

object DupX2 : Opcode(1)

object Swap : Opcode(0)

// Locals

class LoadInt(val local: Local) : Opcode(1)

class StoreInt(val local: Local) : Opcode(-1)

class IncInt(val local: Local) : Opcode(0)

class LoadBoolean(val local: Local) : Opcode(1)

class StoreBoolean(val local: Local) : Opcode(-1)

class LoadValue(val local: Local) : Opcode(1)

class StoreValue(val local: Local) : Opcode(-1)

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

object ToString : Opcode(0)

object Negate : Opcode(0)

object BitwiseNot : Opcode(0)

object ToBooleanLogicalNot : Opcode(0)

object Inc : Opcode(0)

object Dec : Opcode(0)

// Objects

object LoadKeyedProperty : Opcode(-1)

object StoreKeyedProperty : Opcode(-3)

// name: String | Symbol
class LoadNamedProperty(val name: Any) : Opcode(0)

// name: String | Symbol
class StoreNamedProperty(val name: Any) : Opcode(-2)

object CreateObject : Opcode(1)

object CreateArray : Opcode(1)

class StoreArray(val index: Local) : Opcode(-2)

class StoreArrayIndexed(val index: Int) : Opcode(-2)

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

class Construct(val argCount: Int) : Opcode(-1 - argCount)

object ConstructArray : Opcode(-2)

// Env

class DeclareGlobals(val vars: List<String>, val lexs: List<String>, val funcs: List<String>) : Opcode(0)

class PushDeclarativeEnvRecord(val slotCount: Int) : Opcode(0)

object PopEnvRecord : Opcode(0)

class LoadGlobal(val name: String) : Opcode(1)

class StoreGlobal(val name: String) : Opcode(-1)

class LoadCurrentEnvSlot(val slot: Int) : Opcode(-1)

class StoreCurrentEnvSlot(val slot: Int) : Opcode(-1)

class LoadEnvSlot(val slot: Int, val distance: Int) : Opcode(-1)

class StoreEnvSlot(val slot: Int, val distance: Int) : Opcode(-1)

// Jumps

sealed class JumpInstr(var to: Int, stackHeightModifier: Int = -1) : Opcode(stackHeightModifier)

class Jump(to: Int) : JumpInstr(to, 0)

class JumpIfTrue(to: Int) : JumpInstr(to)

class JumpIfFalse(to: Int) : JumpInstr(to)

class JumpIfToBooleanTrue(to: Int) : JumpInstr(to)

class JumpIfToBooleanFalse(to: Int) : JumpInstr(to)

class JumpIfUndefined(to: Int) : JumpInstr(to)

class JumpIfNotUndefined(to: Int) : JumpInstr(to)

class JumpIfNotNullish(to: Int) : JumpInstr(to)

class JumpIfNotEmpty(to: Int) : JumpInstr(to)

class JumpTable(val table: Map<Int, Int>) : JumpInstr(-1)

// Generators

object GetGeneratorPhase : Opcode(1)

class SetGeneratorPhase(val phase: Int) : Opcode(0)

object GetGeneratorSentValue : Opcode(1)

// Misc

class CopyObjectExcludingProperties(val propertiesLocal: Local) : Opcode(0)

// stack: object property method
object DefineGetterProperty : Opcode(-3)

// stack: object property method
object DefineSetterProperty : Opcode(-3)

object Throw : Opcode(-1)

class CreateRegExpObject(val source: String, val flags: String) : Opcode(1)

class CreateTemplateLiteral(val numberOfParts: Int) : Opcode(-numberOfParts + 1)

object ForInEnumerate : Opcode(0)

class CreateClosure(val ir: FunctionInfo) : Opcode(1)

class CreateClassConstructor(val info: FunctionInfo) : Opcode(1)

class CreateGeneratorClosure(val ir: FunctionInfo) : Opcode(1)

class CreateAsyncClosure(val ir: FunctionInfo) : Opcode(1)

class CreateAsyncGeneratorClosure(val ir: FunctionInfo) : Opcode(1)

object CreateRestParam : Opcode(1)

object GetSuperConstructor : Opcode(1)

object GetSuperBase : Opcode(1)

object CreateUnmappedArgumentsObject : Opcode(0)

object CreateMappedArgumentsObject : Opcode(0)

class ThrowConstantError(val message: String) : Opcode(0)

object ThrowSuperNotInitializedIfEmpty : Opcode(1)

object PushClosure : Opcode(1)

object Return : Opcode(-1)
