package me.mattco.reeva.runtime.errors

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*

open class JSErrorCtor protected constructor(
    realm: Realm,
    name: String = "Error"
) : JSNativeFunction(realm, name, 1) {
    init {
        isConstructable = true
    }

    open fun errorProto(): JSObject {
        return realm.errorProto
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = super.newTarget.ifUndefined {
            Agent.runningContext.function ?: JSUndefined
        }
        val obj = Operations.ordinaryCreateFromConstructor(newTarget, errorProto(), listOf(SlotName.ErrorData))
        val message = arguments.argument(0)

        if (message != JSUndefined) {
            val msg = Operations.toString(message)
            val msgDesc = Descriptor(msg, attrs { +conf -enum +writ })
            Operations.definePropertyOrThrow(obj, "message".key(), msgDesc)

        }

        return obj
    }

    companion object {
        fun create(realm: Realm) = JSErrorCtor(realm).initialize()
    }
}
