package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.transformer.Block

typealias Register = Int
typealias Index = Int
typealias Literal = Int

sealed class Opcode(val isTerminator: Boolean = false, val isThrowing: Boolean = false) {
    open fun replaceReferences(from: Block, to: Block) {}
}

/////////////////
/// CONSTANTS ///
/////////////////

object LdaEmpty : Opcode()
object LdaUndefined : Opcode()
object LdaNull : Opcode()
object LdaTrue : Opcode()
object LdaFalse : Opcode()
object LdaZero : Opcode()
class LdaConstant(val index: Index) : Opcode()
class LdaInt(val int: Literal) : Opcode()

object LdaClosure : Opcode()

///////////////////////////
/// REGISTER OPERATIONS ///
///////////////////////////

/**
 * Load the value in a register into the accumulator
 *
 * reg: the register containing the value to load into the accumulator
 */
class Ldar(val reg: Register) : Opcode()

/**
 * Store the value in the accumulator into a register
 *
 * reg: the register which the accumulator will be stored to
 */
class Star(val reg: Register) : Opcode()

/**
 * Load a literal property from an object into the accumulator.
 *
 * objectReg: the register containing the object
 * nameIndex: the constant pool index of the name. Must be a string literal
 */
class LdaNamedProperty(val objectReg: Register, val nameIndex: Index) : Opcode(isThrowing = true)

/**
 * Load a computed property from an object into the accumulator.
 *
 * accumulator: the computed property value
 * objectReg: the register containing object
 */
class LdaKeyedProperty(val objectReg: Register) : Opcode(isThrowing = true)

/**
 * Store a literal property into an object.
 *
 * accumulator: the value to store into the object property
 * objectReg: the register containing the object
 * nameIndex: the constant pool index of the name. Must be a string literal
 */
class StaNamedProperty(val objectReg: Register, val nameIndex: Index) : Opcode(isThrowing = true)

/**
 * Store a computed property into an object.
 *
 * accumulator: the value to store into the object property
 * objectReg: the register containing the object
 * nameReg: the register containing the computed property value
 */
class StaKeyedProperty(val objectReg: Register, val nameReg: Register) : Opcode(isThrowing = true)

/////////////////////////////
/// OBJECT/ARRAY LITERALS ///
/////////////////////////////

/**
 * Creates an empty array object and loads it into the accumulator
 */
object CreateArray : Opcode()

/**
 * Store a value into an array.
 *
 * accumulator: the value to store into the array
 * arrayReg: the register containing the array
 * index: the literal array index to insert the value into
 */
class StaArrayIndex(val arrayReg: Register, val index: Literal) : Opcode(isThrowing = true)

/**
 * Store a value into an array.
 *
 * accumulator: the value to store into the array
 * arrayReg: the register containing the array
 * indexReg: the literal array index to insert the value into
 */
class StaArray(val arrayReg: Register, val indexReg: Register) : Opcode(isThrowing = true)

/**
 * Creates an empty object and loads it into the accumulator
 */
object CreateObject : Opcode()

/////////////////////////
/// BINARY OPERATIONS ///
/////////////////////////

/*
 * All of the following binary opcodes perform their respective
 * operations between two values and load the result into the
 * accumulator. The first argument hold the LHS value of the
 * operation, and the accumulator holds the RHS value.
 */

class Add(val lhsReg: Register) : Opcode(isThrowing = true)
class Sub(val lhsReg: Register) : Opcode(isThrowing = true)
class Mul(val lhsReg: Register) : Opcode(isThrowing = true)
class Div(val lhsReg: Register) : Opcode(isThrowing = true)
class Mod(val lhsReg: Register) : Opcode(isThrowing = true)
class Exp(val lhsReg: Register) : Opcode(isThrowing = true)
class BitwiseOr(val lhsReg: Register) : Opcode(isThrowing = true)
class BitwiseXor(val lhsReg: Register) : Opcode(isThrowing = true)
class BitwiseAnd(val lhsReg: Register) : Opcode(isThrowing = true)
class ShiftLeft(val lhsReg: Register) : Opcode(isThrowing = true)
class ShiftRight(val lhsReg: Register) : Opcode(isThrowing = true)
class ShiftRightUnsigned(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Increments the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object Inc : Opcode(isThrowing = true)

/**
 * Decrements the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object Dec : Opcode(isThrowing = true)

/**
 * Negates the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object Negate : Opcode(isThrowing = true)

/**
 * Bitwise-nots the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object BitwiseNot : Opcode(isThrowing = true)

/**
 * Appends the string in the accumulator to another string.
 *
 * accumulator: the RHS of the string concatentation operation
 * lhsStringReg: the register with the string that will be the LHS of the
 *               string concatenation operation
 */
class StringAppend(val lhsStringReg: Register) : Opcode()

/**
 * Converts the accumulator to a boolean using the ToBoolean
 * operation, then inverts it.
 */
object ToBooleanLogicalNot : Opcode(isThrowing = true)

/**
 * Inverts the boolean in the accumulator.
 */
object LogicalNot : Opcode()

/**
 * Load the accumulator with the string respresentation of the
 * type currently in the accumulator
 */
object TypeOf : Opcode()

/**
 * Delete a property following strict-mode semantics.
 *
 * accumulator: the property which will be deleted
 * objectReg: the register containing the target object
 */
class DeletePropertyStrict(val objectReg: Register) : Opcode()

/**
 * Delete a property following sloppy-mode semantics.
 *
 * accumulator: the property which will be deleted
 * objectReg: the register containing the target object
 */
class DeletePropertySloppy(val objectReg: Register) : Opcode(isThrowing = true)

/////////////
/// SCOPE ///
/////////////

/**
 * Loads a global variable into the accumulator
 *
 * name: the name of the global variable
 */
class LdaGlobal(val name: Index) : Opcode(isThrowing = true)

/**
 * Stores the value in the accumulator into a global variable.
 *
 * name: the name of the global variable
 */
class StaGlobal(val name: Index) : Opcode(isThrowing = true)

class LdaCurrentRecordSlot(val slot: Literal) : Opcode()

class StaCurrentRecordSlot(val slot: Literal) : Opcode()

class LdaRecordSlot(val slot: Literal, val distance: Literal) : Opcode()

class StaRecordSlot(val slot: Literal, val distance: Literal) : Opcode()

object PushWithEnvRecord : Opcode()

class PushDeclarativeEnvRecord(val numSlots: Literal) : Opcode()

object PopEnvRecord : Opcode()

///////////////
/// CALLING ///
///////////////

/**
 * Calls a value.
 *
 * targetReg: the target of the call
 * receiverReg: the receiver
 * argumentRegs: a variable number of argument registers
 */
class Call(val targetReg: Register, val receiverReg: Register, val argumentRegs: List<Register>) :
    Opcode(isThrowing = true)

/**
 * Calls a value with the arguments in an array.
 *
 * targetReg: the target of the call
 * receiverReg: the register containing the receiver
 * argumentReg: the register containing the array of arguments
 */
class CallWithArgArray(val targetReg: Register, val receiverReg: Register, val argumentsReg: Register) :
    Opcode(isThrowing = true)

/////////////////////
/// CONSTRUCTIONS ///
/////////////////////

/**
 * Constructs a value.
 *
 * targetReg: the target of the call
 * newTargetReg: the register containing the new.target
 * argumentReg: the register containing the array of arguments
 */
class Construct(val targetReg: Register, val newTargetReg: Register, val argumentRegs: List<Register>) :
    Opcode(isThrowing = true)

/**
 * Constructs a value with the arguments in an array.
 *
 * targetReg: the target of the call
 * newTargetReg: the register containing the new.target
 * argumentReg: the register containing the array of arguments
 */
class ConstructWithArgArray(val targetReg: Register, val newTargetReg: Register, val argumentsReg: Register) :
    Opcode(isThrowing = true)

///////////////
/// TESTING ///
///////////////

/**
 * Tests if a value is weakly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestEqual(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Tests if a value is not weakly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestNotEqual(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Tests if a value is strictly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestEqualStrict(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is strictly not equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestNotEqualStrict(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is less than the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestLessThan(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Tests if a value is greater than the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestGreaterThan(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Tests if a value is less than or equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestLessThanOrEqual(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Tests if a value is greater than or equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestGreaterThanOrEqual(val lhsReg: Register) : Opcode(isThrowing = true)

/**
 * Tests if a value is the same object in the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestReferenceEqual(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is an instance of the value in the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestInstanceOf(val lhsReg: Register) : Opcode(isThrowing = false)

/**
 * Tests if a value is 'in' the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestIn(val lhsReg: Register) : Opcode(isThrowing = false)

/**
 * Tests if a value is nullish
 *
 * accumulator: the rhs of the operation
 */
object TestNullish : Opcode()

/**
 * Tests if a value is null
 *
 * accumulator: the rhs of the operation
 */
object TestNull : Opcode()

/**
 * Tests if a value is undefined
 *
 * accumulator: the rhs of the operation
 */
object TestUndefined : Opcode()

///////////////////
/// CONVERSIONS ///
///////////////////

/**
 * Convert the accumulator to a boolean using ToBoolean()
 */
object ToBoolean : Opcode(isThrowing = true)

/**
 * Convert the accumulator to a number using ToNumber()
 */
object ToNumber : Opcode(isThrowing = true)

/**
 * Convert the accumulator to a number using ToNumeric()
 */
object ToNumeric : Opcode(isThrowing = true)

/**
 * Convert the accumulator to an object using ToObject()
 */
object ToObject : Opcode(isThrowing = true)

/**
 * Convert the accumulator to a string using ToString()
 */
object ToString : Opcode(isThrowing = true)

/////////////
/// JUMPS ///
/////////////

abstract class Jump(var ifBlock: Block, var elseBlock: Block? = null) : Opcode(isTerminator = true) {
    override fun replaceReferences(from: Block, to: Block) {
        if (ifBlock == from)
            ifBlock = to
        if (elseBlock == from)
            elseBlock = to
    }
}

class JumpAbsolute(ifBlock: Block) : Jump(ifBlock, null)
class JumpIfTrue(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfToBooleanTrue(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfEmpty(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfUndefined(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfNullish(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)

class JumpFromTable(val table: Index) : Opcode(isTerminator = true)

////////////////////////////
/// SPECIAL CONTROL FLOW ///
////////////////////////////

/**
 * Return the value in the accumulator
 */
object Return : Opcode(isTerminator = true)

class Yield(var continuationBlock: Block) : Opcode(isTerminator = true) {
    override fun replaceReferences(from: Block, to: Block) {
        if (continuationBlock == from)
            continuationBlock = to
    }
}

class Await(var continuationBlock: Block) : Opcode(isTerminator = true) {
    override fun replaceReferences(from: Block, to: Block) {
        if (continuationBlock == from)
            continuationBlock = to
    }
}

/**
 * Throws the value in the accumulator
 */
object Throw : Opcode(isTerminator = true, isThrowing = true)

class ThrowConstantError(val message: Index) : Opcode(isTerminator = true)


///////////////
/// CLASSES ///
///////////////

class CreateClass(
    val classDescriptorIndex: Index,
    val constructor: Register,
    val superClass: Register,
    val args: List<Register>,
) : Opcode()

class CreateClassConstructor(val functionInfoIndex: Index) : Opcode()

object GetSuperConstructor : Opcode(isThrowing = true)

object GetSuperBase : Opcode()

object ThrowSuperNotInitializedIfEmpty : Opcode(isThrowing = true)

object ThrowSuperInitializedIfNotEmpty : Opcode(isThrowing = true)

/////////////
/// OTHER ///
/////////////

/**
 * Defines a getter function on an object
 *
 * objectReg: the target object register
 * nameReg: the property name register
 * methodReg: the method register
 */
class DefineGetterProperty(val objectReg: Register, val nameReg: Register, val methodReg: Register) :
    Opcode(isThrowing = true)

/**
 * Defines a setter function on an object
 *
 * objectReg: the target object register
 * nameReg: the property name register
 * methodReg: the method register
 */
class DefineSetterProperty(val objectReg: Register, val nameReg: Register, val methodReg: Register) :
    Opcode(isThrowing = true)

/**
 * Declare global names
 */
class DeclareGlobals(val declarationsIndex: Index) : Opcode()

/**
 * Creates a mapped arguments objects and inserts it into the scope
 * of the current function
 */
object CreateMappedArgumentsObject : Opcode()

/**
 * Creates an unmapped arguments objects and inserts it into the scope
 * of the current function
 */
object CreateUnmappedArgumentsObject : Opcode()

/**
 * Sets the accumulator to the result of calling
 * <accumulator>[Symbol.iterator]()
 */
object GetIterator : Opcode(isThrowing = true)

object IteratorNext : Opcode(isThrowing = true)

object IteratorResultDone : Opcode(isThrowing = true)

object IteratorResultValue : Opcode(isThrowing = true)

object ForInEnumerate : Opcode(isThrowing = true)

/**
 * Creates a function object, referencing a FunctionInfo object stored
 * in the constant pool
 *
 * functionInfoIndex: the constant pool index entry with the FunctionInfo object
 */
class CreateClosure(val functionInfoIndex: Index) : Opcode()

/**
 * Creates a generator function object, referencing a FunctionInfo object stored
 * in the constant pool
 *
 * functionInfoIndex: the constant pool index entry with the FunctionInfo object
 */
class CreateGeneratorClosure(val functionInfoIndex: Index) : Opcode()

/**
 * Creates an async function object, referencing a FunctionInfo object stored
 * in the constant pool
 *
 * functionInfoIndex: the constant pool index entry with the FunctionInfo object
 */
class CreateAsyncClosure(val functionInfoIndex: Index) : Opcode()

/**
 * Collects excess parameters into an array
 */
object CreateRestParam : Opcode(isThrowing = true)

/**
 * TODO
 */
object DebugBreakpoint : Opcode()
