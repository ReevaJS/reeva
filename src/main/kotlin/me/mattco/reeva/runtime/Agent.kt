package me.mattco.reeva.runtime

import me.mattco.reeva.compiler.ByteClassLoader
import me.mattco.reeva.compiler.Compiler
import me.mattco.reeva.compiler.TopLevelScript
import me.mattco.reeva.interpreter.Interpreter
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.utils.expect
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class Agent(val signifier: Any = "Agent${objectCount++}") {
    private lateinit var globalEnv: GlobalEnvRecord
    private val classLoader = ByteClassLoader()

    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)
    }

    fun interpretedEvaluation(scriptRecord: Realm.ScriptRecord) {
        val realm = scriptRecord.realm
        val newContext = ExecutionContext(this, realm, null)
        runningContextStack.add(newContext)

        realm.initObjects()

        if (!::globalEnv.isInitialized) {
            val globalObj = JSObject.create(realm)
            realm.globalObject = globalObj
            globalEnv = GlobalEnvRecord.create(globalObj, globalObj)
            realm.globalEnv = globalEnv
        } else {
            realm.globalEnv = globalEnv
            realm.globalObject = globalEnv.globalThis
        }

        realm.populateGlobalObject()

        newContext.variableEnv = globalEnv
        newContext.lexicalEnv = globalEnv

        runningContextStack.add(newContext)

        val interpreter = Interpreter(scriptRecord)
        val start = System.nanoTime()
        val interpretationResult = interpreter.interpret(newContext)
        val interpretTime = System.nanoTime() - start

        if (interpretationResult.isAbrupt) {
            print("\u001b[31m")
            runningContext.error = null
            val error = interpretationResult.value
            expect(error is JSErrorObject)
            val name = Operations.getValue(error.get("name"))
            val message = Operations.getValue(error.get("message"))
            print("${Operations.toPrintableString(name)}: ${Operations.toPrintableString(message)}")
            println("\u001b[0m")
        }
        println("Execution time: ${interpretTime / 1_000_000}ms")

        runningContextStack.remove(newContext)
    }

    fun compiledEvaluation(scriptRecord: Realm.ScriptRecord) {
        val realm = scriptRecord.realm
        val newContext = ExecutionContext(this, realm, null)
        runningContextStack.add(newContext)

        realm.initObjects()

        if (!::globalEnv.isInitialized) {
            val globalObj = JSObject.create(realm)
            realm.globalObject = globalObj
            globalEnv = GlobalEnvRecord.create(globalObj, globalObj)
            realm.globalEnv = globalEnv
        } else {
            realm.globalEnv = globalEnv
            realm.globalObject = globalEnv.globalThis
        }

        realm.populateGlobalObject()

        newContext.variableEnv = globalEnv
        newContext.lexicalEnv = globalEnv

        runningContextStack.add(newContext)

        val scriptNode = scriptRecord.scriptOrModule
        val compiler = Compiler(scriptNode, "index_js")
        var start = System.nanoTime()
        val compilationResult = compiler.compile()
        val compileTime = System.nanoTime() - start

        val mainClassNode = compilationResult.mainClass
        val dependencies = compilationResult.dependencies
        dependencies.forEach { classNode ->
            compileClassNode(classNode, false)
        }

        val mainClass = compileClassNode(mainClassNode, true)
        val topLevelScript = mainClass.newInstance() as TopLevelScript
        start = System.nanoTime()
        val ret = topLevelScript.run(newContext)

        if (ret is JSErrorObject) {
            // TODO: We really need a better system to communicate errors to the caller.
            // If there is a leftover error in a context, no code will run. This may be
            // ok, but we should allow the current error to be easily and quickly examined
            // and consumed
            print("\u001b[31m")
            runningContext.error = null
            val name = Operations.getValue(ret.get("name"))
            val message = Operations.getValue(ret.get("message"))
            print("${Operations.toPrintableString(name)}: ${Operations.toPrintableString(message)}")
            println("\u001b[0m")
        }
        println("Compile time: ${(System.nanoTime() - start) / 1_000_000}ms")
        println("Execution time: ${(System.nanoTime() - start) / 1_000_000}ms")

        runningContextStack.remove(newContext)
    }

    private fun compileClassNode(classNode: ClassNode, isMainFile: Boolean): Class<*> {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        classNode.accept(writer)
        val bytes = writer.toByteArray()

        if (EMIT_DEBUG_OUTPUT)
            FileOutputStream("./demo/out/${classNode.name}.class").use { it.write(bytes) }

        classLoader.addClass(classNode.name, bytes)
        return classLoader.loadClass(classNode.name)
    }

    companion object {
        // Used to ensure names of various things are unique
        @Volatile
        @JvmStatic
        var objectCount = 0

        private const val EMIT_DEBUG_OUTPUT = true
        private val agentIdentifiers = mutableSetOf<Any>()

        private val runningContextStack = CopyOnWriteArrayList<ExecutionContext>()

        // TODO: This is a hack for now, and doesn't really do much. However,
        // This should more-or-less be the final API. At any one time there is
        // only one running ExecutingContext, so this should be totally realizable
        @JvmStatic
        val runningContext: ExecutionContext
            get() = runningContextStack.last()

        @JvmStatic
        fun pushContext(context: ExecutionContext) {
            runningContextStack.add(context)
        }

        @JvmStatic
        fun popContext() {
            runningContextStack.removeLast()
        }

        /**
         * Checks the running execution context for errors. Designed
         * to be used in the following way:
         *
         * fun method(): JSValue {
         *     // ...
         *     checkError() ?: return JSUndefined
         *     // ...
         * }
         *
         * where the "checkError()" call would return a nullable
         * value if there _is_ an error present. This is a bit
         * odd, but allows the above elegant syntax instead of having
         * to do checkError()?.also { return JSUndefined }
         */
        @JvmStatic
        fun checkError(): Unit? {
            if (runningContext.error != null)
                return null
            return Unit
        }

        @JvmStatic
        fun hasError() = runningContext.error != null

        @JvmStatic
        fun throwError(error: JSErrorObject) {
            expect(runningContext.error == null)
            runningContext.error = error
        }
    }
}
