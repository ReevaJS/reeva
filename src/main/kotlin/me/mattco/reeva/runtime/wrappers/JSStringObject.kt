package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue

open class JSStringObject protected constructor(realm: Realm, val string: JSString) : JSObject(realm) {
    override fun init() {
        internalSetPrototype(realm.stringProto)
        super.init()

        defineOwnProperty("length", string.string.length.toValue(), 0)
    }

    companion object {
        fun create(realm: Realm, string: JSString) = JSStringObject(realm, string).also { it.init() }
    }
}
