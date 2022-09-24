package com.reevajs.reeva.runtime.wrappers.strings

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.PropertyKey
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSString
import com.reevajs.reeva.utils.toValue

open class JSStringObject protected constructor(realm: Realm, string: JSString) : JSObject(realm) {
    val string by slot(SlotName.StringData, string)

    override fun init() {
        val realm = Agent.activeAgent.getActiveRealm()
        setPrototype(realm.stringProto)
        super.init()

        defineOwnProperty("length", string.string.length.toValue(), 0)
    }

    override fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        val desc = super.getOwnPropertyDescriptor(property)
        if (desc != null)
            return desc
        return stringGetOwnProperty(property)
    }

    override fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        val desc = stringGetOwnProperty(property)
        if (desc != null)
            return Operations.isCompatiblePropertyDescriptor(isExtensible(), descriptor, desc)
        return super.defineOwnProperty(property, descriptor)
    }

    override fun ownPropertyKeys(onlyEnumerable: Boolean): List<PropertyKey> {
        val keys = mutableListOf<PropertyKey>()
        string.string.indices.map(PropertyKey::from).forEach(keys::add)
        return super.ownPropertyKeys(onlyEnumerable) + keys
    }

    @ECMAImpl("9.4.3.5")
    private fun stringGetOwnProperty(property: PropertyKey): Descriptor? {
        if (property.isSymbol)
            return null
        val index = Operations.canonicalNumericIndexString(property.asValue) ?: return null
        if (!Operations.isIntegralNumber(index))
            return null
        if (index.isNegativeZero)
            return null
        val indexInt = index.asInt
        if (indexInt < 0 || indexInt >= string.string.length)
            return null
        return Descriptor(string.string[indexInt].toValue(), Descriptor.HAS_BASIC or Descriptor.ENUMERABLE)
    }

    companion object {
        fun create(string: JSString, realm: Realm = Agent.activeAgent.getActiveRealm()) =
            JSStringObject(realm, string).initialize()
    }
}
