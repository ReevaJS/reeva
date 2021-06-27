package me.mattco.reeva.core.environment

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.objects.PropertyKey
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.runtime.toBoolean
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.unreachable

class ObjectEnvRecord(
    realm: Realm,
    isStrict: Boolean,
    outer: EnvRecord?,
    val bindingObject: JSObject,
    private val isWithEnvironment: Boolean,
) : EnvRecord(realm, isStrict, outer) {
    override fun hasBinding(name: String): Boolean {
        if (!bindingObject.hasProperty(name))
            return false
        if (isWithEnvironment)
            return true

        val unscopables = bindingObject.get(Realm.`@@unscopables`)
        if (unscopables is JSObject)
            return unscopables.get(name).toBoolean()

        return true
    }

    override fun createMutableBinding(name: String, deletable: Boolean) {
        var attributes = Descriptor.ENUMERABLE or Descriptor.WRITABLE
        if (deletable)
            attributes = attributes or Descriptor.CONFIGURABLE

        Operations.definePropertyOrThrow(
            realm,
            bindingObject,
            PropertyKey.from(name),
            Descriptor(JSUndefined, attributes)
        )
    }

    override fun createImmutableBinding(name: String, strict: Boolean) {
        unreachable()
    }

    override fun initializeBinding(name: String, value: JSValue) {
        setMutableBinding(name, value, false)
    }

    override fun setMutableBinding(name: String, value: JSValue, strict: Boolean) {
        val stillExists = bindingObject.hasProperty(name)
        if (!stillExists && strict)
            Errors.TODO("ObjectEnvRecord::setMutableBinding").throwReferenceError(realm)

        Operations.set(realm, bindingObject, PropertyKey.from(name), value, strict)
    }

    override fun getBindingValue(name: String, strict: Boolean): JSValue {
        val value = bindingObject.hasProperty(name)
        if (!value) {
            if (strict)
                Errors.TODO("ObjectEnvRecord::getBindingValue").throwReferenceError(realm)
            return JSUndefined
        }
        return bindingObject.get(name)
    }

    override fun deleteBinding(name: String): Boolean {
        return bindingObject.delete(name)
    }

    override fun hasThisBinding() = false

    override fun hasSuperBinding() = false

    override fun withBaseObject() = if (isWithEnvironment) bindingObject else null
}
