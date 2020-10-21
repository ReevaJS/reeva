package me.mattco.renva.runtime.values.global

import me.mattco.renva.runtime.Operations
import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.annotations.JSMethod
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.primitives.JSUndefined

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
