package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key

open class JSErrorCtor protected constructor(
    realm: Realm,
    name: String = "Error"
) : JSNativeFunction(realm, name, 1) {
    open fun errorProto(): JSObject {
        return realm.errorProto
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget.ifUndefined {
            // TODO: "If NewTarget is undefined, let newTarget be the active
            //  function object; else let newTarget be NewTarget."
            JSUndefined
        }
        val obj = Operations.ordinaryCreateFromConstructor(realm, newTarget, errorProto(), listOf(SlotName.ErrorData))
        val message = arguments.argument(0)

        if (message != JSUndefined) {
            val msg = Operations.toString(realm, message)
            val msgDesc = Descriptor(msg, attrs { +conf; -enum; +writ })
            Operations.definePropertyOrThrow(realm, obj, "message".key(), msgDesc)
        }

        return obj
    }

    companion object {
        fun create(realm: Realm) = JSErrorCtor(realm).initialize()
    }
}
