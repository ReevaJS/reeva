package com.reevajs.reeva.compiler.generators

import codes.som.koffee.assembleClass
import codes.som.koffee.MethodAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import codes.som.koffee.modifiers.*
import codes.som.koffee.types.void
import com.reevajs.reeva.ast.literals.MethodDefinitionNode
import com.reevajs.reeva.compiler.*
import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.jvmcompat.JVMValueMapper
import com.reevajs.reeva.transformer.FunctionInfo
import com.reevajs.reeva.transformer.opcodes.ConstructSuper
import com.reevajs.reeva.transformer.opcodes.ConstructSuperArray
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.unreachable
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

class InstanceGenerator(private val compiler: ClassCompiler) {
    fun generate(): ClassNode {
        val superClass = compiler.superClass
        val interfaces = compiler.interfaces
        val constructors = superClass?.constructors

        return assembleClass(
            public + final,
            compiler.implClassPath,
            superClass = superClass ?: Object::class.java,
            interfaces = interfaces,
        ) {
            field<Realm>(private + final, "realm")
            field<JSObject>(private + final, "wrapper")

            // Generate a constructor for every constructor in the super class

            if (constructors == null) {
                method(public, "<init>", void, Realm::class, JSObject::class) {
                    aload_0
                    invokespecial<Any>("<init>", void)
                    generateCommonCtorImpl()
                }
            } else {
                for (constructor in constructors) {
                    method(
                        Modifiers(constructor.modifiers),
                        "<init>",
                        void,
                        Realm::class,
                        JSObject::class,
                        *constructor.parameterTypes,
                    ) {
                        aload_0

                        for (i in constructor.parameterTypes.indices)
                            loadType(Type.getType(constructor.parameterTypes[i]), i + 3)

                        invokespecial(
                            superClass.canonicalName!!.replace(".", "/"),
                            "<init>",
                            void,
                            *constructor.parameterTypes,
                        )

                        generateCommonCtorImpl()
                    }
                }
            }

            // TODO: Fields

            for (descriptor in compiler.instanceMethodDescriptors) {
                if (!descriptor.key.isString)
                    continue

                val methodName = descriptor.key.asString

                if (descriptor.kind != MethodDefinitionNode.Kind.Normal)
                    TODO()

                val targetMethods = listOfNotNull(superClass, *interfaces.toTypedArray()).flatMap { clazz ->
                    clazz.declaredMethods.filter {
                        it.name == methodName
                    }
                }

                // TODO: Optimize this out if targetMethods.size == 1?

                for (targetMethod in targetMethods) {
                    method(
                        public + final,
                        methodName,
                        targetMethod.returnType,
                        *targetMethod.parameterTypes,
                        exceptions = targetMethod.exceptionTypes.filterNotNull().toTypedArray()
                    ) {
                        aload_0

                        construct<JSArguments>()

                        // Insert receiver
                        dup
                        aload_0
                        getfield(compiler.implClassPath, "wrapper", JSObject::class)
                        invokevirtual<JSArguments>("add", Boolean::class, JSValue::class)
                        pop

                        // Insert new.target
                        dup
                        getstatic<JSUndefined>("INSTANCE", JSUndefined::class)
                        invokevirtual<JSArguments>("add", Boolean::class, JSValue::class)
                        pop

                        // Insert arguments
                        for (i in targetMethod.parameterTypes.indices) {
                            dup
                            aload(i + 1)
                            invokestatic<JVMValueMapper>("jvmToJS", JSValue::class, Any::class)
                            invokevirtual<JSArguments>("add", Boolean::class, JSValue::class)
                            pop
                        }

                        // Invoke implementation method
                        invokevirtual(compiler.implClassPath, methodName + "Impl", JSValue::class, JSArguments::class)

                        if (targetMethod.returnType == Void.TYPE) {
                            pop
                            _return
                            return@method
                        }

                        val returnType = Type.getType(targetMethod.returnType)
                        ldc(returnType)
                        invokestatic<JVMValueMapper>("jsToJvm", Any::class, JSValue::class, Class::class)
                        checkcast(returnType)

                        when (targetMethod.returnType) {
                            Int::class.java -> ireturn
                            Long::class.java -> lreturn
                            Float::class.java -> freturn
                            Double::class.java -> dreturn
                            else -> areturn
                        }
                    }
                }

                method(private + final, methodName + "Impl", JSValue::class, JSArguments::class) {
                    Impl(this, descriptor.functionInfo).visitIR()
                }
            }
        }
    }
    
    private fun MethodAssembly.generateCommonCtorImpl() {
        // this.realm = realm;
        aload_0
        aload_1
        putfield(compiler.implClassPath, "realm", Realm::class)

        // this.wrapper = wrapper;
        aload_0
        aload_2
        putfield(compiler.implClassPath, "wrapper", JSObject::class)

        _return
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
