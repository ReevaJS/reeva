package com.reevajs.reeva.transformer.opcodes

import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.transformer.BlockIndex
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.transformer.Local
import com.reevajs.regexp.RegExp
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

interface FunctionContainerOpcode {
    val functionInfo: FunctionInfo?
}

// Marker interface
interface TerminatingOpcode

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
class TypeOfGlobal(val name: String, val isStrict: Boolean) : Opcode(1)

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
class StoreKeyedProperty(val isStrict: Boolean) : Opcode(-3)

/**
 * Loads a literal property name from an object. name can be a JVM String or a
 * JSSymbol.
 *
 * Stack:
 *   ... object -> ... value
 */
class LoadNamedProperty(val name: String) : Opcode(0)

/**
 * Stores a literal property name into an object. name can be a JVM String or a
 * JSSymbol.
 *
 * Stack:
 *   ... object value -> ...
 */
class StoreNamedProperty(val name: String, val isStrict: Boolean) : Opcode(-2)

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
class StoreArray(val arrayLocal: Local, val indexLocal: Local) : Opcode(-1)

/**
 * Stores a value into an array at the constant [index].
 *
 * Stack:
 *   ... array value -> ...
 */
class StoreArrayIndexed(val arrayLocal: Local, val index: Int) : Opcode(-1)

/**
 * Deletes a property from an object using strict ECMAScript semantics.
 *
 * Stack:
 *   ... object property -> ...
 */
object DeletePropertyStrict : Opcode(-1)

/**
 * Deletes a property from an object using non-strict ECMAScript semantics.
 *
 * Stack:
 *   ... object property -> ...
 */
object DeletePropertySloppy : Opcode(-1)

///////////////
// Iterators //
///////////////

/**
 * Pushes an [AOs.IteratorRecord] from the value at the top of the stack
 * using the GetIterator ECMAScript algorithm.
 */
object GetIterator : Opcode(0)

/**
 * Calls the IteratorNext ECMAScript algorithm on the value at the top of the
 * stack. The value must be an [AOs.IteratorRecord] created from
 * [GetIterator].
 */
object IteratorNext : Opcode(0)

/**
 * Calls the IteratorComplete ECMAScript algorithm on the value at the top of
 * the stack. THe value must be an [AOs.IteratorRecord] created from
 * [GetIterator].
 */
object IteratorResultDone : Opcode(0)

/**
 * Calls the IteratorValue ECMAScript algorithm on the value at the top of the
 * stack. THe value must be an [AOs.IteratorRecord] created from
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
 * The same as [Call], but for an identifier which was named "eval". Has a bit
 * more overhead since it must compare the calling target to the %eval% intrinsic,
 * so it gets its own opcode. It also must be concerned with the strictness of its
 * environment.
 */
class CallWithDirectEvalCheck(
    val argCount: Int,
    val isStrict: Boolean,
    val isArray: Boolean,
) : Opcode(-1 - argCount)

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
class DeclareGlobalVars(
    val vars: List<String>,
    val lexs: List<Pair<String, /* isConstant: */ Boolean>>,
    private val flags: Int,
) : Opcode(0) {
    val isEval: Boolean
        get() = (flags and IS_EVAL) == IS_EVAL

    val isStrict: Boolean
        get() = (flags and IS_STRICT) == IS_STRICT

    companion object {
        const val IS_EVAL = 1 shl 0
        const val IS_STRICT = 1 shl 1
    }
}

/**
 * Declare a global function. Takes a function off the stack
 */
class DeclareGlobalFunc(val name: String) : Opcode(-1)

/**
 * Creates a new DeclarativeEnvRecord with the current EnvRecord as its parent,
 * and sets the new record as the active EnvRecord. If slotCount is null, this
 * should be a non-optimized declarative env record. This can happen in scopes
 * where eval is present, for example.
 */
object PushDeclarativeEnvRecord : Opcode(0)

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
class LoadGlobal(val name: String, val isStrict: Boolean) : Opcode(1)

/**
 * Stores a value into a global variable by name.
 */
class StoreGlobal(val name: String, val isStrict: Boolean) : Opcode(-1)

/**
 * Loads a value from the current EnvRecord with the specified [name].
 */
class LoadCurrentEnvName(val name: String, val isStrict: Boolean) : Opcode(1)

/**
 * Stores a value into the current EnvRecord with the specified [name].
 */
class StoreCurrentEnvName(val name: String, val isStrict: Boolean) : Opcode(-1)

/**
 * Loads a value from a parent EnvRecord with the specified [name]. The EnvRecord
 * is chosen via [distance], where a distance of zero implies the current
 * EnvRecord (though [distance] will always be greater than zero).
 */
class LoadEnvName(val name: String, val distance: Int, val isStrict: Boolean) : Opcode(1)

/**
 * Stores a value to a parent EnvRecord with the specified [name]. The EnvRecord
 * is chosen via [distance], where a distance of zero implies the current
 * EnvRecord (though [distance] will always be greater than zero).
 */
class StoreEnvName(val name: String, val distance: Int, val isStrict: Boolean) : Opcode(-1)

/**
 * Loads a named variable from the outer ModuleEnvRecord.
 */
class LoadModuleVar(val name: String) : Opcode(1)

/**
 * Stores a named variable to the outer ModuleEnvRecord
 */
class StoreModuleVar(val name: String) : Opcode(-1)

///////////
// Jumps //
///////////

/**
 * An absolute jump. Does not modify the stack
 */
class Jump(val target: BlockIndex) : Opcode(0), TerminatingOpcode

/**
 * Jumps to [trueTarget] if the value at the top of the stack is the JVM boolean true,
 * [falseTarget] otherwise.
 */
class JumpIfTrue(val trueTarget: BlockIndex, val falseTarget: BlockIndex) : Opcode(-1), TerminatingOpcode

/**
 * Jumps to [trueTarget] if the result of calling ToBoolean on the JSValue at the top
 * of the stack is the JVM boolean true, [falseTarget] otherwise.
 */
class JumpIfToBooleanTrue(val trueTarget: BlockIndex, val falseTarget: BlockIndex) : Opcode(-1), TerminatingOpcode

/**
 * Jumps to [undefinedTarget] if the value on the top of the stack is the JSUndefined
 * instance, [elseTarget] otherwise.
 */
class JumpIfUndefined(val undefinedTarget: BlockIndex, val elseTarget: BlockIndex) : Opcode(-1), TerminatingOpcode

/**
 * Jumps to [nullishTarget] if the value on the top of the stack is the neither the
 * JSUndefined instance nor the JSNull instance, [elseTarget] otherwise.
 */
class JumpIfNullish(val nullishTarget: BlockIndex, val elseTarget: BlockIndex) : Opcode(-1), TerminatingOpcode

/////////////
// Classes //
/////////////

/**
 * Creates a method. Works similarly to CreateClosure but without
 * unnecessary Operations calls.
 */
class CreateConstructor(override val functionInfo: FunctionInfo) : Opcode(1), FunctionContainerOpcode

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

/**
 * Creates a class field descriptor for the CreateClass opcode.
 *
 * Stack:
 *   ... name (JSValue) -> descriptor
 */
class CreateClassFieldDescriptor(
    val isStatic: Boolean,
    override val functionInfo: FunctionInfo?,
) : Opcode(0), FunctionContainerOpcode

data class ClassFieldDescriptor(
    val key: PropertyKey,
    val isStatic: Boolean,
    val functionInfo: FunctionInfo?,
)

/**
 * Creates a class method descriptor for the CreateClass opcode.
 *
 * Stack:
 *   ... name (JSValue) -> descriptor
 */
class CreateClassMethodDescriptor(
    val isStatic: Boolean,
    val kind: MethodDefinitionNode.Kind,
    val isConstructor: Boolean,
    override val functionInfo: FunctionInfo,
) : Opcode(0), FunctionContainerOpcode

data class ClassMethodDescriptor(
    val key: PropertyKey,
    val isStatic: Boolean,
    val kind: MethodDefinitionNode.Kind,
    val isConstructor: Boolean,
    val functionInfo: FunctionInfo,
)

/**
 * Creates a class. Note that this is a temporary data class to allow proper
 * setup of class methods. FinalizeClass must be called on the result in order
 * to get the JS class.
 *
 * Stack:
 *   ... <fields> <methods> superClass -> class
 */
class CreateClass(val name: String?, val numFields: Int, val numMethods: Int) : Opcode(-numFields - numMethods)

//////////////////
// Control flow //
//////////////////

/**
 * Throws the value on the top of the stack as an exception.
 */
object Throw : Opcode(-1), TerminatingOpcode

/**
 * Throws a constant reassignment error, using [name] in the error message.
 */
class ThrowConstantReassignmentError(val name: String) : Opcode(0)

/**
 * Throws a lexical access error, using [name] in the error message.
 */
class ThrowLexicalAccessErrorIfEmpty(val name: String) : Opcode(-1)

/**
 * Throws a super not initialized error if the value on the top of the stack
 * is empty.
 */
object ThrowSuperNotInitializedIfEmpty : Opcode(-1)

/**
 * Returns the value on the top of the stack from this function.
 */
object Return : Opcode(-1), TerminatingOpcode

/**
 * Yields the value on the top of the stack from this function.
 */
class Yield(val target: BlockIndex) : Opcode(0), TerminatingOpcode

/**
 * Awaits the value on the top of the stack from this function.
 */
class Await(val target: BlockIndex) : Opcode(0), TerminatingOpcode

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
class CreateClosure(override val functionInfo: FunctionInfo) : Opcode(1), FunctionContainerOpcode

/**
 * Creates a generator function closure from a FunctionInfo.
 */
class CreateGeneratorClosure(override val functionInfo: FunctionInfo) : Opcode(1), FunctionContainerOpcode

/**
 * Creates an async function closure from a FunctionInfo.
 */
class CreateAsyncClosure(override val functionInfo: FunctionInfo) : Opcode(1), FunctionContainerOpcode

/**
 * Creates an async generator function closure from a FunctionInfo.
 */
class CreateAsyncGeneratorClosure(override val functionInfo: FunctionInfo) : Opcode(1), FunctionContainerOpcode

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
object CreateUnmappedArgumentsObject : Opcode(1)

/**
 * Creates a mapped arguments object from the function arguments. Does not
 * perform the binding to the "arguments" variable.
 */
object CreateMappedArgumentsObject : Opcode(1)

//////////
// Misc //
//////////

/**
 * Creates a new JSRegExpObject from [source], [flags], [regexp]
 *
 * TODO: Can we store only the source/flags or RegExp? We need to throw syntax error
 *       before this opcode is created, but we also need to preserve the original
 *       source/flags exactly as they appear in the source code. Maybe add that
 *       functionality to RegExp/
 */
class CreateRegExpObject(val source: String, val flags: String, val regexp: RegExp) : Opcode(1)

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
 * [AOs.IteratorRecord]
 */
object ForInEnumerate : Opcode(0)

/**
 * Pushes Realm.InternalSymbols.classInstanceFields symbol onto the stack
 */
object PushClassInstanceFieldsSymbol : Opcode(1)
