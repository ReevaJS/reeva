package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.key

class JSIteratorProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin(Realm.`@@iterator`.key(), 0, Builtin.IteratorProtoSymbolIterator)
    }

    companion object {
        fun create(realm: Realm) = JSIteratorProto(realm).initialize()

        @ECMAImpl("27.1.2.1")
        @JvmStatic
        fun `@@iterator`(realm: Realm, arguments: JSArguments): JSValue {
            return arguments.thisValue
        }
    }
}