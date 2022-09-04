package com.reevajs.reeva.compiler.graph

open class Node(val type: NodeType) {
    // If this node represents a "statement" node, then next is the
    // statement which follows
    var next: Node? = null
    var prev: Node? = null

    val inputs = mutableListOf<Node>()

    override fun toString() = buildString {
        append(type.name)

        if (inputs.isNotEmpty()) {
            append(" [")
            append(inputs.joinToString())
            append("]")
        }
    }
}

class ConstantNode(type: NodeType, val constant: Any) : Node(type) {
    override fun toString() = buildString {
        append(type.name)
        append(" {")
        append(constant.toString())
        append("}")

        if (inputs.isNotEmpty()) {
            append(" [")
            append(inputs.joinToString())
            append("]")
        }
    }
}

enum class NodeType(val isControl: Boolean = true) {
    Start,

    Empty(false),
    Null(false),
    Undefined(false),
    Bool(false),
    String(false),
    Int(false),
    Number(false),
    BigInt(false),

    IncInt,

    Add,
    Sub,
    Mul,
    Div,
    Exp,
    Mod,
    BitwiseAnd,
    BitwiseOr,
    BitwiseXor,
    ShiftLeft,
    ShiftRight,
    ShiftRightUnsigned,

    TestEqualStrict,
    TestNotEqualStrict,
    TestEqual,
    TestNotEqual,
    TestLessThan,
    TestLessThanOrEqual,
    TestInstanceOf,
    TestIn,

    TypeOfGlobal,
    TypeOf,
    ToNumber,
    ToNumeric,
    ToString,
    ToObject,
    ToPropertyKey,
    Negate,
    BitwiseNot,
    ToBooleanLogicalNot,
    Inc,
    Dec,

    LoadKeyedProperty,
    StoreKeyedProperty,
    LoadNamedProperty,
    StoreNamedProperty,
    CreateObject(false),
    CopyObjectExcludingProperties,
    DefineGetterProperty,
    DefineSetterProperty,
    CreateArray,
    StoreArray,
    StoreArrayIndexed,
    DeletePropertyStrict,
    DeletePropertySloppy,

    GetIterator,
    IteratorNext,
    IteratorResultDone,
    IteratorResultValue,

    Call,
    CallArray,
    Construct,
    ConstructArray,

    PushDeclarativeEnvRecord,
    PushModuleEnvRecord,
    PopEnvRecord,
    LoadGlobal,
    StoreGlobal,
    LoadCurrentEnvSlot,
    StoreCurrentEnvSlot,
    LoadEnvSlot,
    StoreEnvSlot,
    LoadModuleVar,
    StoreModuleVar,

    Jump,
    JumpIfTrue,
    JumpIfFalse,
    JumpIfUndefined,
    JumpIfNotUndefined,
    JumpIfNotNullish,
    JumpIfNullish,
    JumpIfNotEmpty,
    JumpTable,

    GetGeneratorPhase,
    SetGeneratorPhase,
    GetGeneratorSentValue,
    PushToGeneratorState,
    PopFromGeneratorState,

    CreateClass,
    CreateMethod,
    AttachClassMethod,
    AttachComputedClassMethod,
    FinalizeClass,
    GetSuperConstructor,
    GetSuperBase,

    Throw,
    ThrowConstantReassignmentError,
    ThrowLexicalAccessError,
    ThrowSuperNotInitializedIfEmpty,
    Return,

    DeclareGlobals,
}