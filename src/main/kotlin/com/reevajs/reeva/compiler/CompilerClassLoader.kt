package com.reevajs.reeva.compiler

class CompilerClassLoader : ClassLoader() {
    private val compiledClasses = mutableMapOf<String, ByteArray>()

    fun compileClassBytes(name: String, bytes: ByteArray): Class<*> {
        compiledClasses[name] = bytes
        return loadClass(name)
    }

    override fun findClass(name: String?): Class<*> {
        val result = compiledClasses[name]
        if (result != null)
            return defineClass(name, result, 0, result.size)

        return super.findClass(name)
    }
}
