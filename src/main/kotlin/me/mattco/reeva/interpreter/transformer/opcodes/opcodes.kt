package me.mattco.reeva.interpreter.transformer.opcodes

import me.mattco.reeva.interpreter.transformer.Block

typealias Register = Int
typealias Index = Int
typealias Literal = Int

sealed class Opcode(val isTerminator: Boolean = false, val isThrowing: Boolean = false) {
    open fun readRegisters(): List<Register> = emptyList()
    open fun writeRegisters(): List<Register> = emptyList()

    open fun replaceBlock(from: Block, to: Block) {}
    open fun replaceRegisters(from: Register, to: Register) {}
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
class Ldar(var reg: Register) : Opcode() {
    override fun readRegisters() = listOf(reg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (reg == from)
            reg = to
    }
}

/**
 * Store the value in the accumulator into a register
 *
 * reg: the register which the accumulator will be stored to
 */
class Star(var reg: Register) : Opcode() {
    override fun writeRegisters() = listOf(reg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (reg == from)
            reg = to
    }
}

/**
 * Load a literal property from an object into the accumulator.
 *
 * objectReg: the register containing the object
 * nameIndex: the constant pool index of the name. Must be a string literal
 */
class LdaNamedProperty(var objectReg: Register, val nameIndex: Index) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
    }
}

/**
 * Load a computed property from an object into the accumulator.
 *
 * accumulator: the computed property value
 * objectReg: the register containing object
 */
class LdaKeyedProperty(var objectReg: Register) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
    }
}

/**
 * Store a literal property into an object.
 *
 * accumulator: the value to store into the object property
 * objectReg: the register containing the object
 * nameIndex: the constant pool index of the name. Must be a string literal
 */
class StaNamedProperty(var objectReg: Register, val nameIndex: Index) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
    }
}

/**
 * Store a computed property into an object.
 *
 * accumulator: the value to store into the object property
 * objectReg: the register containing the object
 * nameReg: the register containing the computed property value
 */
class StaKeyedProperty(var objectReg: Register, var nameReg: Register) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg, nameReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
        if (nameReg == from)
            nameReg = to
    }
}

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
class StaArrayIndex(var arrayReg: Register, val index: Literal) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(arrayReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (arrayReg == from)
            arrayReg = to
    }
}

/**
 * Store a value into an array.
 *
 * accumulator: the value to store into the array
 * arrayReg: the register containing the array
 * indexReg: the literal array index to insert the value into
 */
class StaArray(var arrayReg: Register, val indexReg: Register) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(arrayReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (arrayReg == from)
            arrayReg = to
    }
}

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

sealed class BinaryOpcode(var lhsReg: Register) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(lhsReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (lhsReg == from)
            lhsReg = to
    }
}

class Add(lhsReg: Register) : BinaryOpcode(lhsReg)
class Sub(lhsReg: Register) : BinaryOpcode(lhsReg)
class Mul(lhsReg: Register) : BinaryOpcode(lhsReg)
class Div(lhsReg: Register) : BinaryOpcode(lhsReg)
class Mod(lhsReg: Register) : BinaryOpcode(lhsReg)
class Exp(lhsReg: Register) : BinaryOpcode(lhsReg)
class BitwiseOr(lhsReg: Register) : BinaryOpcode(lhsReg)
class BitwiseXor(lhsReg: Register) : BinaryOpcode(lhsReg)
class BitwiseAnd(lhsReg: Register) : BinaryOpcode(lhsReg)
class ShiftLeft(lhsReg: Register) : BinaryOpcode(lhsReg)
class ShiftRight(lhsReg: Register) : BinaryOpcode(lhsReg)
class ShiftRightUnsigned(lhsReg: Register) : BinaryOpcode(lhsReg)

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
class StringAppend(var lhsStringReg: Register) : Opcode() {
    override fun readRegisters() = listOf(lhsStringReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (lhsStringReg == from)
            lhsStringReg = to
    }
}

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
class DeletePropertyStrict(var objectReg: Register) : Opcode() {
    override fun readRegisters() = listOf(objectReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
    }
}

/**
 * Delete a property following sloppy-mode semantics.
 *
 * accumulator: the property which will be deleted
 * objectReg: the register containing the target object
 */
class DeletePropertySloppy(var objectReg: Register) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
    }
}

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
class Call(
    var targetReg: Register,
    var receiverReg: Register,
    val argumentRegs: MutableList<Register>,
) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(targetReg, receiverReg) + argumentRegs

    override fun replaceRegisters(from: Register, to: Register) {
        if (targetReg == from)
            targetReg = to
        if (receiverReg == from)
            receiverReg = to
        argumentRegs.forEachIndexed { index, reg ->
            if (reg == from)
                argumentRegs[index] = to
        }
    }
}

/**
 * Calls a value with the arguments in an array.
 *
 * targetReg: the target of the call
 * receiverReg: the register containing the receiver
 * argumentReg: the register containing the array of arguments
 */
class CallWithArgArray(
    var targetReg: Register,
    var receiverReg: Register,
    var argumentsReg: Register,
) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(targetReg, receiverReg, argumentsReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (targetReg == from)
            targetReg = to
        if (receiverReg == from)
            receiverReg = to
        if (argumentsReg == from)
            argumentsReg = to
    }
}

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
class Construct(
    var targetReg: Register,
    var newTargetReg: Register,
    val argumentRegs: MutableList<Register>,
) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(targetReg, newTargetReg) + argumentRegs

    override fun replaceRegisters(from: Register, to: Register) {
        if (targetReg == from)
            targetReg = to
        if (newTargetReg == from)
            newTargetReg = to
        argumentRegs.forEachIndexed { index, reg ->
            if (reg == from)
                argumentRegs[index] = to
        }
    }
}

/**
 * Constructs a value with the arguments in an array.
 *
 * targetReg: the target of the call
 * newTargetReg: the register containing the new.target
 * argumentReg: the register containing the array of arguments
 */
class ConstructWithArgArray(
    var targetReg: Register,
    var newTargetReg: Register,
    var argumentsReg: Register,
) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(targetReg, newTargetReg, argumentsReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (targetReg == from)
            targetReg = to
        if (newTargetReg == from)
            newTargetReg = to
        if (argumentsReg == from)
            argumentsReg = to
    }
}

///////////////
/// TESTING ///
///////////////

sealed class TestOpcode(var lhsReg: Register, isThrowing: Boolean = true) : Opcode(isThrowing = isThrowing) {
    override fun readRegisters() = listOf(lhsReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (lhsReg == from)
            lhsReg = to
    }
}

/**
 * Tests if a value is weakly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestEqual(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is not weakly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestNotEqual(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is strictly equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestEqualStrict(lhsReg: Register) : TestOpcode(lhsReg, isThrowing = false)

/**
 * Tests if a value is strictly not equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestNotEqualStrict(lhsReg: Register) : TestOpcode(lhsReg, isThrowing = false)

/**
 * Tests if a value is less than the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestLessThan(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is greater than the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestGreaterThan(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is less than or equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestLessThanOrEqual(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is greater than or equal to the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestGreaterThanOrEqual(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is the same object in the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestReferenceEqual(lhsReg: Register) : TestOpcode(lhsReg, isThrowing = false)

/**
 * Tests if a value is an instance of the value in the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestInstanceOf(lhsReg: Register) : TestOpcode(lhsReg)

/**
 * Tests if a value is 'in' the accumulator
 *
 * accumulator: the rhs of the operation
 * lhsReg: the lhs of the operation
 */
class TestIn(lhsReg: Register) : TestOpcode(lhsReg)

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
    override fun replaceBlock(from: Block, to: Block) {
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
    override fun replaceBlock(from: Block, to: Block) {
        if (continuationBlock == from)
            continuationBlock = to
    }
}

class Await(var continuationBlock: Block) : Opcode(isTerminator = true) {
    override fun replaceBlock(from: Block, to: Block) {
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
    var constructor: Register,
    var superClass: Register,
    val args: MutableList<Register>,
) : Opcode() {
    override fun readRegisters() = listOf(constructor, superClass) + args

    override fun replaceRegisters(from: Register, to: Register) {
        if (constructor == from)
            constructor = to
        if (superClass == from)
            superClass = to
        args.forEachIndexed { index, reg ->
            if (reg == from)
                args[index] = to
        }
    }
}

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
class DefineGetterProperty(
    var objectReg: Register,
    var nameReg: Register,
    var methodReg: Register,
) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg, nameReg, methodReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
        if (nameReg == from)
            nameReg = to
        if (methodReg == from)
            methodReg = to
    }
}

/**
 * Defines a setter function on an object
 *
 * objectReg: the target object register
 * nameReg: the property name register
 * methodReg: the method register
 */
class DefineSetterProperty(
    var objectReg: Register,
    var nameReg: Register,
    var methodReg: Register,
) : Opcode(isThrowing = true) {
    override fun readRegisters() = listOf(objectReg, nameReg, methodReg)

    override fun replaceRegisters(from: Register, to: Register) {
        if (objectReg == from)
            objectReg = to
        if (nameReg == from)
            nameReg = to
        if (methodReg == from)
            methodReg = to
    }
}

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
