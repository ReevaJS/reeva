package me.mattco.reeva.jvmcompat

import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.JumpCondition
import codes.som.anthony.koffee.insns.sugar.construct
import codes.som.anthony.koffee.insns.sugar.ifElseStatement
import codes.som.anthony.koffee.modifiers.Modifiers
import codes.som.anthony.koffee.modifiers.public
import me.mattco.reeva.Reeva
import me.mattco.reeva.compiler.Compiler
import me.mattco.reeva.compiler.ReevaClassLoader
import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.utils.expect
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Modifier

class ProxyClassCompiler : Compiler() {
    fun makeProxyClass(baseClass: Class<*>?, interfaces: List<Class<*>>): Class<*> {
        expect(baseClass != null || interfaces.isNotEmpty())
        expect(interfaces.all { it.isInterface })

        val className = buildString {
            append("JVMProxyClass")
            if (baseClass != null) {
                append("_")
                append(baseClass.simpleName)
            }

            if (interfaces.isNotEmpty())
                append("_I")

            interfaces.forEach {
                append("_")
                append(it.simpleName)
            }
        }.replace('.', '_')

        val superName = baseClass?.name?.replace('.', '/') ?: "java/lang/Object"

        val classes = listOfNotNull(baseClass) + interfaces

        val classNode = assembleClass(public, className, superName = superName, interfaces = interfaces.toList() + listOf(JVMProxyMarker::class.java)) {
            field(private + final, "jsDelegate", JSObject::class)

            baseClass?.declaredConstructors?.forEach { ctor ->
                method(Modifiers(ctor.modifiers), "<init>", void, JSObject::class, *ctor.parameterTypes) {
                    aload_0
                    for (i in 0 until ctor.parameterCount)
                        aload(i + 2)
                    invokespecial(superName, "<init>", void, *ctor.parameterTypes)

                    aload_0
                    aload_1
                    putfield(className, "jsDelegate", JSObject::class)
                    _return
                }
            }

            classes.forEach { clazz ->
                clazz.declaredMethods.filterNot { method ->
                    Modifier.isStatic(method.modifiers) || Modifier.isFinal(method.modifiers)
                }.forEach { method ->
                    method(Modifiers(method.modifiers and Modifier.ABSTRACT.inv()), method.name, method.returnType, *method.parameterTypes) {
                        data class RetInfo(val wrapperType: String, val primitiveMethod: String?, val instr: () -> Unit)

                        fun doReturn(coerce: Boolean = true) {
                            val (wrapperType, primitiveMethod, retInstr) = when (method.returnType) {
                                Float::class.java -> RetInfo("java/lang/Float", "floatValue") { freturn }
                                Double::class.java -> RetInfo("java/lang/Double", "doubleValue") { dreturn }
                                Long::class.java -> RetInfo("java/lang/Long", "longValue") { lreturn }
                                Byte::class.java -> RetInfo("java/lang/Byte", "byteValue") { ireturn }
                                Short::class.java -> RetInfo("java/lang/Short", "shortValue") { ireturn }
                                Char::class.java -> RetInfo("java/lang/Char", "charValue") { ireturn }
                                Int::class.java -> RetInfo("java/lang/Integer", "intValue") { ireturn }
                                else -> RetInfo(method.returnType.typeName, null) { areturn }
                            }

                            if (coerce) {
                                ldc(coerceType(wrapperType))
                                invokestatic(JVMValueMapper::class, "coerceValueToType", Any::class, JSValue::class, Class::class)

                                checkcast(wrapperType)
                                if (primitiveMethod != null)
                                    invokevirtual(wrapperType, primitiveMethod, method.returnType)
                            }

                            retInstr()
                        }

                        fun jsDelegation() {
                            aload_0
                            getfield(className, "jsDelegate", JSObject::class)
                            construct(PropertyKey::class, String::class) {
                                ldc(method.name)
                            }

                            construct(ArrayList::class)

                            for (i in 1..method.parameterCount) {
                                dup
                                loadRealm()
                                aload(i)
                                invokestatic(JVMValueMapper::class, "jvmToJS", JSValue::class, Realm::class, Any::class)
                                invokeinterface(List::class, "add", Boolean::class, Any::class)
                                pop
                            }

                            operation("invoke", JSValue::class, JSValue::class, PropertyKey::class, List::class)
                            if (method.returnType == Void::class.java) {
                                _return
                            } else doReturn()
                        }

                        val defaultImpls = clazz.declaredClasses.firstOrNull {
                            it.simpleName == "DefaultImpls" &&
                                Modifier.isStatic(it.modifiers) &&
                                Modifier.isPublic(it.modifiers) &&
                                Modifier.isFinal(it.modifiers)
                        }

                        val methodDefaultImpl = defaultImpls?.declaredMethods?.firstOrNull {
                            it.name == method.name && it.returnType == method.returnType &&
                                Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) &&
                                it.parameterCount >= 1 && it.parameterTypes[0].name == clazz.name
                        }

                        if (!Modifier.isAbstract(method.modifiers) || methodDefaultImpl != null) {
                            aload_0
                            getfield(className, "jsDelegate", JSObject::class)
                            construct(PropertyKey::class, String::class) {
                                ldc(method.name)
                            }
                            operation("hasOwnProperty", Boolean::class, JSValue::class, PropertyKey::class)
                            ifElseStatement(JumpCondition.True) {
                                ifBlock {
                                    jsDelegation()
                                }

                                elseBlock {
                                    aload_0
                                    for (i in 1..method.parameterCount)
                                        aload(i)
                                    if (methodDefaultImpl != null) {
                                        invokestatic(defaultImpls.name.replace('.', '/'), methodDefaultImpl.name, descriptor = Type.getMethodDescriptor(methodDefaultImpl))
                                    } else {
                                        invokevirtual(clazz.name.replace('.', '/'), method.name, descriptor = Type.getMethodDescriptor(method))
                                    }
                                    doReturn(coerce = false)
                                }
                            }
                        } else {
                            jsDelegation()
                        }
                    }
                }
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)

        if (Reeva.EMIT_CLASS_FILES) {
            FileOutputStream(File(Reeva.CLASS_FILE_DIRECTORY, "$className.class")).use {
                it.write(writer.toByteArray())
            }
        }

        val ccl = Agent.activeAgent.compilerClassLoader
        ccl.addClass(className, writer.toByteArray())
        return ccl.loadClass(className)
    }
}
