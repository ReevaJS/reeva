package com.reevajs.reeva.compiler.generators

import codes.som.koffee.assembleClass
import codes.som.koffee.MethodAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import codes.som.koffee.modifiers.public
import codes.som.koffee.modifiers.final
import codes.som.koffee.types.void
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.compiler.*
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.jvmcompat.JVMValueMapper
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.transformer.opcodes.ConstructSuper
import com.reevajs.reeva.transformer.opcodes.ConstructSuperArray
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.expect
import org.objectweb.asm.Type
import java.lang.reflect.Modifier
import java.util.Collections

class ConstructorGenerator(private val compiler: ClassCompiler) {
    // TODO: This should be cached somehow
    fun generate(): Class<*> {
        val length = compiler.constructorDescriptor.functionInfo.length

        val clazz = assembleClass(
            public + final,
            compiler.ctorClassPath,
            superClass = JSNativeFunction::class,
            interfaces = listOf(GeneratedObjectConstructor::class),
        ) {
            field<List<*>>(private, "staticFieldKeys")
            field<List<*>>(private, "staticMethodKeys")
            field<JSObject>(private, "associatedPrototype")

            method(public, "<init>", void, Realm::class, JSObject::class) {
                aload_0
                aload_1
                ldc(compiler.className)
                ldc(length)
                invokespecial<JSNativeFunction>("<init>", void, Realm::class, String::class, Int::class)

                aload_0
                aload_2
                putfield(compiler.ctorClassPath, "associatedPrototype", JSObject::class)

                _return
            }

            method(public + final, "setStaticFieldKeys", void, List::class) {
                aload_0
                aload_1
                putfield(compiler.ctorClassPath, "staticFieldKeys", List::class)
                _return
            }

            method(public + final, "setStaticMethodKeys", void, List::class) {
                aload_0
                aload_1
                putfield(compiler.ctorClassPath, "staticMethodKeys", List::class)
                _return
            }

            method(public, "init", void) {
                aload_0
                invokespecial<JSObject>("init", void)

                for ((index, descriptor) in compiler.staticFieldDescriptors.withIndex()) {
                    aload_0

                    aload_0
                    getfield(compiler.ctorClassPath, "staticFieldKeys", List::class)
                    ldc(index)
                    invokeinterface<List<*>>("get", Any::class, int)
                    checkcast<PropertyKey>()

                    if (descriptor.functionInfo != null) {
                        Impl(this, descriptor.functionInfo).visitIR()
                    } else {
                        pushUndefined
                    }

                    ldc(0)

                    invokevirtual(compiler.ctorClassPath, "defineOwnProperty", void, PropertyKey::class, JSValue::class, int)
                }

                for ((index, descriptor) in compiler.staticMethodDescriptors.withIndex()) {
                    val isGetterSetter = descriptor.kind.let {
                        it == MethodDefinitionNode.Kind.Getter || it == MethodDefinitionNode.Kind.Setter
                    }
        
                    if (descriptor.kind != MethodDefinitionNode.Kind.Normal && !isGetterSetter)
                        TODO()
        
                    // Receiver for call to define method
                    aload_0
        
                    // Property key
                    aload_0
                    getfield(compiler.ctorClassPath, "staticMethodKeys", List::class)
                    ldc(index)
                    invokeinterface<List<*>>("get", Any::class, int)
                    checkcast<PropertyKey>()
        
                    if (!isGetterSetter)
                        ldc(descriptor.functionInfo.length)
        
                    // Function
                    IndyUtils(this@assembleClass, this).generateMethod(descriptor.key.toString()) {
                        Impl(this, descriptor.functionInfo).visitIR()
                    }
        
                    // Set on receiver
                    when (descriptor.kind) {
                        MethodDefinitionNode.Kind.Normal -> invokevirtual(
                            compiler.ctorClassPath,
                            "defineBuiltin",
                            void,
                            PropertyKey::class,
                            int,
                            Function1::class,
                        )
                        MethodDefinitionNode.Kind.Getter -> invokevirtual(
                            compiler.ctorClassPath,
                            "defineBuiltinGetter",
                            void,
                            PropertyKey::class,
                            Function1::class,
                        )
                        MethodDefinitionNode.Kind.Setter -> invokevirtual(
                            compiler.ctorClassPath,
                            "defineBuiltinSetter",
                            void,
                            PropertyKey::class,
                            Function1::class,
                        )
                        else -> TODO()
                    }
                }

                _return
            }

            val constructors = compiler.superClass?.declaredConstructors?.filter {
                Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers)
            }?.groupBy { it.parameterCount }

            method(public, "evaluate", JSValue::class, JSArguments::class) {
                val impl = Impl(this, compiler.constructorDescriptor.functionInfo)

                // "new" check
                aload_1
                invokevirtual<JSArguments>("getThisValue", JSValue::class)
                getstatic<JSUndefined>("INSTANCE", JSUndefined::class)
                ifStatement(JumpCondition.RefEqual) {
                    construct<Errors.CtorCallWithoutNew>(String::class) {
                        ldc(compiler.className)
                    }

                    invokevirtual<Error>("throwTypeError", Void::class.java)
                    pop
                }

                impl.pushRealm
                aload_0
                getfield(compiler.ctorClassPath, "associatedPrototype", JSObject::class)
                invokestatic<JSObject>("create", JSObject::class, Realm::class, JSValue::class)
                dup
                val wrapper = astore()
                impl.wrapperLocal = wrapper

                pushSlot("Impl")

                // Generate constructor call
                if (constructors == null) {
                    construct(compiler.implClassPath, Realm::class, JSObject::class) {
                        impl.pushRealm
                        aload(wrapper)
                    }
                } else {
                    val constructor = constructors.values.singleOrNull()?.singleOrNull()
                        ?: TODO("Support multiple constructors")

                    construct(compiler.implClassPath, Realm::class, JSObject::class, *constructor.parameterTypes) {
                        impl.pushRealm
                        aload(wrapper)

                        for ((index, param) in constructor.parameterTypes.withIndex()) {
                            val paramType = Type.getType(param)

                            aload_1
                            ldc(index)
                            invokevirtual<JSArguments>("argument", JSValue::class, int)
                            ldc(paramType)
                            invokestatic<JVMValueMapper>("jsToJvm", Any::class, JSValue::class, Class::class)
                            checkcast(paramType)
                        }
                    }
                }

                invokevirtual<JSObject>("setSlot", void, int, Any::class)

                // Invoke user constructor
                impl.visitIR()

                pushUndefined
                areturn
            }
        }

        return CompilerClassLoader.load(clazz)
    }

    private inner class Impl(
        methodAssembly: MethodAssembly,
        functionInfo: FunctionInfo,
    ) : BaseGenerator(methodAssembly, functionInfo) {
        lateinit var wrapperLocal: Local

        override val pushReceiver: Unit
            get() {
                // This would only be invoked from the "evaluate" method context, as the only other
                // place this is used would be in a static method, where "this." would be an error
                aload(wrapperLocal)
            }

        override val pushArguments: Unit
            get() = aload_1

        override val pushRealm: Unit
            get() {
                aload_0
                invokevirtual<JSObject>("getRealm", Realm::class)
            }

        override fun visitConstructSuper(opcode: ConstructSuper) {
            construct<ArrayList<*>>()
            val list = astore()
    
            repeat(opcode.argCount) {
                aload(list)
                swap
                invokevirtual<ArrayList<*>>("add", Boolean::class, Any::class)
                pop
            }
    
            aload(list)
            invokestatic<Collections>("reverse", void, List::class)
            constructSuperImpl(list)
        }

        override fun visitConstructSuperArray() {
            invokestatic<AOs>("iterableToList", List::class, JSValue::class)
            constructSuperImpl(astore())
        }
    
        private fun constructSuperImpl(argsLocal: Local) {    
            pushReceiver
            pushSlot("Impl")
            dup2
            invokevirtual<JSObject>("getSlot", Any::class, int)
            ifStatement(JumpCondition.NonNull) {
                getstatic<Errors.Class.DuplicateSuperCall>("INSTANCE", Errors.Class.DuplicateSuperCall::class)
                invokevirtual<Error>("throwTypeError", Void::class)
                pop
            }
    
            aload(argsLocal)
            ldc(Type.getType("L${compiler.implClassPath};"))
            invokestatic<CompilerAOs>("constructSuper", Any::class, List::class, Class::class)
            invokevirtual<JSObject>("setSlot", void, int, Any::class)
    
            pushReceiver
        }
    }
}
