package com.reevajs.reeva.transformer.opcodes

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.transformer.Local
import com.reevajs.reeva.runtime.Operations
import java.math.BigInteger

/**
 * The base opcode class for all interpreter instructions.
 *
 * @param stackHeightModifier The change in stack height value _after_
 *                            executing this opcode.
 */
sealed class Opcode(val stackHeightModifier: Int) {
    override fun toString(): String {
        return this::class.simpleName!!
    }
}

////////////////////////
// Stack manipulation //
////////////////////////

/**
 * Push the JSEmpty instance onto the stack.
 */
object PushEmpty : Opcode(1)

/**
 * Push the JSNull instance onto the stack.
 */
object PushNull : Opcode(1)

/**
 * Push the JSUndefined instance onto the stack.
 */
object PushUndefined : Opcode(1)

/**
 * Push the JVM boolean false value onto the stack.
 */
object PushJVMFalse : Opcode(1)

/**
 * Push the JVM boolean true value onto the stack.
 */
object PushJVMTrue : Opcode(1)

/**
 * Push a constant JVM int value onto the stack.
 */
class PushJVMInt(val int: Int) : Opcode(1)

/**
 * Push a constant onto the stack as a JSValue. literal can be a String,
 * Int, Double, or Boolean value. These are converted to their proper
 * JSValue equivalents.
 */
class PushConstant(val literal: Any) : Opcode(1)

/**
 * Push a constant JSBigInt value onto the stack.
 */
class PushBigInt(val bigint: BigInteger) : Opcode(1)

/**
 * Pop the top value from the stack.
 */
object Pop : Opcode(-1)

/**
 * Duplicates the top value on the stack.
 */
object Dup : Opcode(1)

/**
 * Duplicates the top value on the stack.
 *
 * Stack:
 *   ... v1 v2 -> ... v2 v1 v2
 */
object DupX1 : Opcode(1)

/**
 * Duplicates the top value on the stack.
 *
 * Stack:
 *   ... v1 v2 v3 -> ... v3 v1 v2 v3
 */
object DupX2 : Opcode(1)

/**
 * Swaps the top two values on the stack.
 */
object Swap : Opcode(0)

////////////
// Locals //
////////////

/**
 * Loads a JVM int from the specified local slot.
 */
class LoadInt(val local: Local) : Opcode(1)

/**
 * Stores a JVM int to the specified local slot.
 */
class StoreInt(val local: Local) : Opcode(-1)

/**
 * Increments the JVM int stored in the specified local slot.
 */
class IncInt(val local: Local) : Opcode(0)

/**
 * Loads a JVM boolean from the specified local slot.
 */
class LoadBoolean(val local: Local) : Opcode(1)

/**
 * Stores a JVM boolean to the specified local slot.
 */
class StoreBoolean(val local: Local) : Opcode(-1)

/**
 * Loads a JSValue from the specified local slot.
 */
class LoadValue(val local: Local) : Opcode(1)

/**
 * Stores a JSValue to the specified local slot.
 */
class StoreValue(val local: Local) : Opcode(-1)

////////////////
// Operations //
////////////////

/*
 * The following opcodes are applied to the stack operands according to the
 * generic ApplyStringOrNumericBinaryOperator ECMAScript algorithm. The value
 * on the top of the stack is the RHS, and the second value is the LHS.
 */

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

/**
 * Apply the TypeOf algorithm to the global variable [name]. This opcode is
 * required in order to avoid a ReferenceError being thrown if given an
 * undefined global identifier.
 */
class TypeOfGlobal(val name: String) : Opcode(1)

/**
 * Performs the generic ToNumber algorithm to the value at the top of the stack.
 */
object ToNumber : Opcode(0)

/**
 * Performs the generic ToNumeric algorithm to the value at the top of the
 * stack
 */
object ToNumeric : Opcode(0)

/**
 * Performs the generic ToString algorithm to the value at the top of the
 * stack.
 */
object ToString : Opcode(0)

/**
 * Performs either the Number::unaryMinus algorithm or the BigInt::bitwiseNot
 * algorithm to the value at the top of the stack, depending on the value's
 * type.
 */
object Negate : Opcode(0)

/**
 * Performs either the Number::unaryMinus algorithm or the BigInt::bitwiseNot
 * algorithm to the value at the top of the stack, depending on the value's
 * type.
 */
object BitwiseNot : Opcode(0)

/**
 * First performs the generic ToBoolean algorithm to the value at the top of
 * the stack, then performs a logical not.
 */
object ToBooleanLogicalNot : Opcode(0)

/**
 * Increments the number on the top of the stack. This is not a generic opcode;
 * the value on the top of the stack _must_ be a JSNumber.
 */
object Inc : Opcode(0)

/**
 * Decrements the number on the top of the stack. This is not a generic opcode;
 * the value on the top of the stack _must_ be a JSNumber.
 */
object Dec : Opcode(0)

/////////////
// Objects //
/////////////

/**
 * Loads a computed object property value.
 *
 * Stack:
 *   ... object property -> ... value
 */
object LoadKeyedProperty : Opcode(-1)

/**
 * Stores a computed object property value.
 *
 * Stack:
 *   ... object property value -> ...
 */
object StoreKeyedProperty : Opcode(-3)

/**
 * TODO: This shouldn't allow symbols
 *
 * Loads a literal property name from an object. name can be a JVM String or a
 * JSSymbol.
 *
 * Stack:
 *   ... object -> ... value
 */
class LoadNamedProperty(val name: Any) : Opcode(0)

/**
 * TODO: This shouldn't allow symbols
 *
 * Stores a literal property name into an object. name can be a JVM String or a
 * JSSymbol.
 *
 * Stack:
 *   ... object value -> ...
 */
class StoreNamedProperty(val name: Any) : Opcode(-2)

/**
 * Creates and pushes a new empty JSObject.
 *
 * Stack:
 *   ... -> object ...
 */
object CreateObject : Opcode(1)

/**
 * Performs a shallow copy of an object on the top of the stack. A JSArray
 * containing PropertyKeys which should not be copied is stored in
 * [propertiesLocal]. This opcode is used to perform binding rest operations,
 * where [propertiesLocal] stores the keys which have already been bound. For
 * example, in the following code:
 *
 *     let { a, b, ...rest } = foo;
 *
 * [propertiesLocal] would be an array containing "a" and "b"
 */
class CopyObjectExcludingProperties(val propertiesLocal: Local) : Opcode(0)

/**
 * Defines a getter method onto an object. The name is always computed.
 *
 * Stack:
 *   ... obj property method -> ...
 */
object DefineGetterProperty : Opcode(-3)

/**
 * Defines a setter method onto an object. The name is always computed.
 *
 * Stack:
 *   ... obj property method -> ...
 */
object DefineSetterProperty : Opcode(-3)

/**
 * Creates and pushes a new empty JSArray.
 *
 * Stack:
 *   ... -> array ...
 */
object CreateArray : Opcode(1)

/**
 * Stores a value into an array at the index specified in the JVM integer
 * local slot [index]. This opcode also increments the value in the local
 * by 1.
 *
 * Stack:
 *   ... array value -> ...
 */
class StoreArray(val index: Local) : Opcode(-2)

/**
 * Stores a value into an array at the constant [index].
 *
 * Stack:
 *   ... array value -> ...
 */
class StoreArrayIndexed(val index: Int) : Opcode(-2)

/**
 * Deletes a property from an object using strict ECMAScript semantics.
 *
 * Stack:
 *   ... object property -> ...
 */
object DeletePropertyStrict : Opcode(-2)

/**
 * Deletes a property from an object using non-strict ECMAScript semantics.
 *
 * Stack:
 *   ... object property -> ...
 */
object DeletePropertySloppy : Opcode(-2)

///////////////
// Iterators //
///////////////

/**
 * Pushes an [Operations.IteratorRecord] from the value at the top of the stack
 * using the GetIterator ECMAScript algorithm.
 */
object GetIterator : Opcode(0)

/**
 * Calls the IteratorNext ECMAScript algorithm on the value at the top of the
 * stack. The value must be an [Operations.IteratorRecord] created from
 * [GetIterator].
 */
object IteratorNext : Opcode(0)

/**
 * Calls the IteratorComplete ECMAScript algorithm on the value at the top of
 * the stack. THe value must be an [Operations.IteratorRecord] created from
 * [GetIterator].
 */
object IteratorResultDone : Opcode(0)

/**
 * Calls the IteratorValue ECMAScript algorithm on the value at the top of the
 * stack. THe value must be an [Operations.IteratorRecord] created from
 * [GetIterator].
 */
object IteratorResultValue : Opcode(0)

///////////
// Calls //
///////////

/**
 * Calls a JSValue with a variable number of arguments. The arguments are a
 * variable number of JSValues on the stack dictated by [argCount]. The first
 * arguments is the receiver.
 *
 * Stack:
 *   ... target receiver arg1 arg2 ... arg_argCount -> ... result
 */
class Call(val argCount: Int) : Opcode(-1 - argCount)

/**
 * Calls a JSValue with a variable number of arguments. The arguments are
 * stored in a JSArrayObject. The receiver is taken outside this array.
 *
 * Stack:
 *   ... target receiver argArray -> ... result
 */
object CallArray : Opcode(-2)

/**
 * Constructs a JSValue with a variable number of arguments. The arguments are
 * a variable number of JSValues on the stack dictated by [argCount]. The first
 * arguments is the new.target.
 *
 * Stack:
 *   ... target new.target arg1 arg2 ... arg_argCount -> ... result
 */
class Construct(val argCount: Int) : Opcode(-1 - argCount)

/**
 * Constructs a JSValue with a variable number of arguments. The arguments are
 * stored in a JSArrayObject. The new.target is taken outside this array.
 *
 * Stack:
 *   ... target new.target argArray -> ... result
 */
object ConstructArray : Opcode(-2)

/////////////////
// Environment //
/////////////////

/**
 * Declares global variables. This is required to prevent global variable
 * collision.
 */
class DeclareGlobals(val vars: List<String>, val lexs: List<String>, val funcs: List<String>) : Opcode(0)

/**
 * Creates a new DeclarativeEnvRecord with the current EnvRecord as its parent,
 * and sets the new record as the active EnvRecord.
 */
class PushDeclarativeEnvRecord(val slotCount: Int) : Opcode(0)

/**
 * Creates a new ModuleEnvRecord with the current EnvRecord as its parent,
 * and sets the new record as the active EnvRecord.
 */
object PushModuleEnvRecord : Opcode(0)

/**
 * Sets the active EnvRecord to the currently active EnvRecord's parent.
 */
object PopEnvRecord : Opcode(0)

/**
 * Loads a global variable by name. Throws a ReferenceError if there is no
 * global variable bound to [name].
 */
class LoadGlobal(val name: String) : Opcode(1)

/**
 * Stores a value into a global variable by name.
 */
class StoreGlobal(val name: String) : Opcode(-1)

/**
 * Loads a value from the current EnvRecord at the specified [slot].
 */
class LoadCurrentEnvSlot(val slot: Int) : Opcode(1)

/**
 * Stores a value into the current EnvRecord at the specified [slot].
 */
class StoreCurrentEnvSlot(val slot: Int) : Opcode(-1)

/**
 * Loads a value from a parent EnvRecord at the specified [slot]. The EnvRecord
 * is chosen via [distance], where a distance of zero implies the current
 * EnvRecord (though [distance] will always be greater than zero).
 */
class LoadEnvSlot(val slot: Int, val distance: Int) : Opcode(1)

/**
 * Stores a value to a parent EnvRecord at the specified [slot]. The EnvRecord
 * is chosen via [distance], where a distance of zero implies the current
 * EnvRecord (though [distance] will always be greater than zero).
 */
class StoreEnvSlot(val slot: Int, val distance: Int) : Opcode(-1)

///////////
// Jumps //
///////////

sealed class JumpInstr(var to: Int, stackHeightModifier: Int = -1) : Opcode(stackHeightModifier)

/**
 * An absolute jump. Does not modify the stack
 */
class Jump(to: Int) : JumpInstr(to, 0)

/**
 * Jumps to [to] if the value at the top of the stack is the JVM boolean true.
 */
class JumpIfTrue(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the value at the top of the stack is the JVM boolean false.
 */
class JumpIfFalse(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the result of calling ToBoolean on the JSValue at the top
 * of the stack is the JVM boolean true.
 */
class JumpIfToBooleanTrue(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the result of calling ToBoolean on the JSValue at the top
 * of the stack is the JVM boolean false.
 */
class JumpIfToBooleanFalse(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the value on the top of the stack is the JSUndefined
 * instance.
 */
class JumpIfUndefined(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the value on the top of the stack is not the JSUndefined
 * instance.
 */
class JumpIfNotUndefined(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the value on the top of the stack is the neither the
 * JSUndefined instance nor the JSNull instance.
 */
class JumpIfNotNullish(to: Int) : JumpInstr(to)

/**
 * Jumps to [to] if the value on the top of the stack is not the JSEmpty
 * instance.
 */
class JumpIfNotEmpty(to: Int) : JumpInstr(to)

/**
 * Jumps to the location specified in the table, given by a JVM int value at
 * the top of the stack. This is the primary mechanism behind Generators.
 */
class JumpTable(val table: Map<Int, Int>) : JumpInstr(-1)

////////////////
// Generators //
////////////////

/**
 * Loads the current function's GeneratorState and calls its getPhase() method.
 */
object GetGeneratorPhase : Opcode(1)

/**
 * Loads the current function's GeneratorState and calls its setPhase() method
 * with [phase] as the argument.
 */
class SetGeneratorPhase(val phase: Int) : Opcode(0)

/**
 * Loads the current function's GeneratorState and calls its getSentValue()
 * method.
 */
object GetGeneratorSentValue : Opcode(1)

/**
 * Pushes the value at the top of the stack into the GeneratorState's temporary
 * stack. This is used to save stack values across yield boundaries.
 */
object PushToGeneratorState : Opcode(-1)

/**
 * Pops a value from the GeneratorState's temporary stack and pushes it to the
 * primary stack. This is used to load stack values across yield boundaries.
 */
object PopFromGeneratorState : Opcode(1)

/////////////
// Classes //
/////////////

/**
 * Creates a class. Note that this is a temporary data class to allow proper
 * setup of class methods. FinalizeClass must be called on the result in order
 * to get the JS class.
 *
 * Stack:
 *   ... ctor superClass -> ... class
 */
object CreateClass : Opcode(-1)

/**
 * Creates a method. Works similarly to CreateClosure but without
 * unnecessary Operations calls.
 */
class CreateMethod(val ir: FunctionInfo) : Opcode(1)

/**
 * Attaches a class method given the result of CreateClass.
 *
 * Stack:
 *   ... class -> ...
 */
class AttachClassMethod(
    val name: String,
    val isStatic: Boolean,
    val kind: MethodDefinitionNode.Kind,
    val ir: FunctionInfo,
) : Opcode(-1)


/**
 * Attaches a class method given the result of CreateClass.
 *
 * Stack:
 *   ... class name -> ...
 */
class AttachComputedClassMethod(
    val isStatic: Boolean,
    val kind: MethodDefinitionNode.Kind,
    val ir: FunctionInfo,
) : Opcode(-2)

/**
 * Creates a JSValue from the data class created by CreateClass.
 */
object FinalizeClass : Opcode(0)

/**
 * Retrieves the currently active super constructor (the prototype of the
 * function at the top of the callstack).
 */
object GetSuperConstructor : Opcode(1)

/**
 * Retrieves the currently active super base (the prototype of the homeObject
 * of the function at the top of the callstack).
 */
object GetSuperBase : Opcode(1)

//////////////////
// Control flow //
//////////////////

/**
 * Throws the value on the top of the stack as an exception.
 */
object Throw : Opcode(-1)

/**
 * Throws a constant reassignment error, using [name] in the error message.
 */
class ThrowConstantReassignmentError(val name: String) : Opcode(0)

/**
 * Throws a lexical access error, using [name] in the error message.
 */
class ThrowLexicalAccessError(val name: String) : Opcode(0)

/**
 * Throws a super not initialized error if the value on the top of the stack
 * is empty.
 */
object ThrowSuperNotInitializedIfEmpty : Opcode(-1)

/**
 * Returns the value on the top of the stack from this function.
 */
object Return : Opcode(-1)

///////////////
// Functions //
///////////////

/**
 * Retrieves the currently active function (the function at the top of the
 * callstack).
 */
object PushClosure : Opcode(1)

/**
 * Creates a normal function closure from a FunctionInfo.
 */
class CreateClosure(val ir: FunctionInfo) : Opcode(1)

/**
 * Creates a generator function closure from a FunctionInfo.
 */
class CreateGeneratorClosure(val ir: FunctionInfo) : Opcode(1)

/**
 * Creates an async function closure from a FunctionInfo.
 */
class CreateAsyncClosure(val ir: FunctionInfo) : Opcode(1)

/**
 * Creates an async generator function closure from a FunctionInfo.
 */
class CreateAsyncGeneratorClosure(val ir: FunctionInfo) : Opcode(1)

/**
 * Collects any arguments which have not been bound to a non-rest parameter
 * (those which are beyond the length of the function) into a JSArray and
 * pushes the array to the stack.
 */
object CollectRestArgs : Opcode(1)

/**
 * Creates an unmapped arguments object from the function arguments. Does not
 * perform the binding to the "arguments" variable.
 */
object CreateUnmappedArgumentsObject : Opcode(0)

/**
 * Creates a mapped arguments object from the function arguments. Does not
 * perform the binding to the "arguments" variable.
 */
object CreateMappedArgumentsObject : Opcode(0)

/////////////
// Modules //
/////////////

/**
 * Declares the list of named which are imported by the module. This is
 * required to make sure that the module being import from actually contains
 * these exports, as if it does not, it is an error at the time of the import.
 *
 * Stack:
 *   ... moduleRecord -> ...
 */
class DeclareNamedImports(val namedImports: Set<String>) : Opcode(-1)

/**
 * Stores the ModuleRecord at the top of the stack into the currently-active
 * ModuleEnvRecord. As imports are always top-level, the active EnvRecord is
 * guaranteed to be a ModuleEnvRecord.
 */
object StoreModuleRecord : Opcode(-1)

//////////
// Misc //
//////////

/**
 * Creates a new JSRegExpObject from [source] and [flags]
 */
class CreateRegExpObject(val source: String, val flags: String) : Opcode(1)

/**
 * Creates a string from a variable number of string values on the stack.
 * There must be [numberOfParts] stack entries on the stack which are all
 * JSStrings.
 */
class CreateTemplateLiteral(val numberOfParts: Int) : Opcode(-numberOfParts + 1)

/**
 * Pushes a JSObjectPropertyIterator for the object at the top of the
 * stack. The value need not be an object; ToObject is performs on the value
 * before creates the property iterator. The pushed value is a
 * [Operations.IteratorRecord]
 */
object ForInEnumerate : Opcode(0)
