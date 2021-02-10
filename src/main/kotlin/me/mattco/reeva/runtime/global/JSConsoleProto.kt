package me.mattco.reeva.runtime.global

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSSymbol
import me.mattco.reeva.runtime.primitives.JSUndefined

class JSConsoleProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()
        defineNativeFunction("log", 0, function = ::log)
    }

    fun log(arguments: JSArguments): JSValue {
        println(arguments.joinToString(separator = " ") {
            if (it is JSSymbol) {
                it.descriptiveString()
            } else Operations.toString(it).string
        })
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSConsoleProto(realm).initialize()
    }
}
