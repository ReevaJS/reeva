package me.mattco.jsthing.runtime.values.nonprimitives.arrays

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.annotations.JSMethod
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject
import me.mattco.jsthing.runtime.values.primitives.JSUndefined

class JSArrayProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    @JSMethod("push", 0)
    private fun push(context: ExecutionContext, arguments: List<JSValue>): JSValue {
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSArrayProto(realm).also { it.init() }
    }
}
