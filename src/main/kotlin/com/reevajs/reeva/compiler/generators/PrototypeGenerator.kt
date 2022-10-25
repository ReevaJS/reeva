package com.reevajs.reeva.compiler.generators

import codes.som.koffee.assembleClass
import codes.som.koffee.MethodAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import codes.som.koffee.modifiers.*
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.compiler.*
import com.reevajs.reeva.jvmcompat.JVMValueMapper
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObjectProto
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.transformer.opcodes.*
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.utils.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.reflect.Modifier

class PrototypeGenerator(private val compiler: ClassCompiler) {
    fun generate(): ClassNode {
        return assembleClass(
            public + final,
            compiler.protoClassPath,
            superClass = JSObject::class,
            interfaces = listOf(GeneratedObjectPrototype::class),
        ) {
            field<List<*>>(private, "instanceMethodKeys")

            method(public, "<init>", void, Realm::class) {
                aload_0
                aload_1
                dup
                invokevirtual<Realm>("getObjectProto", JSObjectProto::class)
                invokespecial<JSObject>("<init>", void, Realm::class, JSValue::class)
                _return
            }

            method(public + final, "setInstanceMethodKeys", void, List::class) {
                aload_0
                aload_1
                putfield(compiler.protoClassPath, "instanceMethodKeys", List::class)
                _return
            }

            method(public + final, "init", void) {
                aload_0
                invokespecial<JSObject>("init", void)

                for (field in compiler.superClass?.declaredFields.orEmpty()) {
                    if (Modifier.isStatic(field.modifiers) || field.name == "Companion")
                        continue

                    // receiver
                    aload_0

                    // Key
                    ldc(field.name)

                    // attributes
                    ldc(0)

                    // Getter
                    IndyUtils(this@assembleClass, this).generateGetter(field.name) {
                        getterSetterPrelude(field.name)
                        getfield(compiler.implClassPath, field.name, field.type)
                        boxIfNecessary(field.type)
                        invokestatic<JVMValueMapper>("jvmToJS", JSValue::class, Any::class)
                        areturn
                    }

                    // Setter
                    IndyUtils(this@assembleClass, this).generateSetter(field.name) {
                        if (Modifier.isFinal(field.modifiers)) {
                            construct<Errors.JVMClass.FinalFieldSet>(String::class, String::class) {
                                ldc(compiler.className)
                                ldc(field.name)
                            }
                            invokevirtual<Error>("throwTypeError", Void::class.java)
                            pop
                        } else {
                            getterSetterPrelude(field.name)

                            aload_2
                            ldc(Type.getType(field.type))
                            invokestatic<JVMValueMapper>("jsToJvm", Any::class, JSValue::class, Class::class)

                            putfield(compiler.implClassPath, field.name, field.type)
                        }

                        _return
                    }

                    invokevirtual<JSObject>(
                        "defineNativeProperty",
                        void,
                        String::class,
                        int,
                        Function1::class,
                        Function2::class,
                    )
                }

                for ((index, descriptor) in compiler.instanceMethodDescriptors.withIndex()) {
                    val isGetterSetter = descriptor.kind.let {
                        it == MethodDefinitionNode.Kind.Getter || it == MethodDefinitionNode.Kind.Setter
                    }
        
                    if (descriptor.kind != MethodDefinitionNode.Kind.Normal && !isGetterSetter)
                        TODO()
        
                    // Receiver for call to define method
                    aload_0
        
                    // Property key
                    aload_0
                    getfield(compiler.protoClassPath, "instanceMethodKeys", List::class)
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
                            compiler.protoClassPath,
                            "defineBuiltin",
                            void,
                            PropertyKey::class,
                            int,
                            Function1::class,
                        )
                        MethodDefinitionNode.Kind.Getter -> invokevirtual(
                            compiler.protoClassPath,
                            "defineBuiltinGetter",
                            void,
                            PropertyKey::class,
                            Function1::class,
                        )
                        MethodDefinitionNode.Kind.Setter -> invokevirtual(
                            compiler.protoClassPath,
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
        }
    }

    private fun MethodAssembly.getterSetterPrelude(fieldName: String) {
        aload_0
        checkcast<JSObject>()

        pushSlot("Impl")
        invokevirtual<JSObject>("getSlot", Any::class, int)

        dup
        instanceof(compiler.implClassPath)
        ifStatement(JumpCondition.False) {
            pop

            construct<Errors.JVMClass.IncompatibleFieldGet>(String::class, String::class) {
                ldc(compiler.className)
                ldc(fieldName)
            }
            invokevirtual<Error>("throwTypeError", Void::class.java)
            pop

            generateUnreachable()
        }

        checkcast(compiler.implClassPath)
    }

    private inner class Impl(
        methodAssembly: MethodAssembly,
        functionInfo: FunctionInfo,
    ) : BaseGenerator(methodAssembly, functionInfo) {
        override val pushReceiver: Unit
            get() {
                aload_0
                invokevirtual<JSArguments>("getThisValue", JSValue::class)
            }

        override val pushArguments: Unit
            get() = aload_0

        override val pushRealm: Unit
            get() {
                invokestatic<Agent>("getActiveAgent", Agent::class)
                invokevirtual<Agent>("getActiveRealm", Realm::class)
            }

        override fun visitConstructSuper(opcode: ConstructSuper) = unreachable()

        override fun visitConstructSuperArray() = unreachable()
    }
}
