package me.mattco.reeva.runtime

import me.mattco.reeva.compiler.ByteClassLoader
import me.mattco.reeva.compiler.Compiler
import me.mattco.reeva.compiler.TopLevelScript
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.errors.JSErrorObject
import me.mattco.reeva.runtime.values.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.utils.expect
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.full.primaryConstructor

class Agent(val signifier: Any = "Agent${objectCount++}") {
    private lateinit var globalEnv: GlobalEnvRecord
    private val classLoader = ByteClassLoader()

    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)
    }

    fun execute(scriptRecord: Realm.ScriptRecord) {
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
        val compilationResult = compiler.compile()

        val mainClassNode = compilationResult.mainClass
        val dependencies = compilationResult.dependencies
        dependencies.forEach { classNode ->
            compileClassNode(classNode, false)
        }

        val mainClass = compileClassNode(mainClassNode, true)
        val topLevelScript = mainClass.newInstance() as TopLevelScript
        val ret = topLevelScript.run(runningContext)

        if (ret is JSErrorObject) {
            print("\u001b[31m")
            print("${ret.name}: ${ret.message}")
            println("\u001b[0m")
        }

        runningContextStack.remove(newContext)
    }

    private fun compileClassNode(classNode: ClassNode, isMainFile: Boolean): Class<*> {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
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
