package com.reevajs.reeva.compiler

import codes.som.anthony.koffee.assembleClass
import codes.som.anthony.koffee.insns.jvm.*
import codes.som.anthony.koffee.modifiers.public
import com.reevajs.reeva.compiler.graph.GraphBuilder
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSFunction
import com.reevajs.reeva.transformer.TransformedSource
import org.objectweb.asm.ClassWriter
import java.io.File

class Compiler(val transformedSource: TransformedSource) {
    fun compile(realm: Realm): JSFunction {
        val graph = GraphBuilder(transformedSource).build()

        val className = "${transformedSource.functionInfo.name.replace('.', '_')}$${classCount++}"

        val classNode = assembleClass(public, className, superName = SUPER_CLASS) {
            method(public, "<init>", void, Realm::class) {
                aload_0
                aload_1
                ldc(transformedSource.functionInfo.name)
                ldc(transformedSource.functionInfo.isStrict)
                invokespecial<JSCompiledFunction>(
                    "<init>",
                    void,
                    Realm::class,
                    String::class,
                    Boolean::class,
                )
                _return
            }

            method(public, "evaluate", JSValue::class, JSArguments::class) {
                MethodCompiler(className, transformedSource, this.node).compile()
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        val bytes = writer.toByteArray()
        File("./demo/classes/$className.class").writeBytes(bytes)

        val clazz = classLoader.compileClassBytes(className, bytes)
        return clazz.declaredConstructors[0].newInstance(realm) as JSFunction
    }

    companion object {
        private const val SUPER_CLASS = "com/reevajs/reeva/compiler/JSCompiledFunction"
        private var classCount = 0
        private val classLoader = CompilerClassLoader()
    }
}