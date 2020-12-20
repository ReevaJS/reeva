package me.mattco.reeva.runtime.objects

import me.mattco.reeva.core.Agent
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.*
import me.mattco.reeva.core.ExecutionContext
import me.mattco.reeva.core.environment.FunctionEnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.functions.JSFunction
import me.mattco.reeva.runtime.functions.JSNativeFunction
import me.mattco.reeva.runtime.primitives.*
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.objects.index.IndexedProperties
import me.mattco.reeva.runtime.objects.index.IndexedStorage
import me.mattco.reeva.utils.*

open class JSObject protected constructor(
    val realm: Realm,
    prototype: JSValue = JSNull,
) : JSValue() {
    private val storage = mutableListOf<JSValue>()
    internal val indexedProperties = IndexedProperties()
    private var extensible: Boolean = true
    private var shape: Shape

    var transitionsEnabled: Boolean = true

    init {
        expect(prototype is JSObject || prototype == JSNull)

        if (prototype == JSNull) {
            shape = Shape(realm)
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

    data class NativeMethodPair(
        var attributes: Int,
        var getter: NativeGetterSignature? = null,
        var setter: NativeSetterSignature? = null,
    )

    data class NativeAccessorPair(
        var attributes: Int,
        var getter: JSFunction? = null,
        var setter: JSFunction? = null,
    )

    open fun init() { }

    @ECMAImpl("9.1.1")
    open fun getPrototype() = shape.prototype ?: JSNull

    @ECMAImpl("9.1.2")
    open fun setPrototype(newPrototype: JSValue): Boolean {
        return ordinarySetPrototype(newPrototype)
    }

    @ECMAImpl("9.1.2.1")
    open fun ordinarySetPrototype(newPrototype: JSValue): Boolean {
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
        val hasOwn = getOwnPropertyDescriptor(property)
        if (hasOwn != null)
            return true
        val parent = getPrototype()
        if (parent != JSNull)
            return (parent as JSObject).hasProperty(property)
        return false
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
        return internalGet(property)
    }

    fun getOwnProperty(property: String) = getOwnProperty(property.key())
    fun getOwnProperty(property: JSSymbol) = getOwnProperty(property.key())
    fun getOwnProperty(property: Int) = getOwnProperty(property.key())
    fun getOwnProperty(property: Long) = getOwnProperty(property.toString().key())

    @ECMAImpl("9.1.5")
    fun getOwnProperty(property: PropertyKey): JSValue {
        return getOwnPropertyDescriptor(property)?.toObject(realm, this) ?: JSUndefined
    }

    @JvmOverloads fun defineOwnProperty(property: String, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JvmOverloads fun defineOwnProperty(property: JSSymbol, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JvmOverloads fun defineOwnProperty(property: Int, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JvmOverloads fun defineOwnProperty(property: Long, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.toString().key(), Descriptor(value, attributes))

    @ECMAImpl("9.1.6")
    open fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        return Operations.validateAndApplyPropertyDescriptor(this, property, isExtensible(), descriptor, getOwnPropertyDescriptor(property))
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
        val desc = getOwnPropertyDescriptor(property)
        if (desc == null) {
            val parent = getPrototype()
            if (parent == JSNull)
                return JSUndefined
            return (parent as JSObject).get(property, receiver)
        }
        if (desc.isAccessorDescriptor)
            return if (desc.hasGetterFunction) Operations.call(desc.getter!!, receiver) else JSUndefined
        return desc.getActualValue(receiver)
    }

    @JvmOverloads fun set(property: String, value: JSValue, receiver: JSValue = this) = set(property.key(), value, receiver)
    @JvmOverloads fun set(property: JSSymbol, value: JSValue, receiver: JSValue = this) = set(property.key(), value, receiver)
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
    private fun ordinarySetWithOwnDescriptor(property: PropertyKey, value: JSValue, receiver: JSValue, ownDesc_: Descriptor?): Boolean {
        var ownDesc = ownDesc_
        if (ownDesc == null) {
            val parent = getPrototype()
            if (parent != JSNull)
                return (parent as JSObject).set(property, value, receiver)
            ownDesc = Descriptor(JSUndefined, Descriptor.defaultAttributes)
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
            return receiver.defineOwnProperty(property, Descriptor(value, Descriptor.defaultAttributes))
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
        if (desc.isConfigurable)
            return internalDelete(property)
        return false
    }

    @ECMAImpl("9.1.11")
    open fun ownPropertyKeys(onlyEnumerable: Boolean = false): List<PropertyKey> {
        return indexedProperties.indices().map(::PropertyKey) + shape.orderedPropertyTable().filter {
            if (onlyEnumerable) (it.attributes and Descriptor.ENUMERABLE) != 0 else true
        }.map { PropertyKey(it.name) }
    }

    fun defineNativeAccessor(
        key: String,
        attributes: Int,
        getter: NativeGetterSignature? = null,
        setter: NativeSetterSignature? = null,
        name: String = ""
    ) {
        defineNativeAccessor(key.key(), attributes, getter, setter, name)
    }

    fun defineNativeAccessor(
        key: PropertyKey,
        attributes: Int,
        getter: NativeGetterSignature? = null,
        setter: NativeSetterSignature? = null,
        name: String = ""
    ) {
        val getterFunc = getter?.let { f ->
            JSNativeFunction.fromLambda(realm, name, 0) { thisValue, _ -> f(thisValue) }
        }
        val setterFunc = setter?.let { f ->
            JSNativeFunction.fromLambda(realm, name, 0) { thisValue, args ->
                if (args.size != 1)
                    TODO()
                f(thisValue, args[0])
                JSUndefined
            }
        }

        val value = JSAccessor(getterFunc, setterFunc)
        internalSet(key, Descriptor(value, attributes))
    }

    fun defineNativeProperty(key: String, attributes: Int, getter: NativeGetterSignature?, setter: NativeSetterSignature?) {
        defineNativeProperty(key.key(), attributes, getter, setter)
    }

    fun defineNativeProperty(key: PropertyKey, attributes: Int, getter: NativeGetterSignature?, setter: NativeSetterSignature?) {
        val value = JSNativeProperty(getter, setter)
        internalSet(key, Descriptor(value, attributes))
    }

    fun defineNativeFunction(
        key: String,
        length: Int,
        function: NativeFunctionSignature
    ) {
        defineNativeFunction(key.key(), length, attrs { +conf -enum +writ }, key, function)
    }

    fun defineNativeFunction(
        key: String,
        length: Int,
        attributes: Int = attrs { +conf -enum +writ },
        function: NativeFunctionSignature
    ) {
        defineNativeFunction(key.key(), length, attributes, key, function)
    }

    fun defineNativeFunction(
        key: PropertyKey,
        length: Int,
        attributes: Int = attrs { +conf -enum +writ },
        name: String = if (key.isString) key.asString else "[${key.asSymbol.descriptiveString()}]",
        function: NativeFunctionSignature
    ) {
        val obj = JSNativeFunction.fromLambda(realm, name, length, function)
        internalSet(key, Descriptor(obj, attributes))
    }

    internal fun internalGet(property: PropertyKey): Descriptor? {
        val stringOrSymbol = when {
            property.isString -> {
                property.asString.toLongOrNull()?.also {
                    if (it in 0L..IndexedStorage.INDEX_UPPER_BOUND)
                        return indexedProperties.getDescriptor(it)
                }
                StringOrSymbol(property.asString)
            }
            property.isInt -> {
                if (property.asInt >= 0)
                    return indexedProperties.getDescriptor(property.asInt)
                StringOrSymbol(property.asInt.toString())
            }
            property.isLong -> {
                if (property.asLong in 0L..IndexedStorage.INDEX_UPPER_BOUND)
                    return indexedProperties.getDescriptor(property.asLong)
                StringOrSymbol(property.asLong.toString())
            }
            property.isDouble -> StringOrSymbol(property.asDouble.toString())
            property.isSymbol -> StringOrSymbol(property.asSymbol)
            else -> unreachable()
        }

        val data = shape[stringOrSymbol] ?: return null
        return Descriptor(storage[data.offset], data.attributes)
    }

    internal fun internalSet(property: PropertyKey, descriptor: Descriptor) {
        val stringOrSymbol = when {
            property.isString -> {
                property.asString.toLongOrNull()?.also {
                    if (it in 0L..IndexedStorage.INDEX_UPPER_BOUND) {
                        indexedProperties.setDescriptor(it, descriptor)
                        return
                    }
                }
                StringOrSymbol(property.asString)
            }
            property.isInt -> {
                if (property.asInt >= 0)
                    return indexedProperties.setDescriptor(property.asInt, descriptor)
                StringOrSymbol(property.asInt.toString())
            }
            property.isLong -> {
                if (property.asLong in 0L..IndexedStorage.INDEX_UPPER_BOUND)
                    return indexedProperties.setDescriptor(property.asLong, descriptor)
                StringOrSymbol(property.asLong.toString())
            }
            property.isDouble -> StringOrSymbol(property.asDouble.toString())
            property.isSymbol -> StringOrSymbol(property.asSymbol)
            else -> unreachable()
        }

        internalSet(stringOrSymbol, descriptor)
    }

    internal fun internalSet(key: StringOrSymbol, descriptor: Descriptor) {
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

        when (val existingValue = storage[data.offset]) {
            is JSAccessor -> existingValue.callSetter(this, descriptor.getActualValue(this))
            is JSNativeProperty -> existingValue.set(this, descriptor.getActualValue(this))
            else -> storage[data.offset] = descriptor.getRawValue()
        }
    }

    internal fun internalDelete(property: PropertyKey): Boolean {
        val stringOrSymbol = when {
            property.isString -> {
                property.asString.toLongOrNull()?.also {
                    if (it in 0L..IndexedStorage.INDEX_UPPER_BOUND)
                        return indexedProperties.remove(it)
                }
                StringOrSymbol(property.asString)
            }
            property.isInt -> {
                if (property.asInt >= 0)
                    return indexedProperties.remove(property.asInt)
                StringOrSymbol(property.asInt.toString())
            }
            property.isLong -> {
                if (property.asLong in 0L..IndexedStorage.INDEX_UPPER_BOUND)
                    return indexedProperties.remove(property.asLong)
                StringOrSymbol(property.asLong.toString())
            }
            property.isDouble -> StringOrSymbol(property.asDouble.toString())
            property.isSymbol -> StringOrSymbol(property.asSymbol)
            else -> unreachable()
        }

        val data = shape[stringOrSymbol] ?: return true
        if (!shape.isUnique)
            shape = shape.makeUniqueClone()

        shape.removeUniqueShapeProperty(stringOrSymbol, data.offset)
        storage.removeAt(data.offset)
        return true
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

    data class StringOrSymbol private constructor(private val value: Any) {
        val isString = value is String
        val isSymbol = value is JSSymbol

        val asString: String
            get() = value as String
        val asSymbol: JSSymbol
            get() = value as JSSymbol

        val asValue: JSValue
            get() = if (isString) JSString(asString) else asSymbol

        constructor(value: String) : this(value as Any)
        constructor(value: JSString) : this(value.string)
        constructor(value: JSSymbol) : this(value as Any)

        constructor(key: PropertyKey) : this(when {
            key.isInt -> key.asInt.toString()
            key.isDouble -> key.asDouble.toString()
            key.isString -> key.asString
            else -> key.asSymbol
        })

        override fun toString(): String {
            if (isString)
                return asString
            return asSymbol.toString()
        }

        companion object {
            val INVALID_KEY = StringOrSymbol(0)
        }
    }

    companion object {
        val INVALID_OBJECT by lazy { JSObject(Agent.runningContext.realm) }

        @JvmStatic
        @JvmOverloads
        fun create(realm: Realm, proto: JSValue = realm.objectProto) = JSObject(realm, proto).initialize()

        fun <T : JSObject> T.initialize() = apply {
            init()
        }
    }
}
