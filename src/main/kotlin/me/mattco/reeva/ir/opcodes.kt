package me.mattco.reeva.ir

sealed class Opcode

/*************
 * CONSTANTS *
 *************/

object LdaZero: Opcode()
class LdaInt(val number: Int) : Opcode()
object LdaUndefined : Opcode()
object LdaNull : Opcode()
object LdaTrue : Opcode()
object LdaFalse : Opcode()
class LdaConstant(val cpIndex: Int) : Opcode()

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

class AddInt(val immediate: Int) : Opcode()
class SubInt(val immediate: Int) : Opcode()
class MulInt(val immediate: Int) : Opcode()
class DivInt(val immediate: Int) : Opcode()
class ModInt(val immediate: Int) : Opcode()
class ExpInt(val immediate: Int) : Opcode()
class BitwiseOrInt(val immediate: Int) : Opcode()
class BitwiseXorInt(val immediate: Int) : Opcode()
class BitwiseAndInt(val immediate: Int) : Opcode()
class ShiftLeftInt(val immediate: Int) : Opcode()
class ShiftRightInt(val immediate: Int) : Opcode()
class ShiftRightUnsignedInt(val immediate: Int) : Opcode()

object Inc : Opcode()
object Dec : Opcode()
object Negate : Opcode()
object BitwiseNot : Opcode()

/**
 * Converts the accumulator to a boolean using the ToBoolean
 * operation, then inverted it.
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
 * Calls a property in [propertyCpIndex] on the value in
 * [callableReg] with any (possibly undefined) receiver. The
 * receiver is in [receiverReg], and the [argCount] number of
 * arguments directly follow it.
 */
class CallProperty(val callableReg: Int, val receiverReg: Int, val argCount: Int, val propertyCpIndex: Int) : Opcode()

/**
 * Calls a property in [propertyCpIndex] on the value in
 * [callableReg] with any (possibly undefined) receiver and
 * no arguments.
 */
class CallProperty0(val callableReg: Int, val receiverReg: Int, val propertyCpIndex: Int) : Opcode()

/**
 * Calls a property in [propertyCpIndex] on the value in
 * [callableReg] with any (possibly undefined) receiver and
 * one argument.
 */
class CallProperty1(val callableReg: Int, val receiverReg: Int, val argReg: Int) : Opcode()

/**
 * Calls a property in [propertyCpIndex] on the value in
 * [callableReg] with any (possibly undefined) receiver and
 * two arguments.
 */
class CallProperty2(val callableReg: Int, val receiverReg: Int, val arg1Reg: Int, arg2Reg: Int) : Opcode()

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
 * Calls the value in [callableReg] with an undefined receiver
 * and two arguments.
 */
class CallUndefinedReceiver2(val callableReg: Int, val arg1Reg: Int, arg2Reg: Int) : Opcode()

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
 *
 * TODO: This opcode is not implemented
 */
class CallRuntime(val id: Int, val firstArgReg: Int, val argCount: Int) : Opcode()

/****************
 * CONSTRUCTION *
 ****************/

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
 * Tests whether or not [targetReg] is strictly equals to the accumulator.
 */
class TestEqualStrict(val targetReg: Int) : Opcode()

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

object ToName : Opcode()
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
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds the value "true".
 */
class JumpIfTrue(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds the value "false".
 */
class JumpIfFalse(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds the value "true" after being cast to
 * a boolean value.
 */
class JumpIfToBooleanTrue(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds the value "false" after being cast to
 * a boolean value.
 */
class JumpIfToBooleanFalse(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds a null value.
 */
class JumpIfNull(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds a non-null value.
 */
class JumpIfNotNull(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds an undefined value.
 */
class JumpIfUndefined(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds a non-undefined value.
 */
class JumpIfNotUndefined(val offset: Int) : Opcode()

/**
 * Jump by the number of bytes specified by [offset] if the
 * accumulator holds an object value.
 */
class JumpIfObject(val offset: Int) : Opcode()

object JumpPlaceholder : Opcode()

/************************
 * SPECIAL FLOW CONTROL *
 ************************/

/**
 * Throws the value in the accumulator
 */
object Throw : Opcode()

/**
 * Returns the value in the accumulator
 */
object Return : Opcode()
