package me.mattco.reeva.runtime.values.errors

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.JSNativeFunction
import me.mattco.reeva.runtime.values.objects.Descriptor
import me.mattco.reeva.runtime.values.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

open class JSErrorCtor protected constructor(
    realm: Realm,
    name: String = "Error"
) : JSNativeFunction(realm, name, 1) {
    override val isConstructable = true

    open fun constructErrorObj(): JSErrorObject {
        return JSErrorObject.create(realm)
    }

    override fun call(thisValue: JSValue, arguments: JSArguments): JSValue {
        return construct(arguments, Agent.runningContext.function ?: JSUndefined)
    }

    override fun construct(arguments: JSArguments, newTarget: JSValue): JSValue {
        // TODO: Handle new target?
        val obj = constructErrorObj()
        val message = arguments.argument(0)
        val messageValue = if (message != JSUndefined) {
            Operations.toString(message)
        } else "".toValue()

        val msgDesc = Descriptor(messageValue, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        Operations.definePropertyOrThrow(obj, "message".toValue(), msgDesc)

        return obj
    }

    companion object {
        fun create(realm: Realm) = JSErrorCtor(realm).also { it.init() }
    }
}
