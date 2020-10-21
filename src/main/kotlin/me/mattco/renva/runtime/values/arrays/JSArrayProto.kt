package me.mattco.renva.runtime.values.arrays

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.annotations.JSMethod
import me.mattco.renva.runtime.contexts.ExecutionContext
import me.mattco.renva.runtime.values.JSValue
import me.mattco.renva.runtime.values.objects.JSObject
import me.mattco.renva.runtime.values.primitives.JSUndefined

class JSArrayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSMethod("push", 0)
    private fun push(context: ExecutionContext, arguments: List<JSValue>): JSValue {
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSArrayProto(realm).also { it.init() }
    }
}
