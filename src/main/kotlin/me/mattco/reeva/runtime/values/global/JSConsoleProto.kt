package me.mattco.reeva.runtime.values.global

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSUndefined

class JSConsoleProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSMethod("log", 1)
    fun log(thisValue: JSValue, arguments: List<JSValue>): JSValue {
        println(arguments.joinToString(separator = " ") { Operations.toString(it).string })
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSConsoleProto(realm).also { it.init() }
    }
}
