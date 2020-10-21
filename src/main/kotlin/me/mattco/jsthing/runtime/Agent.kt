package me.mattco.jsthing.runtime

import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.GlobalEnvRecord
import me.mattco.jsthing.runtime.values.nonprimitives.functions.JSFunctionProto
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObjectProto
import java.util.concurrent.CopyOnWriteArrayList

class Agent(val signifier: Any = "Agent${agentCount++}") {
    init {
        if (signifier in agentIdentifiers)
            throw IllegalArgumentException("Agent cannot have duplicate signifier $signifier")
        agentIdentifiers.add(signifier)

//        // Begin InitializeHostDefinedRealm
//        val realm = Realm(this)
//        val context = ExecutionContext(this, realm, function = null)
//        contexts.add(context)
//        Agent.runningExecutionContext = context
//        realm.init()
//
//        // Begin SetRealmGlobalObject
//        val globalObj = createGlobalObjectHook(realm)
//        ecmaAssert(globalObj.isObject)
//        realm.globalObject = globalObj
//        val newGlobalEnv = GlobalEnvRecord.create(globalObj)
//        realm.globalEnv = newGlobalEnv
//        // End SetRealmGlobalObject
//
//        // Begin SetDefaultGlobalBindings
//        // TODO: Add all the properties here
//        globalObj.defineOwnProperty(
//            "globalThis",
//            Descriptor(globalObj, Attributes(Attributes.WRITABLE and Attributes.CONFIGURABLE))
//        )
//        // End SetDefaultGlobalBindings
//        // End InitializeHostDefinedRealm
    }

    fun execute(scriptRecord: Realm.ScriptRecord) {
        val realm = scriptRecord.realm
        val newContext = ExecutionContext(this, realm, null, scriptRecord.scriptOrModule)
        runningContextStack.add(newContext)

        val jsObjProto = JSObjectProto.create(realm)
        val jsFuncProto = JSFunctionProto.create(realm)
        realm.init(jsObjProto, jsFuncProto)

        val globalObj = JSObject.create(realm)
        realm.globalObject = globalObj
        val newGlobalEnv = GlobalEnvRecord.create(globalObj, globalObj)
        realm.globalEnv = newGlobalEnv
    }

    companion object {
        private var agentCount = 0
        private val agentIdentifiers = mutableSetOf<Any>()

        private val runningContextStack = CopyOnWriteArrayList<ExecutionContext>()

        @JvmStatic
        fun pushContext(context: ExecutionContext) {
            runningContextStack.add(context)
        }

        @JvmStatic
        fun popContext(context: ExecutionContext) {
            runningContextStack.removeLast()
        }

        // TODO: This is a hack for now, and doesn't really do much. However,
        // This should more-or-less be the final API. At any one time there is
        // only one running ExecutingContext, so this should be totally realizable
        @JvmStatic
        val runningContext: ExecutionContext
            get() = runningContextStack.last()
    }
}
