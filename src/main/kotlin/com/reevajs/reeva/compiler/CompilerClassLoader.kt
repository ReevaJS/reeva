package com.reevajs.reeva.compiler

import com.reevajs.reeva.core.Agent
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.net.URLClassLoader

class CompilerClassLoader private constructor() : URLClassLoader(emptyArray()) {
    fun load(node: ClassNode): Class<*> {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        node.accept(writer)
        val bytes = writer.toByteArray()

        Agent.activeAgent.compiledClassDebugDirectory?.let {
            File(it, node.name.takeLastWhile { it != '/' } + ".class").writeBytes(bytes)
        }

        node.name

        return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.size)
    }

    companion object {
        fun load(node: ClassNode) = CompilerClassLoader().load(node)
    }
}
