package com.reevajs.reeva.runtime.global

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined

class JSConsoleProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineNativeFunction("log", 0, function = ::log)
    }

    fun log(realm: Realm, arguments: JSArguments): JSValue {
        println(arguments.joinToString(separator = " ") {
            if (it is JSSymbol) {
                it.descriptiveString()
            } else Operations.toString(realm, it).string
        })
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSConsoleProto(realm).initialize()
    }
}
