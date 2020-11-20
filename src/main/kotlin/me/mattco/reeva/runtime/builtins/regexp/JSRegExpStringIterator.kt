package me.mattco.reeva.runtime.builtins.regexp

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSString

class JSRegExpStringIterator private constructor(
    realm: Realm,
    var iteratingRegExp: JSRegExpObject,
    val iteratedString: JSString,
    val global: Boolean,
    val unicode: Boolean,
) : JSObject(realm, realm.regExpStringIteratorProto) {
    var done = false

    companion object {
        fun create(realm: Realm, regexp: JSRegExpObject, string: JSString, global: Boolean, unicode: Boolean) =
            JSRegExpStringIterator(realm, regexp, string, global, unicode).initialize()
    }
}
