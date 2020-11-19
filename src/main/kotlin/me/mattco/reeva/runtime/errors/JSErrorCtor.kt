package me.mattco.reeva.runtime.errors

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.JSArguments
import me.mattco.reeva.utils.argument
import me.mattco.reeva.utils.toValue

open class JSErrorCtor protected constructor(
    realm: Realm,
    name: String = "Error"
) : JSNativeFunction(realm, name, 1) {
    init {
        isConstructable = true
    }

    open fun constructErrorObj(): JSErrorObject {
        return JSErrorObject.create(realm)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
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
        fun create(realm: Realm) = JSErrorCtor(realm).initialize()
    }
}
