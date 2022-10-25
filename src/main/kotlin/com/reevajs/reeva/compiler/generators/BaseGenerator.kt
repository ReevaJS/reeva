package com.reevajs.reeva.compiler.generators

import codes.som.koffee.MethodAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import com.reevajs.reeva.compiler.*
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.core.environment.GlobalEnvRecord
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.transformer.Local as TransformerLocal
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.utils.*
import org.objectweb.asm.tree.TryCatchBlockNode
import java.util.Collections

abstract class BaseGenerator(
    methodAssembly: MethodAssembly,
    protected val functionInfo: FunctionInfo,
) : MethodAssembly(methodAssembly.node), OpcodeVisitor {
    protected val blocks = functionInfo.ir.blocks.mapValues { it.value to makeLabel() }
    private val localMap = mutableMapOf<TransformerLocal, Local>()

    fun visitIR() {
        for ((block, label) in blocks.values) {
            placeLabel(label)
            block.opcodes.forEach(::visit)

            if (block.handlerBlock != null) {
                val end = makeLabel()
                placeLabel(end)

                node.tryCatchBlocks.add(
                    TryCatchBlockNode(
                        label,
                        end,
                        blocks[block.handlerBlock]!!.second,
                        "com/reevajs/reeva/core/errors/ThrowException",
                    )
                )
            }
        }
    }

    abstract val pushReceiver: Unit

    abstract val pushArguments: Unit

    abstract val pushRealm: Unit

    val pushNewTarget: Unit
        get() {
            pushArguments
            invokevirtual<JSArguments>("getNewTarget", JSValue::class)
        }

    override fun visitPushNull() = pushNull

    override fun visitPushUndefined() = pushUndefined

    override fun visitPushConstant(opcode: PushConstant) {
        when (val value = opcode.literal) {
            is String -> construct<JSString>(String::class) {
                ldc(value)
            }
            is Int -> construct<JSNumber>(Double::class) {
                ldc(value.toDouble())
            }
            is Double -> construct<JSNumber>(Double::class) {
                ldc(value)
            }
            is Boolean -> if (value) pushTrue else pushFalse
            else -> unreachable()
        }
    }

    override fun visitPop() = pop

    override fun visitDup() = dup

    override fun visitDupX1() = dup_x1

    override fun visitDupX2() = dup_x2

    override fun visitSwap() = swap

    override fun visitLoadInt(opcode: LoadInt) {
        expect(opcode.local.value >= functionInfo.length)
        iload(localMap[opcode.local]!!)
    }
    
    override fun visitStoreInt(opcode: StoreInt) {
        expect(opcode.local.value >= functionInfo.length)
        istore(getOrCreateLocal(opcode.local, LocalType.Int))
    }
    
    override fun visitIncInt(opcode: IncInt) {
        expect(opcode.local.value >= functionInfo.length)
        iinc(getOrCreateLocal(opcode.local, LocalType.Int).index)
    }
    
    override fun visitLoadValue(opcode: LoadValue) {
        val index = opcode.local.value

        when {
            index == 0 -> pushReceiver
            index == 1 -> pushNewTarget
            index < functionInfo.length -> {
                pushArguments
                ldc(opcode.local.value - 2)
                invokevirtual<JSArguments>("get", JSValue::class, int)
            }
            else -> load(localMap[opcode.local]!!)
        }
    }

    override fun visitStoreValue(opcode: StoreValue) {
        val index = opcode.local.value
        if (index < functionInfo.length) {
            unreachable()
        } else {
            store(getOrCreateLocal(opcode.local, LocalType.Object))
        }
    }
    private fun visitBinaryOperator(operator: String) {
        ldc(operator)
        invokestatic<AOs>(
            "applyStringOrNumericBinaryOperator",
            JSValue::class,
            JSValue::class,
            JSValue::class,
            String::class
        )
    }

    override fun visitAdd() = visitBinaryOperator("+")

    override fun visitSub() = visitBinaryOperator("-")

    override fun visitMul() = visitBinaryOperator("*")

    override fun visitDiv() = visitBinaryOperator("/")

    override fun visitExp() = visitBinaryOperator("**")

    override fun visitMod() = visitBinaryOperator("%")

    override fun visitBitwiseAnd() = visitBinaryOperator("&")

    override fun visitBitwiseOr() = visitBinaryOperator("|")

    override fun visitBitwiseXor() = visitBinaryOperator("^")

    override fun visitShiftLeft() = visitBinaryOperator("<<")

    override fun visitShiftRight() = visitBinaryOperator(">>")

    override fun visitShiftRightUnsigned() = visitBinaryOperator(">>>")

    override fun visitTestEqualStrict() {
        invokestatic<AOs>("isStrictlyEqual", JSBoolean::class, JSValue::class, JSValue::class)
    }

    override fun visitTestNotEqualStrict() {
        invokestatic<AOs>("isStrictlyEqual", JSBoolean::class, JSValue::class, JSValue::class)
        invokevirtual<JSBoolean>("inv", JSBoolean::class)
    }

    override fun visitTestEqual() {
        invokestatic<AOs>("isLooselyEqual", JSBoolean::class, JSValue::class, JSValue::class)
    }

    override fun visitTestNotEqual() {
        invokestatic<AOs>("isLooselyEqual", JSBoolean::class, JSValue::class, JSValue::class)
        invokevirtual<JSBoolean>("inv", JSBoolean::class)
    }

    override fun visitTestLessThan() {
        ldc(true)
        invokestatic<AOs>("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
        dup
        pushUndefined
        ifStatement(JumpCondition.RefEqual) {
            pop
            pushFalse
        }
    }

    override fun visitTestLessThanOrEqual() {
        swap
        ldc(false)
        invokestatic<AOs>("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
        pushFalse
        ifElseStatement(JumpCondition.RefEqual) {
            ifBlock { pushTrue }
            ifBlock { pushFalse }
        }
    }

    override fun visitTestGreaterThan() {
        swap
        ldc(false)
        invokestatic<AOs>("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
        dup
        pushUndefined
        ifStatement(JumpCondition.RefEqual) {
            pop
            pushFalse
        }
    }

    override fun visitTestGreaterThanOrEqual() {
        ldc(true)
        invokestatic<AOs>("isLessThan", JSValue::class, JSValue::class, JSValue::class, Boolean::class)
        pushFalse
        ifElseStatement(JumpCondition.RefEqual) {
            ifBlock { pushTrue }
            elseBlock { pushFalse }
        }
    }

    override fun visitTestInstanceOf() =
        invokestatic<AOs>("instanceofOperator", JSValue::class, JSValue::class, JSValue::class)

    override fun visitTestIn() {
        dup_x1
        // rhs lhs rhs
        checkcast<JSObject>()
        ifStatement(JumpCondition.False) {
            pushRealm
            invokestatic<Errors.InBadRHS>("throwTypeError", Void::class.java, Realm::class)
            pop
        }

        // rhs lhs
        invokestatic<AOs>("toPropertyKey", PropertyKey::class, JSValue::class)

        invokestatic<AOs>("hasProperty", Boolean::class, JSValue::class, JSValue::class)
        invokestatic<JSValue>("from", JSValue::class, Any::class)
    }

    override fun visitTypeOf() = invokestatic<AOs>("typeofOperator", JSValue::class, JSValue::class)

    override fun visitTypeOfGlobal(opcode: TypeOfGlobal) {
        pushRealm
        invokevirtual<Realm>("getGlobalEnv", GlobalEnvRecord::class)

        dup
        ldc(opcode.name)
        invokevirtual<GlobalEnvRecord>("hasBinding", Boolean::class, String::class)
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                pop
                construct<JSString>(String::class) { ldc("undefined") }
            }

            elseBlock {
                ldc(opcode.name)
                ldc(opcode.isStrict)
                invokevirtual<GlobalEnvRecord>("getBindingValue", JSValue::class, String::class, Boolean::class)
                invokestatic<AOs>("typeofOperator", JSValue::class, JSValue::class)
            }
        }
    }

    override fun visitToNumber() = invokestatic<AOs>("toNumber", JSNumber::class, JSValue::class)

    override fun visitToNumeric() = invokestatic<AOs>("toNumeric", JSValue::class, JSValue::class)

    override fun visitToString() = invokestatic<AOs>("toString", JSString::class, JSValue::class)

    override fun visitNegate() {
        dup
        instanceof<JSBigInt>()
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                invokestatic<AOs>("bigintUnaryMinus", JSBigInt::class, JSValue::class)
            }

            elseBlock {
                invokestatic<AOs>("bigintUnaryMinus", JSValue::class, JSValue::class)
            }
        }
    }

    override fun visitBitwiseNot() {
        dup
        instanceof<JSBigInt>()
        ifElseStatement(JumpCondition.True) {
            ifBlock {
                invokestatic<AOs>("bigintBitwiseNOT", JSBigInt::class, JSValue::class)
            }

            elseBlock {
                invokestatic<AOs>("numericBitwiseNOT", JSValue::class, JSValue::class)
            }
        }
    }

    override fun visitToBooleanLogicalNot() {
        ldc(1)
        invokestatic<AOs>("toBoolean", Boolean::class, JSValue::class)
        isub
        invokestatic<JSBoolean>("valueOf", JSBoolean::class, Boolean::class)
    }

    override fun visitInc() {
        checkcast<JSNumber>()
        val number = astore()
        construct<JSNumber>(Double::class) {
            aload(number)
            invokevirtual<JSNumber>("getNumber", Double::class)
            ldc(1.0)
            dadd
        }
    }

    override fun visitDec() {
        checkcast<JSNumber>()
        val number = astore()
        construct<JSNumber>(Double::class) {
            aload(number)
            invokevirtual<JSNumber>("getNumber", Double::class)
            ldc(1.0)
            dsub
        }
    }

    // TODO: It'd be nice if these were already property keys and objects
    override fun visitLoadKeyedProperty() {
        invokestatic<AOs>("toPropertyKey", PropertyKey::class, JSValue::class)
        val key = astore()
        invokestatic<AOs>("toObject", JSObject::class, JSValue::class)
        aload(key)
        invokevirtual<JSObject>("get", JSValue::class, PropertyKey::class)
    }

    override fun visitStoreKeyedProperty(opcode: StoreKeyedProperty) {
        val value = astore()
        invokestatic<AOs>("toPropertyKey", PropertyKey::class, JSValue::class)
        val key = astore()
        invokestatic<AOs>("toObject", JSObject::class, JSValue::class)
        aload(key)
        aload(value)
        ldc(opcode.isStrict)
        invokestatic<AOs>("set", Boolean::class, JSObject::class, PropertyKey::class, JSValue::class, Boolean::class)
        pop
    }

    override fun visitLoadNamedProperty(opcode: LoadNamedProperty) {
        invokestatic<AOs>("toObject", JSObject::class, JSValue::class)
        ldc(opcode.name)
        invokevirtual<JSObject>("get", JSValue::class, String::class)
    }

    override fun visitStoreNamedProperty(opcode: StoreNamedProperty) {
        val value = astore()
        invokestatic<AOs>("toObject", JSObject::class, JSValue::class)
        construct<PropertyKey>(Any::class) {
            ldc(opcode.name)
        }
        aload(value)
        ldc(opcode.isStrict)
        invokestatic<AOs>("set", JSObject::class, PropertyKey::class, JSValue::class, Boolean::class)
    }

    override fun visitCreateObject() {
        invokestatic<JSObject>("create", JSObject::class)
    }

    override fun visitCreateArray() {
        invokestatic<JSArrayObject>("create", JSArrayObject::class)
    }

    override fun visitStoreArray(opcode: StoreArray): Nothing = TODO("FunctionCompiler::visitStoreArray")

    override fun visitStoreArrayIndexed(opcode: StoreArrayIndexed): Nothing =
        TODO("FunctionCompiler::visitStoreArrayIndexed")

    override fun visitDeletePropertyStrict(): Nothing = TODO("FunctionCompiler::visitDeletePropertyStrict")

    override fun visitDeletePropertySloppy(): Nothing = TODO("FunctionCompiler::visitDeletePropertySloppy")

    override fun visitGetIterator(): Nothing = TODO("FunctionCompiler::visitGetIterator")

    override fun visitIteratorNext(): Nothing = TODO("FunctionCompiler::visitIteratorNext")

    override fun visitIteratorResultDone(): Nothing = TODO("FunctionCompiler::visitIteratorResultDone")

    override fun visitIteratorResultValue(): Nothing = TODO("FunctionCompiler::visitIteratorResultValue")

    override fun visitCall(opcode: Call) {
        construct<ArrayList<*>>()
        val list = astore()

        repeat(opcode.argCount) {
            aload(list)
            swap
            invokevirtual<ArrayList<*>>("add", Boolean::class, Any::class)
            pop
        }

        aload(list)
        dup
        invokestatic<Collections>("reverse", void, List::class)

        invokestatic<AOs>("call", JSValue::class, JSValue::class, JSValue::class, List::class)
    }

    override fun visitCallArray() {
        // TODO: Do not do this, it is super inefficient. Do what the interpreter does
        invokestatic<AOs>("iterableToList", List::class, JSValue::class)
    }

    override fun visitCallWithDirectEvalCheck(opcode: CallWithDirectEvalCheck): Nothing =
        TODO("FunctionCompiler::visitCallWithDirectEvalCheck")

    override fun visitConstruct(opcode: Construct) {
        construct<ArrayList<*>>()
        val list = astore()

        repeat(opcode.argCount) {
            aload(list)
            swap
            invokevirtual<ArrayList<*>>("add", Boolean::class, Any::class)
            pop
        }

        invokestatic<Collections>("reverse", void, List::class)

        val args = astore()
        val newTarget = astore()
        val target = astore()

        aload(target)
        invokestatic<AOs>("isConstructor", Boolean::class, JSValue::class)
        ifStatement(JumpCondition.False) {
            construct<Errors.NotACtor>(String::class) {
                aload(target)
                invokevirtual<JSValue>("toString", String::class)
            }
            invokevirtual<Error>("throwTypeError", Void::class.java)
            pop
        }

        aload(target)
        aload(newTarget)
        aload(args)

        invokestatic<AOs>("construct", JSValue::class, JSValue::class, JSValue::class, List::class)
    }

    override fun visitConstructArray() {
        checkcast<JSObject>()
        // TODO: Do not do this, it is very inefficient
        invokestatic<AOs>("iterableToList", List::class, JSValue::class)
        swap
        invokestatic<AOs>("construct", JSValue::class, JSValue::class, List::class, JSValue::class)
    }

    override fun visitDeclareGlobalVars(opcode: DeclareGlobalVars): Nothing =
        TODO("FunctionCompiler::visitDeclareGlobalVars")

    override fun visitDeclareGlobalFunc(opcode: DeclareGlobalFunc): Nothing =
        TODO("FunctionCompiler::visitDeclareGlobalFunc")

    override fun visitPushDeclarativeEnvRecord(opcode: PushDeclarativeEnvRecord): Nothing =
        TODO("FunctionCompiler::visitPushDeclarativeEnvRecord")

    override fun visitPushModuleEnvRecord(): Nothing = TODO("FunctionCompiler::visitPushModuleEnvRecord")

    override fun visitPopEnvRecord(): Nothing = TODO("FunctionCompiler::visitPopEnvRecord")

    override fun visitLoadGlobal(opcode: LoadGlobal) {
        pushRealm
        dup
        invokevirtual<Realm>("getGlobalEnv", GlobalEnvRecord::class)
        ldc(opcode.name)
        invokevirtual<GlobalEnvRecord>("hasBinding", Boolean::class, String::class)
        ifStatement(JumpCondition.False) {
            construct<Errors.NotDefined>(String::class) {
                ldc(opcode.name)
            }
            invokevirtual<Error>("throwReferenceError", Void::class.java)
            pop
        }

        invokevirtual<Realm>("getGlobalEnv", GlobalEnvRecord::class)
        ldc(opcode.name)
        ldc(opcode.isStrict)
        invokevirtual<GlobalEnvRecord>("getBindingValue", JSValue::class, String::class, Boolean::class)
    }

    override fun visitStoreGlobal(opcode: StoreGlobal): Nothing = TODO("FunctionCompiler::visitStoreGlobal")

    override fun visitLoadCurrentEnvName(opcode: LoadCurrentEnvName): Nothing =
        TODO("FunctionCompiler::visitLoadCurrentEnvName")

    override fun visitStoreCurrentEnvName(opcode: StoreCurrentEnvName): Nothing =
        TODO("FunctionCompiler::visitStoreCurrentEnvName")

    override fun visitLoadEnvName(opcode: LoadEnvName): Nothing = TODO("FunctionCompiler::visitLoadEnvName")

    override fun visitStoreEnvName(opcode: StoreEnvName): Nothing = TODO("FunctionCompiler::visitStoreEnvName")

    override fun visitJump(opcode: Jump): Nothing = TODO("FunctionCompiler::visitJump")

    override fun visitJumpIfTrue(opcode: JumpIfTrue): Nothing = TODO("FunctionCompiler::visitJumpIfTrue")

    override fun visitJumpIfToBooleanTrue(opcode: JumpIfToBooleanTrue): Nothing =
        TODO("FunctionCompiler::visitJumpIfToBooleanTrue")

    override fun visitJumpIfUndefined(opcode: JumpIfUndefined): Nothing = TODO("FunctionCompiler::visitJumpIfUndefined")

    override fun visitJumpIfNullish(opcode: JumpIfNullish): Nothing = TODO("FunctionCompiler::visitJumpIfNullish")

    override fun visitCreateRegExpObject(opcode: CreateRegExpObject): Nothing =
        TODO("FunctionCompiler::visitCreateRegExpObject")

    override fun visitCreateTemplateLiteral(opcode: CreateTemplateLiteral) {
        val locals = (0 until opcode.numberOfParts).map { astore() }

        construct<JSString>(String::class) {
            construct<StringBuilder>()

            locals.asReversed().forEach {
                aload(it)
                checkcast<JSString>()
                invokevirtual<JSString>("getString", String::class)
                invokevirtual<StringBuilder>("append", StringBuilder::class, String::class)
            }

            invokevirtual<StringBuilder>("toString", String::class)
        }
    }

    override fun visitForInEnumerate(): Nothing = TODO("FunctionCompiler::visitForInEnumerate")

    override fun visitCreateClosure(opcode: CreateClosure): Nothing = TODO("FunctionCompiler::visitCreateClosure")

    override fun visitCreateGeneratorClosure(opcode: CreateGeneratorClosure): Nothing =
        TODO("FunctionCompiler::visitCreateGeneratorClosure")

    override fun visitCreateAsyncClosure(opcode: CreateAsyncClosure): Nothing =
        TODO("FunctionCompiler::visitCreateAsyncClosure")

    override fun visitCreateAsyncGeneratorClosure(opcode: CreateAsyncGeneratorClosure): Nothing =
        TODO("FunctionCompiler::visitCreateAsyncGeneratorClosure")

    override fun visitCreateUnmappedArgumentsObject(): Nothing =
        TODO("FunctionCompiler::visitCreateUnmappedArgumentsObject")

    override fun visitCreateMappedArgumentsObject(): Nothing =
        TODO("FunctionCompiler::visitCreateMappedArgumentsObject")

    override fun visitThrowConstantReassignmentError(opcode: ThrowConstantReassignmentError): Nothing =
        TODO("FunctionCompiler::visitThrowConstantReassignmentError")

    override fun visitThrowLexicalAccessError(opcode: ThrowLexicalAccessErrorIfEmpty): Nothing =
        TODO("FunctionCompiler::visitThrowLexicalAccessError")

    override fun visitThrowSuperNotInitializedIfEmpty() {
        pushEmpty
        ifStatement(JumpCondition.RefEqual) {
            getstatic<Errors.Class.DerivedSuper>("INSTANCE", Errors.Class.DerivedSuper::class)
            invokevirtual<Errors.Class.DerivedSuper>("throwTypeError", Void::class)
            pop
        }
    }

    override fun visitPushClosure(): Nothing = TODO("FunctionCompiler::visitPushClosure")

    override fun visitThrow(): Nothing = TODO("FunctionCompiler::visitThrow")

    override fun visitReturn() = areturn

    override fun visitYield(opcode: Yield): Nothing = TODO("FunctionCompiler::visitYield")

    override fun visitAwait(opcode: Await): Nothing = TODO("FunctionCompiler::visitAwait")

    override fun visitDefineGetterProperty(): Nothing = TODO("FunctionCompiler::visitDefineGetterProperty")

    override fun visitDefineSetterProperty(): Nothing = TODO("FunctionCompiler::visitDefineSetterProperty")

    override fun visitGetSuperBase(): Nothing = TODO("FunctionCompiler::visitGetSuperBase")

    override fun visitPushBigInt(opcode: PushBigInt): Nothing = TODO("FunctionCompiler::visitPushBigInt")

    override fun visitPushEmpty() = pushEmpty

    override fun visitCopyObjectExcludingProperties(opcode: CopyObjectExcludingProperties): Nothing =
        TODO("FunctionCompiler::visitCopyObjectExcludingProperties")

    override fun visitLoadBoolean(opcode: LoadBoolean): Nothing = TODO("FunctionCompiler::visitLoadBoolean")

    override fun visitStoreBoolean(opcode: StoreBoolean): Nothing = TODO("FunctionCompiler::visitStoreBoolean")

    override fun visitPushJVMFalse(): Nothing = TODO("FunctionCompiler::visitPushJVMFalse")

    override fun visitPushJVMTrue(): Nothing = TODO("FunctionCompiler::visitPushJVMTrue")

    override fun visitPushJVMInt(opcode: PushJVMInt): Nothing = TODO("FunctionCompiler::visitPushJVMInt")

    override fun visitCreateConstructor(opcode: CreateConstructor): Nothing =
        TODO("FunctionCompiler::visitCreateConstructor")

    override fun visitCreateClassFieldDescriptor(opcode: CreateClassFieldDescriptor): Nothing =
        TODO("FunctionCompiler::visitCreateClassFieldDescriptor")

    override fun visitCreateClassMethodDescriptor(opcode: CreateClassMethodDescriptor): Nothing =
        TODO("FunctionCompiler::visitCreateClassMethodDescriptor")

    override fun visitCreateClass(opcode: CreateClass): Nothing = TODO("FunctionCompiler::visitCreateClass")

    override fun visitLoadModuleVar(opcode: LoadModuleVar): Nothing = TODO("FunctionCompiler::visitLoadModuleVar")

    override fun visitStoreModuleVar(opcode: StoreModuleVar): Nothing = TODO("FunctionCompiler::visitStoreModuleVar")
    
    override fun visitCollectRestArgs() {
        pushArguments

        dup
        invokeinterface<List<*>>("size", int)
        ldc(functionInfo.ir.argCount - 1)
        swap

        invokeinterface<List<*>>("subList", List::class, int, int)
        invokestatic<AOs>("createArrayFromList", JSValue::class, List::class)
    }

    override fun visitPushClassInstanceFieldsSymbol() =
        getstatic<Realm.InternalSymbols>("classInstanceFields", JSSymbol::class)

    private fun getOrCreateLocal(transformerLocal: TransformerLocal, type: LocalType): Local {
        return localMap.getOrPut(transformerLocal) {
            Local(currentLocalIndex++, type)
        }
    }
}
