package com.reevajs.reeva.runtime.errors

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.key

open class JSErrorCtor protected constructor(
    realm: Realm,
    name: String = "Error"
) : JSNativeFunction(realm, name, 1) {
    open fun errorProto(realm: Realm): JSObject {
        return realm.errorProto
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        val newTarget = arguments.newTarget.ifUndefined {
            // TODO: "If NewTarget is undefined, let newTarget be the active
            //  function object; else let newTarget be NewTarget."
            JSUndefined
        }
        val obj = AOs.ordinaryCreateFromConstructor(
            newTarget,
            listOf(Slot.ErrorData),
            defaultProto = ::errorProto,
        )
        val message = arguments.argument(0)

        if (message != JSUndefined) {
            val msg = message.toJSString()
            val msgDesc = Descriptor(msg, attrs { +conf; -enum; +writ })
            AOs.definePropertyOrThrow(obj, "message".key(), msgDesc)
        }

        return obj
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSErrorCtor(realm).initialize()
    }
}
