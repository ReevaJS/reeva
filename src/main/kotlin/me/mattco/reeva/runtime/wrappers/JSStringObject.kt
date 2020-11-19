package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSNativePropertyGetter
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSNumber
import me.mattco.reeva.runtime.primitives.JSString
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.toValue

open class JSStringObject protected constructor(realm: Realm, val string: JSString) : JSObject(realm) {
    override fun init() {
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
        string.string.indices.map(::PropertyKey).forEach(keys::add)
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
        fun create(realm: Realm, string: JSString) = JSStringObject(realm, string).initialize()
    }
}
