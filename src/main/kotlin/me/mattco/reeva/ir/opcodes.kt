package me.mattco.reeva.ir

import me.mattco.reeva.interpreter.InterpRuntime

sealed class Opcode

/*************
 * CONSTANTS *
 *************/

object LdaZero: Opcode()
object LdaUndefined : Opcode()
object LdaNull : Opcode()
object LdaTrue : Opcode()
object LdaFalse : Opcode()
class LdaConstant(val cpIndex: Int) : Opcode()
class LdaInt(val int: Int) : Opcode()
class LdaDouble(val double: Double) : Opcode()

/***********************
 * REGISTER OPERATIONS *
 ***********************/

/**
 * Load the register [reg] into the accumulator
 */
class Ldar(val reg: Int) : Opcode()

/**
 * Store the accumulator into the register [reg]
 */
class Star(val reg: Int) : Opcode()

/**
 * Move the value in [fromReg] to [toReg]
 */
class Mov(val fromReg: Int, val toReg: Int) : Opcode()

/*********************
 * OBJECT PROPERTIES *
 *********************/

/**
 * Load a literal property from an object in [objectReg]. The name
 * of the property is in [nameCpIndex] in the constant pool. The
 * result is inserted into the accumulator.
 */
class LdaNamedProperty(val objectReg: Int, val nameCpIndex: Int) : Opcode()

/**
 * Load a property from an object in [objectReg]. The property is in
 * the accumulator. The result is inserted into the accumulator.
 */
class LdaKeyedProperty(val objectReg: Int) : Opcode()

/**
 * Store a literal property into an object in [objectReg]. The name
 * of the property is in [nameCpIndex] in the constant pool. The
 * value is in the accumulator.
 */
class StaNamedProperty(val objectReg: Int, val nameCpIndex: Int) : Opcode()

/**
 * Store a property into an object in [objectReg]. The name of the
 * property is in [keyReg]. The value is in the accumulator. The
 * result is inserted into the accumulator.
 */
class StaKeyedProperty(val objectReg: Int, val keyReg: Int) : Opcode()

/*************************
 * OBJECT/ARRAY LITERALS *
 *************************/

object CreateArrayLiteral : Opcode()
class SetArrayLiteralIndex(val arrayReg: Int, val arrayIndex: Int) : Opcode()
object CreateObjectLiteral : Opcode()

/*********************
 * BINARY OPERATIONS *
 *********************/

// All binary operations perform their operation between the
// accumulator and the value stored in [valueReg], and put
// the result of the operation back into the accumulator.

class Add(val valueReg: Int) : Opcode()
class Sub(val valueReg: Int) : Opcode()
class Mul(val valueReg: Int) : Opcode()
class Div(val valueReg: Int) : Opcode()
class Mod(val valueReg: Int) : Opcode()
class Exp(val valueReg: Int) : Opcode()
class BitwiseOr(val valueReg: Int) : Opcode()
class BitwiseXor(val valueReg: Int) : Opcode()
class BitwiseAnd(val valueReg: Int) : Opcode()
class ShiftLeft(val valueReg: Int) : Opcode()
class ShiftRight(val valueReg: Int) : Opcode()
class ShiftRightUnsigned(val valueReg: Int) : Opcode()

object Inc : Opcode()
object Dec : Opcode()
object Negate : Opcode()
object BitwiseNot : Opcode()

/**
 * Converts the accumulator to a boolean using the ToBoolean
 * operation, then invertes it.
 */
object ToBooleanLogicalNot : Opcode()

/**
 * Inverts the value in the accumulator. Must already be a
 * Boolean value.
 */
object LogicalNot : Opcode()

/**
 * Load the accumulator with the string representation of the
 * type currently in the accumulator.
 */
object TypeOf : Opcode()

/**
 * Delete the property stored in the accumulator from the object
 * at [targetReg] following strict-mode semantics.
 */
class DeletePropertyStrict(val targetReg: Int) : Opcode()

/**
 * Delete the property stored in the accumulator from the object
 * at [targetReg] following non-strict-mode semantics.
 */
class DeletePropertySloppy(val targetReg: Int) : Opcode()

/*********
 * SCOPE *
 *********/

/**
 * Loads a global variable into the accumulator
 */
class LdaGlobal(val nameCpIndex: Int) : Opcode()

/**
 * Stores the value in the accumulator into the global env
 */
class StaGlobal(val nameCpIndex: Int) : Opcode()

/**
 * Load the named property [nameCpIndex] from the environment
 */
class LdaCurrentEnv(val slot: Int) : Opcode()

/**
 * Store the value in the accumulator into the named property
 * [nameCpIndex] in the environment as a 'let' binding
 */
class StaCurrentEnv(val slot: Int) : Opcode()

class LdaEnv(val slot: Int, val depthOffset: Int) : Opcode()

class StaEnv(val slot: Int, val depthOffset: Int) : Opcode()

/**
 * Create and immediately start using a new DeclarativeEnvRecord
 */
class PushEnv(val numSlots: Int) : Opcode()

/**
 * Sets the current lexicalEnv to it's outer env
 */
object PopEnv : Opcode()

/***********
 * CALLING *
 ***********/

/**
 * Calls the value in [callableReg] with any (possibly undefined)
 * receiver. The receiver is in [receiverReg], and the [argCount]
 * number of arguments directly follow it.
 */
class CallAnyReceiver(val callableReg: Int, val receiverReg: Int, val argCount: Int) : Opcode()

/**
 * Calls a property on the value in [callableReg] with a non-nullish
 * receiver. The receiver is in [receiverReg], and the [argCount] number
 * of arguments directly follow it.
 */
class CallProperty(val callableReg: Int, val receiverReg: Int, val argCount: Int) : Opcode()

/**
 * Calls a property on the value in [callableReg] with a non-nullish
 * receiver and no arguments. The receiver is in [receiverReg].
 */
class CallProperty0(val callableReg: Int, val receiverReg: Int) : Opcode()

/**
 * Calls a property on the value in [callableReg] with a non-nullish
 * receiver and 1 argument. The receiver is in [receiverReg].
 */
class CallProperty1(val callableReg: Int, val receiverReg: Int, val argReg: Int) : Opcode()

/**
 * Calls the value in [callableReg] with an undefined receiver.
 * The receiver is in [receiverReg], and the [argCount] number
 * of arguments directly follow it.
 */
class CallUndefinedReceiver(val callableReg: Int, val firstArgReg: Int, val argCount: Int) : Opcode()

/**
 * Calls the value in [callableReg] with an undefined receiver
 * and no arguments.
 */
class CallUndefinedReceiver0(val callableReg: Int) : Opcode()

/**
 * Calls the value in [callableReg] with an undefined receiver
 * and one argument.
 */
class CallUndefinedReceiver1(val callableReg: Int, val argReg: Int) : Opcode()

/**
 * Calls the value in [callableReg] with any (possibly undefined)
 * receiver. The receiver is in [receiverReg], and the [argCount]
 * number of arguments directly follow it. The last argument must
 * be a spread value.
 *
 * TODO: This opcode is not implemented
 */
class CallWithSpread(val callableReg: Int, val firstArgReg: Int, val argCount: Int) : Opcode()

/**
 * Call the runtime function specified by [id] with [argCount] number
 * of arguments, starting in [firstArgReg].
 */
class CallRuntime(val id: Int, val firstArgReg: Int, val argCount: Int) : Opcode() {
    constructor(method: InterpRuntime, firstArgReg: Int, argCount: Int) :
        this(method.ordinal, firstArgReg, argCount)
}

/****************
 * CONSTRUCTION *
 ****************/

/**
 * Constructs the target with a given new.target and no arguments.
 *
 * accumulator: new.target
 * [targetReg]: The target of the construct operation
 */
class Construct0(val targetReg: Int) : Opcode()

/**
 * Constructs the target with a given new.target with the given arguments.
 *
 * accumulator: new.target
 * [targetReg]: The target of the construct operation
 * [firstArgReg]: The register containing the first argument
 * [argCount]: The number of args (including firstArgReg)
 */
class Construct(val targetReg: Int, val firstArgReg: Int, val argCount: Int) : Opcode()

/**
 * Constructs the target with a given new.target with the given arguments. The
 * last argument must be a spread argument.
 *
 * TODO: This opcode is unused.
 *
 * accumulator: new.target
 * [targetReg]: The target of the construct operation
 * [firstArgReg]: The register containing the first argument
 * [argCount]: The number of args (including firstArgReg)
 */
class ConstructWithSpread(val targetReg: Int, val firstArgReg: Int, val argCount: Int) : Opcode()

/***********
 * TESTING *
 ***********/

/**
 * Tests whether or not [targetReg] is equal to the accumulator.
 */
class TestEqual(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is equal to the accumulator.
 */
class TestNotEqual(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is strictly equals to the accumulator.
 */
class TestEqualStrict(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is strictly equals to the accumulator.
 */
class TestNotEqualStrict(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is less than to the accumulator.
 */
class TestLessThan(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is greater than to the accumulator.
 */
class TestGreaterThan(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is less than or equals to the accumulator.
 */
class TestLessThanOrEqual(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is greater than or equals to the accumulator.
 */
class TestGreaterThanOrEqual(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is referentially equal to the accumulator.
 */
class TestReferenceEqual(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is an instance of the type in the accumulator.
 */
class TestInstanceOf(val targetReg: Int) : Opcode()

/**
 * Tests whether or not [targetReg] is a property of the object in the accumulator.
 */
class TestIn(val targetReg: Int) : Opcode()

/**
 * Tests whether or not the accumulator is undefined or null
 */
object TestNullish : Opcode()

/**
 * Tests whether or not the accumulator is null
 */
object TestNull : Opcode()

/**
 * Tests whether or not the accumulator is undefined
 */
object TestUndefined : Opcode()

/***************
 * CONVERSIONS *
 ***************/

object ToBoolean : Opcode()
object ToNumber : Opcode()
object ToNumeric : Opcode()
object ToObject : Opcode()
object ToString : Opcode()

/*********
 * JUMPS *
 *********/

/**
 * Jump by [offset] number of opcodes.
 */
class Jump(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds the value "true".
 */
class JumpIfTrue(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds the value "false".
 */
class JumpIfFalse(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds the value "true" after being cast to
 * a boolean value.
 */
class JumpIfToBooleanTrue(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds the value "false" after being cast to
 * a boolean value.
 */
class JumpIfToBooleanFalse(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds a null value.
 */
class JumpIfNull(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds a non-null value.
 */
class JumpIfNotNull(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds an undefined value.
 */
class JumpIfUndefined(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds a non-undefined value.
 */
class JumpIfNotUndefined(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by [offset] if the
 * accumulator holds an object value.
 */
class JumpIfObject(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by offset if accumulator
 * holds an nullish value.
 */
class JumpIfNullish(val offset: Int) : Opcode()

/**
 * Jump by the number of opcodes specified by offset if accumulator
 * holds an nullish value.
 */
class JumpIfNotNullish(val offset: Int) : Opcode()

object JumpPlaceholder : Opcode()

/************************
 * SPECIAL FLOW CONTROL *
 ************************/

/**
 * Returns the value in the accumulator
 */
object Return : Opcode()

/**
 * Throws the value in the accumulator
 */
object Throw : Opcode()

class ThrowConstReassignment(val nameCpIndex: Int) : Opcode()

/**
 * Throws a reference error if the value in the accumulator is empty.
 * This is used for checking if a variable declared in an EnvRecord
 * has already been initialized
 */
class ThrowUseBeforeInitIfEmpty(val nameCpIndex: Int) : Opcode()

/*********
 * OTHER *
 *********/

/**
 * Sets the accumulator to the result of calling
 * <accumulator>[Symbol.iterator]
 */
object GetIterator : Opcode()

/**
 * Creates a function object, referencing a FunctionInfo object stored
 * in the constant pool at [cpIndex].
 */
class CreateClosure(val cpIndex: Int) : Opcode()

object DebugBreakpoint : Opcode()
