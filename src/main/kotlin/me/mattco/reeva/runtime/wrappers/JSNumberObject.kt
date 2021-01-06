package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSNumber

open class JSNumberObject protected constructor(realm: Realm, number: JSNumber) : JSObject(realm) {
    val number by slot(SlotName.NumberData, number)

    override fun init() {
        setPrototype(realm.numberProto)
        super.init()
    }

    companion object {
        fun create(realm: Realm, number: JSNumber) = JSNumberObject(realm, number).initialize()
    }
}
