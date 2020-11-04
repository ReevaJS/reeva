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
import me.mattco.reeva.utils.*

open class JSObject protected constructor(
    val realm: Realm,
    private var prototype: JSValue = JSNull
) : JSValue() {
    private val storage = mutableMapOf<StringOrSymbol, Descriptor>()
    internal val indexedProperties = IndexedProperties()
    private var extensible: Boolean = true

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

    init {
        if (prototype !is JSObject && prototype !is JSNull)
            throw IllegalArgumentException("Invalid prototype provided to JSObject constructor")
    }

    // To facilitate classes which must set their prototypes in the init()
    // call instead of the class constructor
    protected fun internalSetPrototype(prototype: JSValue) {
        if (prototype !is JSObject && prototype !is JSNull)
            throw IllegalArgumentException("Invalid prototype provided to internalSetPrototype")
        this.prototype = prototype
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

    open fun init() {
        configureInstanceProperties()
    }

    // This method exists to be called directly by subclass who cannot call their
    // super.init() method due to prototype complications
    protected fun configureInstanceProperties() {
        // TODO: This is probably terrible for performance, but very cool :)
        // A better way to do it would be to use an annotation processor, and bake
        // these properties into the class's "init" method as direct calls to the
        // appropriate "defineXYZ" method intead of having to do all this reflection
        // every single time a property is instantiated

        val nativeProperties = mutableMapOf<PropertyKey, NativeMethodPair>()

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSNativePropertyGetter::class.java)
        }.forEach { method ->
            val getter = method.getAnnotation(JSNativePropertyGetter::class.java)
            val methodPair = NativeMethodPair(attributes = getter.attributes, getter = { thisValue ->
                method.invoke(this, thisValue) as JSValue
            })
            val key = if (getter.name.startsWith("@@")) {
                Realm.wellknownSymbols[getter.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${getter.name}")
            } else PropertyKey(getter.name)
            expect(key !in nativeProperties)
            nativeProperties[key] = methodPair
        }

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSNativePropertySetter::class.java)
        }.forEach { method ->
            val setter = method.getAnnotation(JSNativePropertySetter::class.java)
            val key = if (setter.name.startsWith("@@")) {
                Realm.wellknownSymbols[setter.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${setter.name}")
            } else PropertyKey(setter.name)
            val methodPair = if (key in nativeProperties) {
                nativeProperties[key]!!.also {
                    expect(it.attributes == setter.attributes)
                }
            } else {
                val t = NativeMethodPair(setter.attributes)
                nativeProperties[key] = t
                t
            }
            methodPair.setter = { thisValue, value -> method.invoke(this, thisValue, value) }
        }

        nativeProperties.forEach { (name, methods) ->
            defineNativeProperty(name, methods.attributes, methods.getter, methods.setter)
        }

        val nativeAccessors = mutableMapOf<PropertyKey, NativeAccessorPair>()

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSNativeAccessorGetter::class.java)
        }.forEach { method ->
            val getter = method.getAnnotation(JSNativeAccessorGetter::class.java)
            val methodPair = NativeAccessorPair(
                attributes = getter.attributes,
                JSNativeFunction.fromLambda(realm, "TODO", 0) { thisValue, _ ->
                    method.invoke(this, thisValue) as JSValue
                }
            )
            val key = if (getter.name.startsWith("@@")) {
                Realm.wellknownSymbols[getter.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${getter.name}")
            } else PropertyKey(getter.name)
            expect(key !in nativeAccessors)
            nativeAccessors[key] = methodPair
        }

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSNativeAccessorSetter::class.java)
        }.forEach { method ->
            val setter = method.getAnnotation(JSNativeAccessorSetter::class.java)
            val key = if (setter.name.startsWith("@@")) {
                Realm.wellknownSymbols[setter.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${setter.name}")
            } else PropertyKey(setter.name)
            val methodPair = if (key in nativeAccessors) {
                nativeAccessors[key]!!.also {
                    expect(it.attributes == setter.attributes)
                }
            } else {
                val t = NativeAccessorPair(setter.attributes)
                nativeAccessors[key] = t
                t
            }
            methodPair.setter = JSNativeFunction.fromLambda(realm, "TODO", 1) { thisValue, arguments ->
                method.invoke(this, thisValue, arguments.argument(0)) as JSValue
            }
        }

        nativeAccessors.forEach { (name, methods) ->
            defineNativeAccessor(name, methods.attributes, methods.getter, methods.setter)
        }

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSMethod::class.java)
        }.forEach {
            val annotation = it.getAnnotation(JSMethod::class.java)
            val key = if (annotation.name.startsWith("@@")) {
                Realm.wellknownSymbols[annotation.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${annotation.name}")
            } else PropertyKey(annotation.name)

            defineNativeFunction(
                key,
                annotation.length,
                annotation.attributes
            ) { thisValue, arguments ->
                it.invoke(this, thisValue, arguments) as JSValue
            }
        }
    }

    @JSThrows
    @ECMAImpl("9.1.1")
    open fun getPrototype() = prototype

    @JSThrows
    @ECMAImpl("9.1.2")
    open fun setPrototype(newPrototype: JSValue): Boolean {
        ecmaAssert(newPrototype.isObject || newPrototype.isNull)
        if (newPrototype.sameValue(prototype))
            return true

        if (!extensible)
            return false

        var p = newPrototype
        while (true) {
            if (p.isNull)
                break
            if (p.sameValue(this))
                return false
            // TODO: Handle 9.1.2.1.8.c.i?
            p = (p as JSObject).getPrototype()
        }

        prototype = p
        return true
    }

    @JSThrows fun hasProperty(property: String): Boolean = hasProperty(property.key())
    @JSThrows fun hasProperty(property: JSSymbol) = hasProperty(property.key())
    @JSThrows fun hasProperty(property: Int) = hasProperty(property.key())
    @JSThrows fun hasProperty(property: Long) = hasProperty(property.toString().key())

    @JSThrows
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

    @JSThrows
    @ECMAImpl("9.1.3")
    open fun isExtensible() = extensible

    @JSThrows
    @ECMAImpl("9.1.4")
    open fun preventExtensions(): Boolean {
        extensible = false
        return true
    }

    @JSThrows fun getOwnPropertyDescriptor(property: String) = getOwnPropertyDescriptor(property.key())
    @JSThrows fun getOwnPropertyDescriptor(property: JSSymbol) = getOwnPropertyDescriptor(property.key())
    @JSThrows fun getOwnPropertyDescriptor(property: Int) = getOwnPropertyDescriptor(property.key())
    @JSThrows fun getOwnPropertyDescriptor(property: Long) = getOwnPropertyDescriptor(property.toString().key())

    @JSThrows
    open fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        return internalGet(property)
    }

    @JSThrows fun getOwnProperty(property: String) = getOwnProperty(property.key())
    @JSThrows fun getOwnProperty(property: JSSymbol) = getOwnProperty(property.key())
    @JSThrows fun getOwnProperty(property: Int) = getOwnProperty(property.key())
    @JSThrows fun getOwnProperty(property: Long) = getOwnProperty(property.toString().key())

    @JSThrows
    @ECMAImpl("9.1.5")
    fun getOwnProperty(property: PropertyKey): JSValue {
        return getOwnPropertyDescriptor(property)?.toObject(realm, this) ?: JSUndefined
    }

    @JSThrows fun defineOwnProperty(property: String, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JSThrows fun defineOwnProperty(property: JSSymbol, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JSThrows fun defineOwnProperty(property: Int, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.key(), Descriptor(value, attributes))
    @JSThrows fun defineOwnProperty(property: Long, value: JSValue, attributes: Int = Descriptor.defaultAttributes) = defineOwnProperty(property.toString().key(), Descriptor(value, attributes))

    @JSThrows
    @ECMAImpl("9.1.6")
    open fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        return Operations.validateAndApplyPropertyDescriptor(this, property, isExtensible(), descriptor, getOwnPropertyDescriptor(property))
    }

    @JSThrows fun get(property: String, receiver: JSValue = this) = get(property.key(), receiver)
    @JSThrows fun get(property: JSSymbol, receiver: JSValue = this) = get(property.key(), receiver)
    @JSThrows fun get(property: Int, receiver: JSValue = this) = get(property.key(), receiver)
    @JSThrows fun get(property: Long, receiver: JSValue = this) = get(property.toString().key(), receiver)

    @JSThrows
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
            return if (desc.hasGetter) Operations.call(desc.getter, this) else JSUndefined
        return desc.getActualValue(receiver)
    }

    @JSThrows fun set(property: String, value: JSValue, receiver: JSValue = this) = set(property.key(), value, receiver)
    @JSThrows fun set(property: JSSymbol, value: JSValue, receiver: JSValue = this) = set(property.key(), value, receiver)
    @JSThrows fun set(property: Int, value: JSValue, receiver: JSValue = this) = set(property.key(), value, receiver)
    @JSThrows fun set(property: Long, value: JSValue, receiver: JSValue = this) = set(property.toString().key(), value, receiver)

    @JSThrows
    @JvmOverloads @ECMAImpl("9.1.9")
    open fun set(property: PropertyKey, value: JSValue, receiver: JSValue = this): Boolean {
        val ownDesc = getOwnPropertyDescriptor(property)
        return ordinarySetWithOwnDescriptor(property, value, receiver, ownDesc)
    }

    @JSThrows
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
        if (!ownDesc.hasSetter)
            return false
        val setter = ownDesc.setter
        Operations.call(setter, receiver, listOf(value))
        return true
    }

    @JSThrows fun delete(property: String) = delete(property.key())
    @JSThrows fun delete(property: JSSymbol) = delete(property.key())
    @JSThrows fun delete(property: Int) = delete(property.key())
    @JSThrows fun delete(property: Long) = delete(property.toString().key())

    @ECMAImpl("9.1.10")
    open fun delete(property: PropertyKey): Boolean {
        val desc = getOwnPropertyDescriptor(property) ?: return true
        if (desc.isConfigurable)
            return internalDelete(property)
        return false
    }

    @JSThrows
    @ECMAImpl("9.1.11")
    open fun ownPropertyKeys(): List<PropertyKey> {
        // TODO: Ordering is wrong here
        return indexedProperties.indices().map(::PropertyKey) + storage.keys.map {
            if (it.isString) PropertyKey(it.asString) else PropertyKey(it.asSymbol)
        }
    }

    fun defineNativeAccessor(key: PropertyKey, attributes: Int, getter: JSFunction?, setter: JSFunction?) {
        val value = JSAccessor(getter, setter)
        internalSet(key, Descriptor(value, attributes))
    }

    fun defineNativeProperty(key: PropertyKey, attributes: Int, getter: NativeGetterSignature?, setter: NativeSetterSignature?) {
        val value = JSNativeProperty(getter, setter)
        internalSet(key, Descriptor(value, attributes))
    }

    fun defineNativeFunction(key: PropertyKey, length: Int, attributes: Int, function: NativeFunctionSignature) {
        val name = if (key.isString) key.asString else "[${key.asSymbol.descriptiveString()}]"
        val obj = JSNativeFunction.fromLambda(realm, name, length, function)
        internalSet(key, Descriptor(obj, attributes))
    }

    @JSThrows
    internal fun internalGet(property: PropertyKey): Descriptor? {
        val stringOrSymbol = when {
            property.isString -> {
                property.asString.toIntOrNull()?.let {
                    if (it >= 0)
                        return indexedProperties.getDescriptor(it)
                }
                StringOrSymbol(property.asString)
            }
            property.isInt -> {
                if (property.asInt >= 0)
                    return indexedProperties.getDescriptor(property.asInt)
                StringOrSymbol(property.asInt.toString())
            }
            property.isDouble -> StringOrSymbol(property.asDouble.toString())
            property.isSymbol -> StringOrSymbol(property.asSymbol)
            else -> unreachable()
        }

        return storage[stringOrSymbol]
    }

    internal fun internalSet(property: PropertyKey, descriptor: Descriptor) {
        val stringOrSymbol = when {
            property.isString -> {
                property.asString.toIntOrNull()?.let {
                    if (it >= 0) {
                        indexedProperties.set(this, it, descriptor)
                        return
                    }
                }
                StringOrSymbol(property.asString)
            }
            property.isInt -> {
                if (property.asInt >= 0) {
                    indexedProperties.set(this, property.asInt, descriptor)
                    return
                }
                StringOrSymbol(property.asInt.toString())
            }
            property.isDouble -> StringOrSymbol(property.asDouble.toString())
            property.isSymbol -> StringOrSymbol(property.asSymbol)
            else -> unreachable()
        }

        storage[stringOrSymbol] = descriptor
    }

    internal fun internalDelete(property: PropertyKey): Boolean {
        val stringOrSymbol = when {
            property.isInt -> {
                if (property.asInt >= 0)
                    return indexedProperties.remove(property.asInt)
                StringOrSymbol(property.asInt.toString())
            }
            property.isDouble -> StringOrSymbol(property.asDouble.toString())
            property.isSymbol -> StringOrSymbol(property.asSymbol)
            else -> StringOrSymbol(property.asString)
        }

        storage.remove(stringOrSymbol)
        return true
    }

    enum class PropertyKind {
        Key,
        Value,
        KeyValue
    }

    data class StringOrSymbol private constructor(private val value: Any) {
        val isString = value is String
        val isSymbol = value is JSSymbol

        val asString by lazy { value as String }
        val asSymbol by lazy { value as JSSymbol }

        val asValue by lazy {
            if (isString) JSString(asString) else asSymbol
        }

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
        fun create(realm: Realm, proto: JSValue = realm.objectProto) = JSObject(realm, proto).also { it.init() }

        protected fun thisBinding(context: ExecutionContext): JSValue {
            val env = context.lexicalEnv ?: throwTypeError("TODO: message")
            if (!env.hasThisBinding())
                throwTypeError(("TODO: message"))
            if (env is FunctionEnvRecord)
                return env.getThisBinding()
            if (env is GlobalEnvRecord)
                return env.getThisBinding()
            unreachable()
        }
    }
}
