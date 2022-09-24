package com.reevajs.reeva.runtime.global

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.toJSString

class JSConsoleProto private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)
        defineBuiltin(realm, "log", 0, ::log)
    }

    companion object {
        fun create(realm: Realm) = JSConsoleProto(realm).initialize(realm)

        @JvmStatic
        fun log(arguments: JSArguments): JSValue {
            println(
                arguments.joinToString(separator = " ") {
                    if (it is JSSymbol) {
                        it.descriptiveString()
                    } else {
                        val str = it.toJSString().string
                        if (it is JSArrayObject) "[$str]" else str
                    }
                }
            )
            return JSUndefined
        }
    }
}
