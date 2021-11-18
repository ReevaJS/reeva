package com.reevajs.reeva.runtime.objects

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.functions.JSBuiltinFunction
import com.reevajs.reeva.runtime.objects.index.IndexedProperties
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class JSObject protected constructor(realm: Realm, prototype: JSValue = JSNull) : JSValue() {
    private val slots = mutableMapOf<Any, Any?>()
    private val id = Agent.activeAgent.nextObjectId()

    internal val storage = mutableListOf<JSValue>()
    val indexedProperties = IndexedProperties()
    private var extensible: Boolean = true
    internal var shape: Shape

    var transitionsEnabled: Boolean = true

    init {
        expect(prototype is JSObject || prototype == JSNull)

        if (prototype == JSNull) {
            shape = Shape()
        } else {
            shape = realm.emptyShape
            ordinarySetPrototype(prototype)
        }
    }

    var isSealed = false
        internal set(value) {
            if (value)
                expect(!field)
            field = value
        }
    var isFrozen = false
        internal set(value) {
            if (value)
                expect(!field)
            field = value
        }

    open fun init() { }

    fun inspect(): Inspection = buildInspection {
        contents("Type: Object")
        child("Class: ${this@JSObject::class.simpleName}")
        child("ID: #$id")
        child("Shape: #${shape.id}")
        child("Prototype:") {
            child(inspect(getPrototype(), simple = true))
        }

        // No need to print the property table of intrinsics, since it'll mostly be a
        // ton of builtin methods. A possible TODO would be to detect changes in these
        // objects from their base state and somehow only print those.
        if (this@JSObject::class === JSObject::class) {
            child("Properties:") {
                val propertyTable = shape.orderedPropertyTable()

                for (property in propertyTable) {
                    child("${property.name} (attrs=${property.attributes} offset=${property.offset})") {
                        child(inspect(storage[property.offset], simple = true))
                    }
                }
            }
        }

        if (slots.isNotEmpty()) {
            child("Slots:") {
                for ((key, value) in slots) {
                    child(key.toString()) {
                        if (value is JSValue) {
                            child(inspect(value, simple = true))
                        } else child(value.toString())
                    }
                }
            }
        }
    }

    @ECMAImpl("9.1.1")
    open fun getPrototype() = shape.prototype ?: JSNull

    @ECMAImpl("9.1.2")
    open fun setPrototype(newPrototype: JSValue): Boolean {
        return ordinarySetPrototype(newPrototype)
    }

    @ECMAImpl("9.1.2.1")
    fun ordinarySetPrototype(newPrototype: JSValue): Boolean {
        ecmaAssert(newPrototype is JSObject || newPrototype == JSNull)
        if (newPrototype.sameValue(shape.prototype ?: JSNull))
            return true

        if (!extensible)
            return false

        if (shape.isUnique) {
            shape.setPrototypeWithoutTransition(newPrototype as? JSObject)
            return true
        }

        shape = shape.makePrototypeTransition(newPrototype as? JSObject)
        return true
    }

    fun hasProperty(property: String): Boolean = hasProperty(property.key())
    fun hasProperty(property: JSSymbol) = hasProperty(property.key())
    fun hasProperty(property: Int) = hasProperty(property.key())
    fun hasProperty(property: Long) = hasProperty(property.key())

    @ECMAImpl("9.1.7")
    open fun hasProperty(property: PropertyKey): Boolean {
        if (getOwnPropertyDescriptor(property) != null)
            return true
        val parent = getPrototype()
        if (parent == JSNull)
            return false
        return (parent as JSObject).hasProperty(property)
    }

    @ECMAImpl("9.1.3")
    open fun isExtensible() = extensible

    @ECMAImpl("9.1.4")
    open fun preventExtensions(): Boolean {
        extensible = false
        return true
    }

    fun getOwnPropertyDescriptor(property: String) = getOwnPropertyDescriptor(property.key())
    fun getOwnPropertyDescriptor(property: JSSymbol) = getOwnPropertyDescriptor(property.key())
    fun getOwnPropertyDescriptor(property: Int) = getOwnPropertyDescriptor(property.key())
    fun getOwnPropertyDescriptor(property: Long) = getOwnPropertyDescriptor(property.toString().key())

    open fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        return when {
            property.isInt -> indexedProperties.getDescriptor(property.asInt)
            property.isLong -> indexedProperties.getDescriptor(property.asLong)
            else -> {
                val data = shape[property.toStringOrSymbol()] ?: return null
                Descriptor(storage[data.offset], data.attributes)
            }
        }
    }

    fun getOwnProperty(property: String) = getOwnProperty(property.key())
    fun getOwnProperty(property: JSSymbol) = getOwnProperty(property.key())
    fun getOwnProperty(property: Int) = getOwnProperty(property.key())
    fun getOwnProperty(property: Long) = getOwnProperty(property.toString().key())

    @ECMAImpl("9.1.5")
    fun getOwnProperty(property: PropertyKey): JSValue {
        return getOwnPropertyDescriptor(property)?.toObject(this) ?: JSUndefined
    }

    @JvmOverloads
    fun defineOwnProperty(
        property: String,
        value: JSValue,
        attributes: Int = Descriptor.DEFAULT_ATTRIBUTES
    ) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JvmOverloads
    fun defineOwnProperty(
        property: JSSymbol,
        value: JSValue,
        attributes: Int = Descriptor.DEFAULT_ATTRIBUTES
    ) = defineOwnProperty(property.key(), Descriptor(value, attributes))

    @JvmOverloads
    fun defineOwnProperty(property: Int, value: JSValue, attributes: Int = Descriptor.DEFAULT_ATTRIBUTES) =
        defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JvmOverloads
    fun defineOwnProperty(property: Long, value: JSValue, attributes: Int = Descriptor.DEFAULT_ATTRIBUTES) =
        defineOwnProperty(property.toString().key(), Descriptor(value, attributes))

    @ECMAImpl("9.1.6")
    open fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        return Operations.validateAndApplyPropertyDescriptor(
            this,
            property,
            isExtensible(),
            descriptor,
            getOwnPropertyDescriptor(property),
        )
    }

    @JvmOverloads fun get(property: String, receiver: JSValue = this) = get(property.key(), receiver)
    @JvmOverloads fun get(property: JSSymbol, receiver: JSValue = this) = get(property.key(), receiver)
    @JvmOverloads
    fun get(property: Number, receiver: JSValue = this) = when (property) {
        is Int -> get(property.key(), receiver)
        is Long -> get(property.key(), receiver)
        else -> throw IllegalArgumentException()
    }

    @JvmOverloads @ECMAImpl("9.1.8")
    open fun get(property: PropertyKey, receiver: JSValue = this): JSValue {
        val desc = getOwnPropertyDescriptor(property) ?: run {
            val parent = getPrototype()
            if (parent == JSNull)
                return JSUndefined
            return (parent as JSObject).get(property, receiver)
        }

        return when {
            desc.isDataDescriptor -> desc.getActualValue(receiver)
            desc.hasGetterFunction -> Operations.call(desc.getter!!, receiver)
            else -> JSUndefined
        }
    }

    @JvmOverloads fun set(property: String, value: JSValue, receiver: JSValue = this) =
        set(property.key(), value, receiver)
    @JvmOverloads fun set(property: JSSymbol, value: JSValue, receiver: JSValue = this) =
        set(property.key(), value, receiver)

    @JvmOverloads
    fun set(property: Number, value: JSValue, receiver: JSValue = this) = when (property) {
        is Int -> set(property.key(), value, receiver)
        is Long -> set(property.key(), value, receiver)
        else -> throw IllegalArgumentException()
    }

    @JvmOverloads @ECMAImpl("9.1.9")
    open fun set(property: PropertyKey, value: JSValue, receiver: JSValue = this): Boolean {
        val ownDesc = getOwnPropertyDescriptor(property)
        return ordinarySetWithOwnDescriptor(property, value, receiver, ownDesc)
    }

    @ECMAImpl("9.1.9.2")
    private fun ordinarySetWithOwnDescriptor(
        property: PropertyKey,
        value: JSValue,
        receiver: JSValue,
        ownDesc_: Descriptor?
    ): Boolean {
        var ownDesc = ownDesc_
        if (ownDesc == null) {
            val parent = getPrototype()
            if (parent != JSNull)
                return (parent as JSObject).set(property, value, receiver)
            ownDesc = Descriptor(JSUndefined, Descriptor.DEFAULT_ATTRIBUTES)
        }
        if (ownDesc.isDataDescriptor) {
            if (!ownDesc.isWritable)
                return false
            if (receiver !is JSObject)
                return false
            val existingDescriptor = receiver.getOwnPropertyDescriptor(property)
            if (existingDescriptor != null) {
                if (existingDescriptor.isAccessorDescriptor)
                    return false
                if (!existingDescriptor.isWritable)
                    return false
                val valueDesc = Descriptor(value, 0)
                return receiver.defineOwnProperty(property, valueDesc)
            }
            return receiver.defineOwnProperty(property, Descriptor(value, Descriptor.DEFAULT_ATTRIBUTES))
        }
        expect(ownDesc.isAccessorDescriptor)
        if (!ownDesc.hasSetterFunction)
            return false
        Operations.call(ownDesc.setter!!, receiver, listOf(value))
        return true
    }

    fun delete(property: String) = delete(property.key())
    fun delete(property: JSSymbol) = delete(property.key())
    fun delete(property: Number) = when (property) {
        is Int -> delete(property.key())
        is Long -> delete(property.key())
        else -> throw IllegalArgumentException()
    }

    @ECMAImpl("9.1.10")
    open fun delete(property: PropertyKey): Boolean {
        val desc = getOwnPropertyDescriptor(property) ?: return true
        if (!desc.isConfigurable)
            return false

        return when {
            property.isInt -> indexedProperties.remove(property.asInt)
            property.isLong -> indexedProperties.remove(property.asLong)
            else -> {
                val stringOrSymbol = property.toStringOrSymbol()
                val data = shape[stringOrSymbol] ?: return true
                if (!shape.isUnique)
                    shape = shape.makeUniqueClone()

                shape.removeUniqueShapeProperty(stringOrSymbol, data.offset)
                storage.removeAt(data.offset)
                true
            }
        }
    }

    @ECMAImpl("9.1.11")
    open fun ownPropertyKeys(onlyEnumerable: Boolean = false): List<PropertyKey> {
        return indexedProperties.indices().map(PropertyKey::from) + shape.orderedPropertyTable().filter {
            if (onlyEnumerable) (it.attributes and Descriptor.ENUMERABLE) != 0 else true
        }.map { PropertyKey.from(it.name) }
    }

    fun defineNativeProperty(
        key: String,
        attributes: Int,
        getter: NativeGetterSignature?,
        setter: NativeSetterSignature?
    ) {
        defineNativeProperty(key.key(), attributes, getter, setter)
    }

    fun defineNativeProperty(
        key: PropertyKey,
        attributes: Int,
        getter: NativeGetterSignature?,
        setter: NativeSetterSignature?
    ) {
        val value = JSNativeProperty(getter, setter)
        addProperty(key, Descriptor(value, attributes))
    }

    fun defineBuiltinGetter(
        name: String,
        builtin: Builtin,
        attributes: Int = Descriptor.DEFAULT_ATTRIBUTES,
    ) {
        defineBuiltinAccessor(name.key(), name, attributes, builtin, isGetter = true)
    }

    fun defineBuiltinGetter(
        name: JSSymbol,
        builtin: Builtin,
        attributes: Int = Descriptor.DEFAULT_ATTRIBUTES,
    ) {
        defineBuiltinAccessor(name.key(), "get [${name.description}]", attributes, builtin, isGetter = true)
    }

    fun defineBuiltinSetter(
        name: String,
        builtin: Builtin,
        attributes: Int = Descriptor.DEFAULT_ATTRIBUTES,
    ) {
        defineBuiltinAccessor(name.key(), name, attributes, builtin, isGetter = false)
    }

    fun defineBuiltinSetter(
        name: JSSymbol,
        builtin: Builtin,
        attributes: Int = Descriptor.DEFAULT_ATTRIBUTES,
    ) {
        defineBuiltinAccessor(name.key(), "set [${name.description}]", attributes, builtin, isGetter = false)
    }

    private fun defineBuiltinAccessor(
        key: PropertyKey,
        jsName: String,
        attributes: Int,
        builtin: Builtin,
        isGetter: Boolean,
    ) {
        val length = if (isGetter) 0 else 1
        val function = JSBuiltinFunction.create(jsName, length, builtin)

        val existingProperty = internalGet(key)
        if (existingProperty != null) {
            val value = existingProperty.getRawValue()
            expect(value is JSAccessor) {
                "Cannot define accessor for property $key which is already defined and is not an accessor"
            }

            if (isGetter) {
                expect(value.getter == null) {
                    "Cannot redefine getter for property $key which already has a getter defined"
                }
                value.getter = function
            } else {
                expect(value.setter == null) {
                    "Cannot redefine setter for property $key which already has a setter defined"
                }
                value.setter = function
            }

            return
        }

        val accessor = if (isGetter) {
            JSAccessor(function, null)
        } else JSAccessor(null, function)

        addProperty(key, Descriptor(accessor, attributes))
    }

    fun defineBuiltin(
        name: String,
        length: Int,
        builtin: Builtin,
        attributes: Int = attrs { +conf; -enum; +writ },
    ) {
        defineBuiltin(name.key(), name, length, builtin, attributes)
    }

    fun defineBuiltin(
        name: JSSymbol,
        length: Int,
        builtin: Builtin,
        attributes: Int = attrs { +conf; -enum; +writ },
    ) {
        defineBuiltin(name.key(), "[${name.description}]", length, builtin, attributes)
    }

    private fun defineBuiltin(
        key: PropertyKey,
        jsName: String,
        length: Int,
        builtin: Builtin,
        attributes: Int = attrs { +conf; -enum; +writ },
    ) {
        val function = JSBuiltinFunction.create(jsName, length, builtin)
        addProperty(key, Descriptor(function, attributes))
    }

    fun addSlot(name: Any, value: JSValue = JSUndefined) {
        slots[name] = value
    }

    fun hasSlot(name: Any) = name in slots

    fun hasSlots(vararg names: Any) = names.all(::hasSlot)

    fun getSlot(name: Any) = slots[name]

    inline fun <reified T> getSlotAs(name: Any) = getSlot(name) as T

    fun setSlot(name: Any, value: Any?) {
        slots[name] = value
    }

    internal fun internalGet(property: PropertyKey): Descriptor? {
        val stringOrSymbol = when {
            property.isInt -> return indexedProperties.getDescriptor(property.asInt)
            property.isLong -> return indexedProperties.getDescriptor(property.asLong)
            else -> property.toStringOrSymbol()
        }

        val data = shape[stringOrSymbol] ?: return null
        return Descriptor(storage[data.offset], data.attributes)
    }

    internal fun addProperty(property: PropertyKey, descriptor: Descriptor) {
        val stringOrSymbol = when {
            property.isInt -> return indexedProperties.setDescriptor(property.asInt, descriptor)
            property.isLong -> return indexedProperties.setDescriptor(property.asLong, descriptor)
            else -> property.toStringOrSymbol()
        }

        addProperty(stringOrSymbol, descriptor)
    }

    internal fun addProperty(key: StringOrSymbol, descriptor: Descriptor) {
        if (!transitionsEnabled && !shape.isUnique) {
            shape.addPropertyWithoutTransition(key, descriptor.attributes)
            ensureStorageCapacity(shape.propertyCount)
            storage[shape.propertyCount - 1] = descriptor.getRawValue()
            return
        }

        var data = shape[key] ?: run {
            if (!shape.isUnique && shape.propertyCount > Shape.PROPERTY_COUNT_TRANSITION_LIMIT)
                shape = shape.makeUniqueClone()

            when {
                shape.isUnique -> shape.addUniqueShapeProperty(key, descriptor.attributes)
                transitionsEnabled -> shape = shape.makePutTransition(key, descriptor.attributes)
                else -> shape.addPropertyWithoutTransition(key, descriptor.attributes)
            }
            ensureStorageCapacity(shape.propertyCount)

            shape[key]!!
        }

        if (descriptor.attributes != data.attributes) {
            if (shape.isUnique) {
                shape.reconfigureUniqueShapeProperty(key, descriptor.attributes)
            } else {
                shape = shape.makeConfigureTransition(key, descriptor.attributes)
            }
            data = shape[key]!!
        }

        storage[data.offset] = descriptor.getRawValue()
    }

    private fun ensureStorageCapacity(capacity: Int) {
        repeat(capacity - storage.size) {
            storage.add(JSEmpty)
        }
    }

    enum class PropertyKind {
        Key,
        Value,
        KeyValue
    }

    class StringOrSymbol private constructor(private val value: Any) {
        val isString = value is String
        val isSymbol = value is JSSymbol

        val asString: String
            get() = value as String
        val asSymbol: JSSymbol
            get() = value as JSSymbol

        val asValue: JSValue
            get() = if (isString) JSString(asString) else asSymbol

        constructor(value: String) : this(value as Any)
        constructor(value: JSSymbol) : this(value as Any)

        override fun toString(): String {
            return if (isString) asString else asSymbol.toString()
        }

        override fun equals(other: Any?): Boolean {
            return other is StringOrSymbol && value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    protected fun <T> slot(name: Any, initialValue: T) = SlotDelegator(name, initialValue)

    protected fun <T> lateinitSlot(name: Any) = LateInitSlotDelegator<T>(name)

    protected inner class SlotDelegator<T>(val name: Any, initialValue: T) : ReadWriteProperty<JSObject, T> {
        init {
            slots[name] = initialValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: JSObject, property: KProperty<*>): T {
            return slots[name] as T
        }

        override fun setValue(thisRef: JSObject, property: KProperty<*>, value: T) {
            slots[name] = value
        }
    }

    protected inner class LateInitSlotDelegator<T>(val name: Any) : ReadWriteProperty<JSObject, T> {
        init {
            slots[name] = null
        }

        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: JSObject, property: KProperty<*>): T {
            return slots[name] as T
        }

        override fun setValue(thisRef: JSObject, property: KProperty<*>, value: T) {
            slots[name] = value
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(
            realm: Realm = Agent.activeAgent.getActiveRealm(),
            proto: JSValue = realm.objectProto,
        ) = JSObject(realm, proto).initialize()

        fun <T : JSObject> T.initialize() = apply {
            init()
        }
    }
}
