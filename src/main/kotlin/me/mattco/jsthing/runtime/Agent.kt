package me.mattco.jsthing.runtime

import me.mattco.jsthing.ast.ScriptNode
import me.mattco.jsthing.parser.Parser
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.GlobalEnvRecord
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Attributes
import me.mattco.jsthing.runtime.values.nonprimitives.objects.Descriptor
import me.mattco.jsthing.utils.ecmaAssert
import java.io.File
import java.nio.ByteOrder

class Agent(val signifier: Any = "Agent${agentCount++}") {
    internal val littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    private val contexts = mutableListOf<ExecutionContext>()
    internal val runningContext: ExecutionContext
        get() = contexts.last()

    private var createGlobalObjectHook = { realm: Realm -> JSGlobalObject(realm) }
    private var executed = false

    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)

        // Begin InitializeHostDefinedRealm
        val realm = Realm(this)
        val context = ExecutionContext(this, realm, function = null)
        contexts.add(context)
        realm.init()

        // Begin SetRealmGlobalObject
        val globalObj = createGlobalObjectHook(realm)
        ecmaAssert(globalObj.isObject)
        realm.globalObject = globalObj
        val newGlobalEnv = GlobalEnvRecord.create(globalObj)
        realm.globalEnv = newGlobalEnv
        // End SetRealmGlobalObject

        // Begin SetDefaultGlobalBindings
        // TODO: Add all the properties here
        globalObj.defineOwnProperty(
            "globalThis",
            Descriptor(globalObj, Attributes(Attributes.WRITABLE and Attributes.CONFIGURABLE))
        )
        // End SetDefaultGlobalBindings
        // End InitializeHostDefinedRealm
    }

    fun createGlobalObject(hook: (Realm) -> JSGlobalObject) {
        createGlobalObjectHook = hook
    }

    fun execute(file: File) {
        execute(file.readText())
    }

    fun execute(script: String) {
        execute(Parser(script).parseScript())
    }

    fun execute(script: ScriptNode) {
        if (executed) {
            TODO("Support running multiple things on a single Agent")
        }
        executed = true

        val globalEnv = runningContext.realm.globalEnv
        runningContext.lexicalEnv = globalEnv
        runningContext.variableEnv = globalEnv
    }

    internal fun addContext(context: ExecutionContext) {
        contexts.add(context)
    }

    internal fun popContext() = contexts.removeLast()

    companion object {
        private var agentCount = 0
        private val agentIdentifiers = mutableSetOf<Any>()
    }
}
