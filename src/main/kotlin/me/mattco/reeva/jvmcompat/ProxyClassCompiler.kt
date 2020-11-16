package me.mattco.reeva.jvmcompat

import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.insns.sugar.construct
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

            classes.forEach {
                it.declaredFields.filterNot { field ->
                    Modifier.isStatic(field.modifiers) || Modifier.isFinal(field.modifiers)
                }.forEach { field ->
                    field(Modifiers(field.modifiers and Modifier.ABSTRACT.inv()), field.name, field.type)
                }
            }

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

            classes.forEach {
                it.declaredMethods.filterNot { method ->
                    Modifier.isStatic(method.modifiers) || Modifier.isFinal(method.modifiers)
                }.forEach { method ->
                    method(Modifiers(method.modifiers and Modifier.ABSTRACT.inv()), method.name, method.returnType, *method.parameterTypes) {
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
                            return@method
                        }

                        ldc(coerceType(method.returnType))
                        invokestatic(JVMValueMapper::class, "coerceValueToType", Any::class, JSValue::class, Class::class)

                        when (method.returnType) {
                            Float::class.java -> {
                                checkcast("java/lang/Float")
                                invokevirtual("java/lang/Float", "doubleValue", Double::class)
                                freturn
                            }
                            Double::class.java -> {
                                checkcast("java/lang/Double")
                                invokevirtual("java/lang/Double", "doubleValue", Double::class)
                                dreturn
                            }
                            Long::class.java -> {
                                checkcast("java/lang/Long")
                                invokevirtual("java/lang/Long", "longValue", Long::class)
                                lreturn
                            }
                            Byte::class.java -> {
                                checkcast("java/lang/Byte")
                                invokevirtual("java/lang/Byte", "byteValue", Byte::class)
                                ireturn
                            }
                            Short::class.java -> {
                                checkcast("java/lang/Short")
                                invokevirtual("java/lang/Short", "shortValue", Double::class)
                                ireturn
                            }
                            Char::class.java -> {
                                checkcast("java/lang/Char")
                                invokevirtual("java/lang/Char", "charValue", Double::class)
                                ireturn
                            }
                            Int::class.java -> {
                                checkcast("java/lang/Integer")
                                invokevirtual("java/lang/Integer", "intValue", Int::class)
                                ireturn
                            }
                            else -> {
                                checkcast(method.returnType)
                                areturn
                            }
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
