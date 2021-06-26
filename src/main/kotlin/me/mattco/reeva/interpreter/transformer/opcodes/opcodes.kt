package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.transformer.Block

typealias Register = Int
typealias Index = Int
typealias Literal = Int

sealed class Opcode {
    open val isTerminator = false
}

/////////////////
/// CONSTANTS ///
/////////////////

object LdaTrue : Opcode()
object LdaFalse : Opcode()
object LdaUndefined : Opcode()
object LdaNull : Opcode()
object LdaZero : Opcode()
class LdaConstant(val index: Index) : Opcode()
class LdaInt(val int: Literal) : Opcode()

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
class LdaNamedProperty(val objectReg: Register, val nameIndex: Index) : Opcode()

/**
 * Load a computed property from an object into the accumulator.
 *
 * accumulator: the computed property value
 * objectReg: the register containing object
 */
class LdaKeyedProperty(val objectReg: Register) : Opcode()

/**
 * Store a literal property into an object.
 *
 * accumulator: the value to store into the object property
 * objectReg: the register containing the object
 * nameIndex: the constant pool index of the name. Must be a string literal
 */
class StaNamedProperty(val objectReg: Register, val nameIndex: Index) : Opcode()

/**
 * Store a computed property into an object.
 *
 * accumulator: the value to store into the object property
 * objectReg: the register containing the object
 * nameReg: the register containing the computed property value
 */
class StaKeyedProperty(val objectReg: Register, val nameReg: Register) : Opcode()

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
class StaArrayIndex(val arrayReg: Register, val index: Literal) : Opcode()

/**
 * Store a value into an array.
 *
 * accumulator: the value to store into the array
 * arrayReg: the register containing the array
 * indexReg: the literal array index to insert the value into
 */
class StaArray(val arrayReg: Register, val indexReg: Register) : Opcode()

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

class Add(val lhsReg: Register) : Opcode()
class Sub(val lhsReg: Register) : Opcode()
class Mul(val lhsReg: Register) : Opcode()
class Div(val lhsReg: Register) : Opcode()
class Mod(val lhsReg: Register) : Opcode()
class Exp(val lhsReg: Register) : Opcode()
class BitwiseOr(val lhsReg: Register) : Opcode()
class BitwiseXor(val lhsReg: Register) : Opcode()
class BitwiseAnd(val lhsReg: Register) : Opcode()
class ShiftLeft(val lhsReg: Register) : Opcode()
class ShiftRight(val lhsReg: Register) : Opcode()
class ShiftRightUnsigned(val lhsReg: Register) : Opcode()

/**
 * Increments the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object Inc : Opcode()

/**
 * Decrements the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object Dec : Opcode()

/**
 * Negates the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object Negate : Opcode()

/**
 * Bitwise-nots the value in the accumulator. This is NOT a generic
 * operation; the value in the accumulator must be numeric.
 */
object BitwiseNot : Opcode()

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
object ToBooleanLogicalNot : Opcode()

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
class DeletePropertySloppy(val objectReg: Register) : Opcode()

/////////////
/// SCOPE ///
/////////////

/**
 * Loads a global variable into the accumulator
 *
 * name: the name of the global variable
 */
class LdaGlobal(val name: Index) : Opcode()

/**
 * Stores the value in the accumulator into a global variable.
 *
 * name: the name of the global variable
 */
class StaGlobal(val name: Index) : Opcode()

/**
 * Loads a named variable from the current environment into the
 * accumulator.
 *
 * name: the index in the constant pool for the name of the binding
 */
class LdaCurrentEnv(val name: Index) : Opcode()

/**
 * Stores the accumulator into a named variable in the current
 * environment.
 *
 * accumulator: the value to store
 * name: the index in the constant pool for the name of the binding
 */
class StaCurrentEnv(val name: Index) : Opcode()

/**
 * Loads a named variable from a parent environment into the
 * accumulator.
 *
 * name: the index in the constant pool for the name of the binding
 * offset: the distance up the environment stack of the target
 *         environment (0 would be the current environment)
 */
class LdaEnv(val name: Index, val offset: Literal) : Opcode()

/**
 * Stores a value into a named variable in a parent environment.
 *
 * name: the index in the constant pool for the name of the binding
 * offset: the distance up the environment stack of the target
 *         environment (0 would be the current environment)
 */
class StaEnv(val name: Index, val offset: Literal) : Opcode()

/**
 * Pushes a DeclarativeEnvRecord onto the env stack
 */
object PushLexicalEnv : Opcode()

/**
 * Pops an EnvRecord off of the env stack
 */
object PopLexicalEnv : Opcode()

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
class Call(val targetReg: Register, val receiverReg: Register, val argumentRegs: List<Register>) : Opcode()

/**
 * Calls a value with the arguments in an array.
 *
 * targetReg: the target of the call
 * receiverReg: the register containing the receiver
 * argumentReg: the register containing the array of arguments
 */
class CallWithArgArray(val targetReg: Register, val receiverReg: Register, val argumentsReg: Register) : Opcode()

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
class Construct(val targetReg: Register, val newTargetReg: Register, val argumentRegs: List<Register>) : Opcode()

/**
 * Constructs a value with the arguments in an array.
 *
 * targetReg: the target of the call
 * newTargetReg: the register containing the new.target
 * argumentReg: the register containing the array of arguments
 */
class ConstructWithArgArray(val targetReg: Register, val newTargetReg: Register, val argumentsReg: Register) : Opcode()

///////////////
/// TESTING ///
///////////////

/**
 * Tests if a value is weakly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestEqual(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is not weakly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestNotEqual(val lhsReg: Register) : Opcode()

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
class TestLessThan(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is greater than the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestGreaterThan(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is less than or equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestLessThanOrEqual(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is greater than or equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestGreaterThanOrEqual(val lhsReg: Register) : Opcode()

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
class TestInstanceOf(val lhsReg: Register) : Opcode()

/**
 * Tests if a value is 'in' the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestIn(val lhsReg: Register) : Opcode()

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
object ToBoolean : Opcode()

/**
 * Convert the accumulator to a number using ToNumber()
 */
object ToNumber : Opcode()

/**
 * Convert the accumulator to a number using ToNumeric()
 */
object ToNumeric : Opcode()

/**
 * Convert the accumulator to an object using ToObject()
 */
object ToObject : Opcode()

/**
 * Convert the accumulator to a string using ToString()
 */
object ToString : Opcode()

/////////////
/// JUMPS ///
/////////////

open class Jump(val ifBlock: Block, val elseBlock: Block? = null) : Opcode() {
    override val isTerminator = true
}

class JumpIfTrue(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfToBooleanTrue(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfNull(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfUndefined(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfNullish(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)
class JumpIfObject(ifBlock: Block, elseBlock: Block) : Jump(ifBlock, elseBlock)

class JumpFromTable(val table: Index) : Opcode() {
    override val isTerminator = true
}

////////////////////////////
/// SPECIAL CONTROL FLOW ///
////////////////////////////

/**
 * Return the value in the accumulator
 */
object Return : Opcode() {
    override val isTerminator = true
}

/**
 * Throws the value in the accumulator
 */
object Throw : Opcode() {
    override val isTerminator = true
}

/**
 * Throws a const reassignment error
 */
class ThrowConstReassignment(val nameIndex: Index) : Opcode() {
    override val isTerminator = true
}

/**
 * Throws a TypeError if the accumulator is <empty>
 */
class ThrowUseBeforeInitIfEmpty(val nameIndex: Index) : Opcode() {
    override val isTerminator = true
}

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
class DefineGetterProperty(val objectReg: Register, val nameReg: Register, val methodReg: Register) : Opcode()

/**
 * Defines a setter function on an object
 *
 * objectReg: the target object register
 * nameReg: the property name register
 * methodReg: the method register
 */
class DefineSetterProperty(val objectReg: Register, val nameReg: Register, val methodReg: Register) : Opcode()

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
object GetIterator : Opcode()

object IteratorNext : Opcode()

object IteratorResultDone : Opcode()

object IteratorResultValue : Opcode()

object ForInEnumerate : Opcode()

/**
 * Creates a function object, referencing a FunctionInfo object stored
 * in the constant pool
 *
 * functionInfoIndex: the constant pool index entry with the FunctionInfo object
 */
class CreateClosure(val functionInfoIndex: Index) : Opcode()

/**
 * TODO
 */
object DebugBreakpoint : Opcode()
