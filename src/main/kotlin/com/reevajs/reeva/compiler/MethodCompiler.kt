package com.reevajs.reeva.compiler

import codes.som.anthony.koffee.MethodAssembly
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.jvm.istore
import codes.som.anthony.koffee.insns.sugar.*
import codes.som.anthony.koffee.types.TypeLike
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.ExecutionContext
import com.reevajs.reeva.core.environment.DeclarativeEnvRecord
import com.reevajs.reeva.core.environment.EnvRecord
import com.reevajs.reeva.core.environment.GlobalEnvRecord
import com.reevajs.reeva.core.environment.ModuleEnvRecord
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.index.IndexedProperties
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.transformer.IR
import com.reevajs.reeva.transformer.TransformedSource
import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.utils.Error
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.unreachable
import org.objectweb.asm.tree.MethodNode
import java.lang.IllegalStateException


class MethodCompiler(
    val className: String,
    val transformedSource: TransformedSource,
    node: MethodNode,
) : MethodAssembly(node), OpcodeVisitor {
    private val ir: IR
        inline get() = transformedSource.functionInfo.ir

    private val scratchLocal = transformedSource.functionInfo.ir.locals.size

    fun compile() {
        ir.opcodes.forEach(::visit)
    }

    private inline fun <reified T> loadKObject() = getstatic<T>("INSTANCE", T::class)

    private val toPropertyKey: Unit
        get() = invokestatic<PropertyKey>("from", JSValue::class, Any::class)

    private val toValue: Unit
        get() = invokestatic<JSValue>("from", JSValue::class, Any::class)

    private val loadRealm: Unit
        get() {
            aload_0
            invokevirtual<JSFunction>("getRealm", Realm::class)
        }

    private val unreachable: Unit
        get() {
            construct<IllegalStateException>()
            athrow
        }

    private val loadRunningContext: Unit
        get() {
            invokestatic<Agent>("getActiveAgent", Agent::class)
            invokevirtual<Agent>("getRunningExecutionContext", ExecutionContext::class)
            dup
            invokevirtual<ExecutionContext>("getEnvRecord", EnvRecord::class)
            invokevirtual<EnvRecord>("getOuter", EnvRecord::class)
            invokevirtual<ExecutionContext>("setEnvRecord", void, EnvRecord::class)
        }

    override fun visitPushNull() = loadKObject<JSNull>()

    override fun visitPushUndefined() = loadKObject<JSUndefined>()

    override fun visitPushConstant(opcode: PushConstant) {
        when (val constant = opcode.literal) {
            is String -> construct<JSString>(String::class) { ldc(constant) }
            is Int -> construct<JSNumber>(Double::class) { ldc(constant.toDouble()) }
            is Double -> construct<JSNumber>(Double::class) { ldc(constant) }
            is Boolean -> construct<JSBoolean>(Boolean::class) { ldc(constant) }
            else -> unreachable()
        }
    }

    override fun visitPop() = pop

    override fun visitDup() = dup

    override fun visitDupX1() = dup_x1

    override fun visitDupX2() = dup_x2

    override fun visitSwap() = swap

    override fun visitLoadInt(opcode: LoadInt) = iload(opcode.local.value)

    override fun visitStoreInt(opcode: StoreInt) = istore(opcode.local.value)

    override fun visitIncInt(opcode: IncInt) = iinc(opcode.local.value)

    override fun visitLoadValue(opcode: LoadValue) = aload(opcode.local.value)

    override fun visitStoreValue(opcode: StoreValue) = astore(opcode.local.value)

    override fun visitAdd() {
        visitBinaryOperator("+")
    }

    override fun visitSub() {
        visitBinaryOperator("-")
    }

    override fun visitMul() {
        visitBinaryOperator("*")
    }

    override fun visitDiv() {
        visitBinaryOperator("/")
    }

    override fun visitExp() {
        visitBinaryOperator("**")
    }

    override fun visitMod() {
        visitBinaryOperator("%")
    }

    override fun visitBitwiseAnd() {
        visitBinaryOperator("&")
    }

    override fun visitBitwiseOr() {
        visitBinaryOperator("|")
    }

    override fun visitBitwiseXor() {
        visitBinaryOperator("^")
    }

    override fun visitShiftLeft() {
        visitBinaryOperator("<<")
    }

    override fun visitShiftRight() {
        visitBinaryOperator(">>")
    }

    override fun visitShiftRightUnsigned() {
        visitBinaryOperator(">>>")
    }

    private fun visitBinaryOperator(op: String) {
        ldc(op)
        operation("applyStringOrNumericBinaryOperator", JSValue::class, JSValue::class, JSValue::class, String::class)
    }

    private fun operation(name: String, returnType: TypeLike, vararg paramTypes: TypeLike) {
        invokestatic<Operations>(name, returnType, *paramTypes)
    }

    override fun visitTestEqualStrict() {
        operation("isLooselyEqual", JSValue::class, JSValue::class, JSValue::class)
    }

    override fun visitTestNotEqualStrict() {
        operation("isLooselyEqual", JSValue::class, JSValue::class, JSValue::class)
        invokevirtual<JSBoolean>("inv", JSBoolean::class)
    }

    override fun visitTestEqual() {
        operation("isStrictlyEqual", JSValue::class, JSValue::class, JSValue::class)
    }

    override fun visitTestNotEqual() {
        operation("isStrictlyEqual", JSValue::class, JSValue::class, JSValue::class)
        invokevirtual<JSBoolean>("inv", JSBoolean::class)
    }

    override fun visitTestLessThan() {
        ldc(true)
        operation("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
        invokevirtual<JSValue>("ifUndefined", JSValue::class, JSValue::class)

        dup
        loadKObject<JSUndefined>()
        ifStatement(JumpCondition.RefEqual) {
            pop
            loadKObject<JSFalse>()
        }
    }

    override fun visitTestLessThanOrEqual() {
        swap
        ldc(false)
        operation("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)

        dup
        loadKObject<JSFalse>()
        ifStatement(JumpCondition.RefEqual) {
            pop
            loadKObject<JSTrue>()
        }
    }

    override fun visitTestGreaterThan() {
        ldc(false)
        operation("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
        invokevirtual<JSValue>("ifUndefined", JSValue::class, JSValue::class)

        dup
        loadKObject<JSUndefined>()
        ifStatement(JumpCondition.RefEqual) {
            pop
            loadKObject<JSFalse>()
        }
    }

    override fun visitTestGreaterThanOrEqual() {
        swap
        ldc(true)
        operation("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)

        dup
        loadKObject<JSFalse>()
        ifStatement(JumpCondition.RefEqual) {
            pop
            loadKObject<JSTrue>()
        }
    }

    override fun visitTestInstanceOf() {
        operation("instanceofOperator", JSBoolean::class, JSValue::class)
    }

    override fun visitTestIn() {
        // TODO: Add a ToPropertyKey opcode to avoid these swaps
        swap
        toPropertyKey
        swap
        operation("hasProperty", Boolean::class, JSValue::class, JSValue::class)
        toValue
    }

    override fun visitTypeOf() {
        operation("typeofOperator", JSValue::class, JSValue::class)
    }

    override fun visitTypeOfGlobal(opcode: TypeOfGlobal) {
        loadRealm
        getfield<Realm>("globalEnv", EnvRecord::class)
        ldc(opcode.name)
        invokevirtual<EnvRecord>("hasBinding", Boolean::class, String::class)

        ifElseStatement(JumpCondition.True) {
            ifBlock {
                construct<JSString>(String::class) { ldc("undefined") }
            }

            elseBlock {
                visitLoadGlobal(LoadGlobal(opcode.name))
                visitTypeOf()
            }
        }
    }

    override fun visitToNumber() {
        operation("toNumber", JSNumber::class, JSValue::class)
    }

    override fun visitToNumeric() {
        operation("toNumeric", JSValue::class, JSValue::class)
    }

    override fun visitToString() {
        operation("toString", JSString::class, JSValue::class)
    }

    override fun visitNegate() {
        dup
        instanceof<JSBigInt>()
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                operation("bigintUnaryMinus", JSBigInt::class, JSValue::class)
            }

            elseBlock {
                operation("numericUnaryMinus", JSNumber::class, JSValue::class)
            }
        }
    }

    override fun visitBitwiseNot() {
        dup
        instanceof<JSBigInt>()
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                operation("bigintBitwiseNOT", JSBigInt::class, JSValue::class)
            }

            elseBlock {
                operation("numericBitwiseNOT", JSNumber::class, JSValue::class)
            }
        }
    }

    override fun visitToBooleanLogicalNot() {
        operation("toBoolean", Boolean::class, JSValue::class)
        toValue
        invokevirtual<JSBoolean>("inv", JSBoolean::class)
    }

    override fun visitInc() {
        invokevirtual<JSValue>("asInt", Int::class)
        ldc(1)
        iadd
        toValue
    }

    override fun visitDec() {
        invokevirtual<JSValue>("asInt", Int::class)
        ldc(1)
        isub
        toValue
    }

    override fun visitLoadKeyedProperty() {
        swap
        operation("toObject", JSObject::class, JSValue::class)
        swap
        toPropertyKey
        invokevirtual<JSObject>("get", JSValue::class, PropertyKey::class)
    }

    override fun visitStoreKeyedProperty() {
        astore(scratchLocal)
        swap
        operation("toObject", JSObject::class, JSValue::class)
        swap
        toPropertyKey
        aload(scratchLocal)
        invokevirtual<JSObject>("set", JSValue::class, PropertyKey::class, JSValue::class)
    }

    override fun visitLoadNamedProperty(opcode: LoadNamedProperty) {
        operation("toObject", JSObject::class, JSValue::class)
        when (val name = opcode.name) {
            is String -> {
                ldc(name)
                invokevirtual<JSObject>("get", JSValue::class, String::class)
            }
            is JSSymbol -> TODO("create a constant pool")
            else -> unreachable()
        }
    }

    override fun visitStoreNamedProperty(opcode: StoreNamedProperty) {
        TODO("Not yet implemented")
    }

    override fun visitCreateObject() {
        TODO("Not yet implemented")
    }

    override fun visitCreateArray() {
        TODO("Not yet implemented")
    }

    override fun visitStoreArray(opcode: StoreArray) {
        TODO("Not yet implemented")
    }

    override fun visitStoreArrayIndexed(opcode: StoreArrayIndexed) {
        TODO("Not yet implemented")
    }

    override fun visitDeletePropertyStrict() {
        TODO("Not yet implemented")
    }

    override fun visitDeletePropertySloppy() {
        TODO("Not yet implemented")
    }

    override fun visitGetIterator() {
        operation("toObject", JSObject::class, JSValue::class)
        operation("getIterator", Operations.IteratorRecord::class, JSValue::class)
    }

    override fun visitIteratorNext() {
        operation("iteratorNext", JSObject::class, Operations.IteratorRecord::class)
    }

    override fun visitIteratorResultDone() {
        operation("iteratorComplete", Boolean::class, JSValue::class)
    }

    override fun visitIteratorResultValue() {
        operation("iteratorValue", JSValue::class, JSValue::class)
    }

    override fun visitCall(opcode: Call) {
        // TODO: This will definitely need some sort of reordering to reduce bytecode
        construct<ArrayList<*>>()

        repeat(opcode.argCount) {
            dup_x1
            swap
            ldc(0)
            swap
            invokevirtual<ArrayList<*>>("add", void, Int::class, Any::class)
        }

        operation("call", JSValue::class, JSValue::class, JSValue::class, List::class)
    }

    override fun visitCallArray() {
        invokevirtual<JSObject>("getIndexedProperties", IndexedProperties::class)
        val indexedProperties = astore()

        construct<ArrayList<*>>()

        ldc(0)
        val index = istore()

        val head = makeLabel()
        placeLabel(head)

        iload(index)
        aload(indexedProperties)
        invokevirtual<IndexedProperties>("getArrayLikeSize", Long::class)
        ifStatement(JumpCondition.LessThan) {
            dup
            aload(indexedProperties)
            iload(index)
            invokevirtual<IndexedProperties>("get", JSValue::class, Int::class)
            invokevirtual<ArrayList<*>>("add", void, Any::class)
            iinc(index.index)
        }

        operation("call", JSValue::class, JSValue::class, JSValue::class, List::class)
    }

    override fun visitConstruct(opcode: Construct) {
        // TODO: This will definitely need some sort of reordering to reduce bytecode
        construct<ArrayList<*>>()

        repeat(opcode.argCount) {
            dup_x1
            swap
            ldc(0)
            swap
            invokevirtual<ArrayList<*>>("add", void, Int::class, Any::class)
        }

        // TODO: isConstructor check

        operation("call", JSValue::class, JSValue::class, JSValue::class, List::class)
    }

    override fun visitConstructArray() {
        TODO("Not yet implemented")
    }

    override fun visitDeclareGlobals(opcode: DeclareGlobals) {
        aload_0

        ldc(opcode.lexs.size)
        anewarray<String>()
        for ((index, str) in opcode.lexs.withIndex()) {
            dup
            ldc(index)
            ldc(str)
            aastore
        }

        ldc(opcode.funcs.size)
        anewarray<String>()
        for ((index, str) in opcode.funcs.withIndex()) {
            dup
            ldc(index)
            ldc(str)
            aastore
        }

        ldc(opcode.vars.size)
        anewarray<String>()
        for ((index, str) in opcode.vars.withIndex()) {
            dup
            ldc(index)
            ldc(str)
            aastore
        }

        val strArr = Array<String>::class
        invokespecial<JSCompiledFunction>("declareGlobals", void, strArr, strArr, strArr)
    }

    override fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord) {
        loadRunningContext
        construct<DeclarativeEnvRecord>(EnvRecord::class, Int::class) {
            loadRunningContext
            invokevirtual<ExecutionContext>("getEnvRecord", EnvRecord::class)
            ldc(opcode.slotCount)
        }
        invokevirtual<ExecutionContext>("setEnvRecord", void, EnvRecord::class)
    }

    override fun visitPushModuleEnvRecord() {
        loadRunningContext
        construct<ModuleEnvRecord>(EnvRecord::class) {
            loadRunningContext
            invokevirtual<ExecutionContext>("getEnvRecord", EnvRecord::class)
        }
        invokevirtual<ExecutionContext>("setEnvRecord", void, EnvRecord::class)
    }

    override fun visitPopEnvRecord() {
        loadRunningContext
        dup
        invokevirtual<ExecutionContext>("getEnvRecord", EnvRecord::class)
        invokevirtual<EnvRecord>("getOuter", EnvRecord::class)
        invokevirtual<ExecutionContext>("setEnvRecord", void, EnvRecord::class)
    }

    override fun visitLoadGlobal(opcode: LoadGlobal) {
        loadRealm
        invokevirtual<Realm>("getGlobalEnv", GlobalEnvRecord::class)
        ldc(opcode.name)
        invokevirtual<EnvRecord>("hasBinding", Boolean::class, String::class)

        ifStatement(JumpCondition.False) {
            construct<Errors.NotDefined>(String::class) {
                ldc(opcode.name)
            }

            loadRealm
            invokevirtual<Error>("throwReferenceError", void, Realm::class)

            unreachable
        }

        loadRealm
        invokevirtual<Realm>("getGlobalEnv", GlobalEnvRecord::class)
        ldc(opcode.name)
        invokevirtual<EnvRecord>("getBinding", JSValue::class, String::class)
    }

    override fun visitStoreGlobal(opcode: StoreGlobal) {
        astore(scratchLocal)
        loadRealm
        invokevirtual<Realm>("getGlobalEnv", GlobalEnvRecord::class)
        ldc(opcode.name)
        aload(scratchLocal)
        invokevirtual<EnvRecord>("setBinding", JSValue::class, String::class, JSValue::class)
    }

    override fun visitLoadCurrentEnvSlot(opcode: LoadCurrentEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitStoreCurrentEnvSlot(opcode: StoreCurrentEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitLoadEnvSlot(opcode: LoadEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitStoreEnvSlot(opcode: StoreEnvSlot) {
        TODO("Not yet implemented")
    }

    override fun visitJump(opcode: Jump) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfTrue(opcode: JumpIfTrue) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfFalse(opcode: JumpIfFalse) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfToBooleanFalse(opcode: JumpIfToBooleanFalse) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfUndefined(opcode: JumpIfUndefined) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNotUndefined(opcode: JumpIfNotUndefined) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNotNullish(opcode: JumpIfNotNullish) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNullish(opcode: JumpIfNullish) {
        TODO("Not yet implemented")
    }

    override fun visitJumpIfNotEmpty(opcode: JumpIfNotEmpty) {
        TODO("Not yet implemented")
    }

    override fun visitCreateRegExpObject(opcode: CreateRegExpObject) {
        TODO("Not yet implemented")
    }

    override fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral) {
        opcode.numberOfParts
    }

    override fun visitForInEnumerate() {
        TODO("Not yet implemented")
    }

    override fun visitCreateClosure(opcode: CreateClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateAsyncClosure(opcode: CreateAsyncClosure) {
        TODO("Not yet implemented")
    }

    override fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure) {
        TODO("Not yet implemented")
    }

    override fun visitGetSuperConstructor() {
        TODO("Not yet implemented")
    }

    override fun visitCreateUnmappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitCreateMappedArgumentsObject() {
        TODO("Not yet implemented")
    }

    override fun visitThrowSuperNotInitializedIfEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitThrowConstantReassignmentError(opcode: ThrowConstantReassignmentError) {
        TODO("Not yet implemented")
    }

    override fun visitThrowLexicalAccessError(opcode: ThrowLexicalAccessError) {
        TODO("Not yet implemented")
    }

    override fun visitPushClosure() {
        TODO("Not yet implemented")
    }

    override fun visitThrow() {
        athrow
    }

    override fun visitReturn() {
        areturn
    }

    override fun visitDefineGetterProperty() {
        TODO("Not yet implemented")
    }

    override fun visitDefineSetterProperty() {
        TODO("Not yet implemented")
    }

    override fun visitGetGeneratorPhase() {
        TODO("Not yet implemented")
    }

    override fun visitGetSuperBase() {
        TODO("Not yet implemented")
    }

    override fun visitJumpTable(opcode: JumpTable) {
        TODO("Not yet implemented")
    }

    override fun visitPushBigInt(opcode: PushBigInt) {
        TODO("Not yet implemented")
    }

    override fun visitPushEmpty() {
        TODO("Not yet implemented")
    }

    override fun visitSetGeneratorPhase(opcode: SetGeneratorPhase) {
        TODO("Not yet implemented")
    }

    override fun visitGeneratorSentValue() {
        TODO("Not yet implemented")
    }

    override fun visitCopyObjectExcludingProperties(opcode: CopyObjectExcludingProperties) {
        TODO("Not yet implemented")
    }

    override fun visitLoadBoolean(opcode: LoadBoolean) {
        TODO("Not yet implemented")
    }

    override fun visitStoreBoolean(opcode: StoreBoolean) {
        TODO("Not yet implemented")
    }

    override fun visitPushJVMFalse() {
        TODO("Not yet implemented")
    }

    override fun visitPushJVMTrue() {
        TODO("Not yet implemented")
    }

    override fun visitPushJVMInt(opcode: PushJVMInt) {
        TODO("Not yet implemented")
    }

    override fun visitCreateClassConstructor(opcode: CreateMethod) {
        TODO("Not yet implemented")
    }

    override fun visitCreateClass() {
        TODO("Not yet implemented")
    }

    override fun visitAttachClassMethod(opcode: AttachClassMethod) {
        TODO("Not yet implemented")
    }

    override fun visitAttachComputedClassMethod(opcode: AttachComputedClassMethod) {
        TODO("Not yet implemented")
    }

    override fun visitFinalizeClass() {
        TODO("Not yet implemented")
    }

    override fun visitPushToGeneratorState() {
        TODO("Not yet implemented")
    }

    override fun visitPopFromGeneratorState() {
        TODO("Not yet implemented")
    }

    override fun visitLoadModuleVar(opcode: LoadModuleVar) {
        TODO("Not yet implemented")
    }

    override fun visitStoreModuleVar(opcode: StoreModuleVar) {
        TODO("Not yet implemented")
    }

    override fun visitCollectRestArgs() {
        TODO("Not yet implemented")
    }
}
