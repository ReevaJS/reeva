package me.mattco.reeva.ir.opcodes

import me.mattco.reeva.utils.expect

data class RegisterRange(val start: Int, val count: Int) {
    val end: Int get() = start + count - 1

    init {
        expect(start >= 0)
        expect(count >= 0)
    }

    fun drop(n: Int) = RegisterRange(start + n, count - n)

    fun dropLast(n: Int) = RegisterRange(start, count - n)
}

enum class IrOpcodeArgType {
    Register,
    CPIndex,
    InstrIndex,
    Literal,
    Range;

    fun validate(obj: Any) {
        val valid = when (this) {
            Register -> obj is Int && obj >= 0
            CPIndex -> obj is Int && obj >= 0
            InstrIndex -> obj is Int && obj >= 0
            Literal -> obj is Int
            Range -> obj is RegisterRange
        }

        expect(valid) {
            "$obj is not a valid argument for IrOpcodeArgType.$name"
        }
    }

    fun format(obj: Any, argCount: Int): String = when (this) {
        Register -> (obj as Int).let {
            when {
                it == 0 -> "<receiver>"
                it < argCount -> "a${argCount - it - 1}"
                else -> "r${it - argCount}"
            }
        }
        CPIndex -> "[$obj]"
        InstrIndex -> "[$obj]"
        Literal -> "#$obj"
        Range -> {
            val range = obj as RegisterRange
            "${Register.format(range.start, argCount)}-${Register.format(range.end, argCount)}"
        }
    }
}

private val REG = IrOpcodeArgType.Register
private val CP = IrOpcodeArgType.CPIndex
private val INSTR = IrOpcodeArgType.InstrIndex
private val LITERAL = IrOpcodeArgType.Literal
private val RANGE = IrOpcodeArgType.Range

enum class IrOpcodeType(
    vararg val types: IrOpcodeArgType,
    val writesToAcc: Boolean = true,
    val hasSideEffects: Boolean = true,
    val isFlow: Boolean = false,
) {
    /////////////////
    /// CONSTANTS ///
    /////////////////

    LdaTrue(hasSideEffects = false),
    LdaFalse(hasSideEffects = false),
    LdaUndefined(hasSideEffects = false),
    LdaNull(hasSideEffects = false),
    LdaZero(hasSideEffects = false),
    LdaConstant(CP, hasSideEffects = false),
    LdaInt(LITERAL, hasSideEffects = false),

    ///////////////////////////
    /// REGISTER OPERATIONS ///
    ///////////////////////////

    /**
     * Load the value in a register into the accumulator
     *
     * arg 1: the register containing the value to load into the accumulator
     */
    Ldar(REG, hasSideEffects = false),

    /**
     * Store the value in the accumulator into a register
     *
     * arg 1: the register which the accumulator will be stored to
     */
    Star(REG, writesToAcc = false),

    /**
     * Copy a value from one register to another
     *
     * arg 1: the register with the desired value
     * arg 2: the register that the value will be copied to
     */
    Mov(REG, REG, writesToAcc = false),

    /**
     * Load a literal property from an object into the accumulator.
     *
     * arg 1: the register containing the object
     * arg 2: the constant pool index of the name. Must be a string literal
     */
    LdaNamedProperty(REG, CP, hasSideEffects = false),

    /**
     * Load a computed property from an object into the accumulator.
     *
     * accumulator: the computed property value
     * arg 1: the register containing object
     */
    LdaKeyedProperty(REG),

    /**
     * Store a literal property into an object.
     *
     * accumulator: the value to store into the object property
     * arg 1: the register containing the object
     * arg 2: the constant pool index of the name. Must be a string literal
     */
    StaNamedProperty(REG, CP, writesToAcc = false),

    /**
     * Store a computed property into an object.
     *
     * accumulator: the value to store into the object property
     * arg 1: the register containing the object
     * arg 2: the register containing the computed property value
     */
    StaKeyedProperty(REG, REG, writesToAcc = false),

    /////////////////////////////
    /// OBJECT/ARRAY LITERALS ///
    /////////////////////////////

    /**
     * Creates an empty array object and loads it into the accumulator
     */
    CreateArrayLiteral,

    /**
     * Store a value into an array.
     *
     * accumulator: the value to store into the array
     * arg 1: the register containing the array
     * arg 2: the literal array index to insert the value into
     */
    StaArrayLiteralIndex(REG, LITERAL, writesToAcc = false),

    /**
     * Store a value into an array.
     *
     * accumulator: the value to store into the array
     * arg 1: the register containing the array
     * arg 2: the literal array index to insert the value into
     */
    StaArrayLiteral(REG, REG, writesToAcc = false),

    /**
     * Creates an empty object and loads it into the accumulator
     */
    CreateObjectLiteral,

    /////////////////////////
    /// BINARY OPERATIONS ///
    /////////////////////////

    /*
     * All of the following binary opcodes perform their respective
     * operations between two values and load the result into the
     * accumulator. The first argument hold the LHS value of the
     * operation, and the accumulator holds the RHS value.
     */

    Add(REG, hasSideEffects = false),
    Sub(REG, hasSideEffects = false),
    Mul(REG, hasSideEffects = false),
    Div(REG, hasSideEffects = false),
    Mod(REG, hasSideEffects = false),
    Exp(REG, hasSideEffects = false),
    BitwiseOr(REG, hasSideEffects = false),
    BitwiseXor(REG, hasSideEffects = false),
    BitwiseAnd(REG, hasSideEffects = false),
    ShiftLeft(REG, hasSideEffects = false),
    ShiftRight(REG, hasSideEffects = false),
    ShiftRightUnsigned(REG, hasSideEffects = false),

    /**
     * Increments the value in the accumulator. This is NOT a generic
     * operation; the value in the accumulator must be numeric.
     */
    Inc(hasSideEffects = false),

    /**
     * Decrements the value in the accumulator. This is NOT a generic
     * operation; the value in the accumulator must be numeric.
     */
    Dec(hasSideEffects = false),

    /**
     * Negates the value in the accumulator. This is NOT a generic
     * operation; the value in the accumulator must be numeric.
     */
    Negate(hasSideEffects = false),

    /**
     * Bitwise-nots the value in the accumulator. This is NOT a generic
     * operation; the value in the accumulator must be numeric.
     */
    BitwiseNot(hasSideEffects = false),

    /**
     * Appends the string in the accumulator to another string.
     *
     * accumulator: the RHS of the string concatentation operation
     * arg 1: the register with the string that will be the LHS of the
     *        string concatenation operation
     */
    StringAppend(REG, writesToAcc = false),

    /**
     * Converts the accumulator to a boolean using the ToBoolean
     * operation, then inverts it.
     */
    ToBooleanLogicalNot,

    /**
     * Inverts the boolean in the accumulator.
     */
    LogicalNot(writesToAcc = true),

    /**
     * Load the accumulator with the string respresentation of the
     * type currently in the accumulator
     */
    TypeOf(writesToAcc = true),

    /**
     * Delete a property following strict-mode semantics.
     *
     * accumulator: the property which will be deleted
     * arg 1: the register containing the target object
     */
    DeletePropertyStrict(REG, writesToAcc = false),

    /**
     * Delete a property following sloppy-mode semantics.
     *
     * accumulator: the property which will be deleted
     * arg 1: the register containing the target object
     */
    DeletePropertySloppy(REG, writesToAcc = false),

    /////////////
    /// SCOPE ///
    /////////////

    /**
     * Loads a global variable into the accumulator
     *
     * arg 1: the name of the global variable
     */
    LdaGlobal(CP, hasSideEffects = false),

    /**
     * Stores the value in the accumulator into a global variable.
     *
     * arg 1: the name of the global variable
     */
    StaGlobal(CP),

    /**
     * Loads a named variable from the current environment into the
     * accumulator.
     *
     * arg 1: the slot in the current environment to load
     */
    LdaCurrentEnv(LITERAL, hasSideEffects = false),

    /**
     * Stores the accumulator into a named variable in the current
     * environment.
     *
     * accumulator: the value to store
     * arg 1: the slot in the current environment to store to
     */
    StaCurrentEnv(LITERAL),

    /**
     * Loads a named variable from a parent environment into the
     * accumulator.
     *
     * arg 1: the slot in the environment to load
     * arg 2: the distance of the current environment from the target
     *        environment
     */
    LdaEnv(LITERAL, LITERAL, hasSideEffects = false),

    /**
     * Stores a value into a named variable in a parent environment.
     *
     * arg 1: the slot in the environment to store to
     * arg 2: the distance of the current environment from the target
     *        environment
     */
    StaEnv(LITERAL, LITERAL),

    /**
     * Pushes a new environment onto the env record stack
     *
     * arg 1: the numbers of slots the new environment will contain
     */
    PushEnv(LITERAL, writesToAcc = false),

    /**
     * Pops the current environment from the env record stack
     */
    PopCurrentEnv(writesToAcc = false),

    /**
     * Pops multiple environments from the env record stack
     *
     * arg 1: the numbers of environments to pop
     */
    PopEnvs(LITERAL, writesToAcc = false),

    ///////////////
    /// CALLING ///
    ///////////////

    /**
     * Calls a value with any receiver.
     *
     * arg 1: the target of the call
     * arg 2: the argument registers, starting with the receiver registers
     */
    Call(REG, RANGE),

    /**
     * Calls a value with any receiver and zero arguments.
     *
     * arg 1: the target of the call
     * arg 2: the receiver register
     */
    Call0(REG, REG),

    /**
     * Calls a value with any receiver and one argument.
     *
     * arg 1: the target of the call
     * arg 2: the argument registers, starting with the receiver registers
     */
    Call1(REG, RANGE),

    /**
     * Calls a value with any receiver and any number of arguments. The
     * last argument must be spread.
     *
     * arg 1: the target of the call
     * arg 2: the argument registers, starting with the receiver registers
     */
    CallLastSpread(REG, RANGE),

    /**
     * Calls a value with any receiver and any number of arguments, with
     * all arguments in an array.
     *
     * arg 1: the target of the call
     * arg 2: the argument register array, with the receiver as the first
     *        index
     */
    CallFromArray(REG, REG),

    /**
     * Calls a runtime function with any numbers of arguments.
     *
     * arg 1: the runtime function ID
     * arg 2: the argument registers
     */
    CallRuntime(LITERAL, RANGE),

    /////////////////////
    /// CONSTRUCTIONS ///
    /////////////////////

    /**
     * Constructs a value with any amount of arguments
     *
     * accumulator: the new.target
     * arg 1: the target of the construction
     * arg 2: the argument registers
     */
    Construct(REG, RANGE),

    /**
     * Constructs a value with no arguments
     *
     * accumulator: the new.target
     * arg 1: the target of the construction
     */
    Construct0(REG),

    /**
     * Constructs a value with any amount of arguments. The last argument
     * is a spread value.
     *
     * accumulator: the new.target
     * arg 1: the target of the construction
     * arg 2: the argument registers
     */
    ConstructLastSpread(REG, RANGE),

    /**
     * Constructs a value with any amount of arguments. The arguments are
     * all stored in an array.
     *
     * accumulator: the new.target
     * arg 1: the target of the construction
     * arg 2: the argument register
     */
    ConstructFromArray(REG, REG),

    ///////////////
    /// TESTING ///
    ///////////////

    /**
     * Tests if a value is weakly equal to the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestEqual(REG),

    /**
     * Tests if a value is not weakly equal to the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestNotEqual(REG),

    /**
     * Tests if a value is strictly equal to the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestEqualStrict(REG),

    /**
     * Tests if a value is strictly not equal to the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestNotEqualStrict(REG),

    /**
     * Tests if a value is less than the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestLessThan(REG),

    /**
     * Tests if a value is greater than the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestGreaterThan(REG),

    /**
     * Tests if a value is less than or equal to the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestLessThanOrEqual(REG),

    /**
     * Tests if a value is greater than or equal to the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestGreaterThanOrEqual(REG),

    /**
     * Tests if a value is the same object in the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestReferenceEqual(REG),

    /**
     * Tests if a value is an instance of the value in the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestInstanceOf(REG),

    /**
     * Tests if a value is 'in' the accumulator
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestIn(REG),

    /**
     * Tests if a value is nullish
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestNullish(hasSideEffects = false),

    /**
     * Tests if a value is null
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestNull(hasSideEffects = false),

    /**
     * Tests if a value is undefined
     *
     * accumulator: the rhs of the operation
     * arg 1: the lhs of the operation
     */
    TestUndefined(hasSideEffects = false),

    ///////////////////
    /// CONVERSIONS ///
    ///////////////////

    /**
     * Convert the accumulator to a boolean using ToBoolean()
     */
    ToBoolean(hasSideEffects = false),

    /**
     * Convert the accumulator to a number using ToNumber()
     */
    ToNumber,

    /**
     * Convert the accumulator to a number using ToNumeric()
     */
    ToNumeric,

    /**
     * Convert the accumulator to an object using ToObject()
     */
    ToObject,

    /**
     * Convert the accumulator to a string using ToString()
     */
    ToString,

    /////////////
    /// JUMPS ///
    /////////////

    /**
     * Non-conditional jump to the instructions
     */
    Jump(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is true
     */
    JumpIfTrue(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is false
     */
    JumpIfFalse(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is true,
     * first calling ToBoolean()
     */
    JumpIfToBooleanTrue(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is false,
     * first calling ToBoolean()
     */
    JumpIfToBooleanFalse(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is null
     */
    JumpIfNull(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is not null
     */
    JumpIfNotNull(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is undefined
     */
    JumpIfUndefined(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is not undefined
     */
    JumpIfNotUndefined(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is nullish
     */
    JumpIfNullish(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is not nullish
     */
    JumpIfNotNullish(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Conditional jump to an instruction if the accumulator is an object
     */
    JumpIfObject(INSTR, writesToAcc = false, isFlow = true),

    /**
     * Placeholder for jump instructions, this should not appear in any
     * emitted IR
     */
    JumpPlaceholder,

    ////////////////////////////
    /// SPECIAL FLOW CONTROL ///
    ////////////////////////////

    /**
     * Return the value in the accumulator
     */
    Return(writesToAcc = false, isFlow = true),

    /**
     * Throws the value in the accumulator
     */
    Throw(writesToAcc = false, isFlow = true),

    /**
     * Throws a const reassignment error
     */
    ThrowConstReassignment(CP, writesToAcc = false, isFlow = true),

    /**
     * Throws a TypeError if the accumulator is <empty>
     */
    ThrowUseBeforeInitIfEmpty(CP, writesToAcc = false, isFlow = true),

    /////////////
    /// OTHER ///
    /////////////

    /**
     * Defines a getter function on an object
     *
     * arg 1: the target object register
     * arg 2: the property name register
     * arg 3: the method register
     */
    DefineGetterProperty(REG, REG, REG, writesToAcc = false),

    /**
     * Defines a setter function on an object
     *
     * arg 1: the target object register
     * arg 2: the property name register
     * arg 3: the method register
     */
    DefineSetterProperty(REG, REG, REG, writesToAcc = false),

    /**
     * Declare global names
     */
    DeclareGlobals(CP, writesToAcc = false),

    /**
     * Creates a mapped arguments objects and inserts it into the scope
     * of the current function
     */
    CreateMappedArgumentsObject(writesToAcc = false),

    /**
     * Creates an unmapped arguments objects and inserts it into the scope
     * of the current function
     */
    CreateUnmappedArgumentsObject(writesToAcc = false),

    /**
     * Sets the accumulator to the result of calling
     * <accumulator>[Symbol.iterator]()
     */
    GetIterator,

    /**
     * Creates a function object, referencing a FunctionInfo object stored
     * in the constant pool
     *
     * arg 1: the constant pool index entry with the FunctionInfo object
     */
    CreateClosure(CP),

    /**
     * TODO
     */
    DebugBreakpoint(writesToAcc = false)
}

class IrOpcode(type: IrOpcodeType, vararg args: Any) {
    var type: IrOpcodeType = type
        private set

    var args = args.toMutableList()
        private set

    init {
        expect(args.size == type.types.size) {
            "OpcodeType.$type expected ${type.types.size} arguments, but received ${args.size}"
        }

        args.forEachIndexed { index, arg ->
            type.types[index].validate(arg)
        }
    }

    fun regAt(index: Int) = args[index] as Int
    fun cpAt(index: Int) = args[index] as Int
    fun instrAt(index: Int) = args[index] as Int
    fun literalAt(index: Int) = args[index] as Int
    fun rangeAt(index: Int) = args[index] as RegisterRange

    fun replaceJumpPlaceholder(newType: IrOpcodeType, offset: Int) {
        expect(type == IrOpcodeType.JumpPlaceholder, type.toString())
        type = newType
        args = mutableListOf(offset)
    }

    override fun toString() = type.name
}
