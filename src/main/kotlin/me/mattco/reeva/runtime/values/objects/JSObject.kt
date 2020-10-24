package me.mattco.reeva.runtime.values.objects

import me.mattco.reeva.runtime.Agent
import me.mattco.reeva.runtime.Agent.Companion.checkError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.annotations.*
import me.mattco.reeva.runtime.contexts.ExecutionContext
import me.mattco.reeva.runtime.environment.FunctionEnvRecord
import me.mattco.reeva.runtime.environment.GlobalEnvRecord
import me.mattco.reeva.runtime.values.JSValue
import me.mattco.reeva.runtime.values.functions.*
import me.mattco.reeva.runtime.values.objects.Attributes.Companion.HAS_WRITABLE
import me.mattco.reeva.runtime.values.objects.Attributes.Companion.WRITABLE
import me.mattco.reeva.runtime.values.primitives.*
import me.mattco.reeva.utils.ecmaAssert
import me.mattco.reeva.utils.expect
import me.mattco.reeva.utils.shouldThrowError
import me.mattco.reeva.utils.unreachable

open class JSObject protected constructor(
    val realm: Realm,
    prototype: JSValue? = null
) : JSValue() {
    private val properties = mutableMapOf<PropertyKey, Descriptor>()
    private var extensible: Boolean = true

    // This must be a lateinit var, because otherwise some objects could not be
    // instantiated without causing a circularity issue. For example, JSNumberProto
    // is itself a JSNumberObject. If the JSNumberObject tried to pass the number
    // proto to it's super constructor, it would cause problems.
    private lateinit var prototype: JSValue

    init {
        if (prototype != null)
            this.prototype = prototype
    }

    // To facilitate classes which must set their prototypes in the init()
    // call instead of the class constructor
    protected fun internalSetPrototype(prototype: JSValue) {
        this.prototype = prototype
    }

    data class NativeMethodPair(
        var attributes: Int,
        var getter: NativeGetterSignature? = null,
        var setter: NativeSetterSignature? = null,
    )

    open fun init() {
        defineOwnProperty("prototype", Descriptor(prototype, Attributes(0)))

        configureInstanceProperties()
    }

    // This method exists to be called directly by subclass who cannot call their
    // super.init() method due to prototype complications
    protected fun configureInstanceProperties(clazz: Class<*> = this::class.java) {
        // TODO: This is terrible for performance, but very cool :)
        // A better way to do it would be to use an annotation processor, and bake
        // these properties into the class's "init" method as direct calls to the
        // appropriate "defineXYZ" method intead of having to do all this reflection
        // every single time a property is instantiated

        val nativeProperties = mutableMapOf<PropertyKey, NativeMethodPair>()

        clazz.declaredMethods.filter {
            it.isAnnotationPresent(JSNativePropertyGetter::class.java)
        }.forEach { method ->
            val getter = method.getAnnotation(JSNativePropertyGetter::class.java)
            val methodPair = NativeMethodPair(attributes = getter.attributes, getter = { thisValue ->
                method.invoke(this, thisValue) as JSValue
            })
            val key = if (getter.name.startsWith("@@")) {
                realm.wellknownSymbols[getter.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${getter.name}")
            } else PropertyKey(getter.name)
            expect(key !in nativeProperties)
            nativeProperties[key] = methodPair
        }

        clazz.declaredMethods.filter {
            it.isAnnotationPresent(JSNativePropertySetter::class.java)
        }.forEach { method ->
            val setter = method.getAnnotation(JSNativePropertySetter::class.java)
            val key = if (setter.name.startsWith("@@")) {
                realm.wellknownSymbols[setter.name]?.let(::PropertyKey) ?:
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
            defineNativeProperty(name, Attributes(methods.attributes), methods.getter, methods.setter)
        }

        clazz.declaredMethods.filter {
            it.isAnnotationPresent(JSMethod::class.java)
        }.forEach {
            val annotation = it.getAnnotation(JSMethod::class.java)
            val key = if (annotation.name.startsWith("@@")) {
                realm.wellknownSymbols[annotation.name]?.let(::PropertyKey) ?:
                throw IllegalArgumentException("No well known symbol found with name ${annotation.name}")
            } else PropertyKey(annotation.name)

            defineNativeFunction(
                key,
                annotation.length,
                Attributes(annotation.attributes)
            ) { thisValue, arguments ->
                it.invoke(this, thisValue, arguments) as JSValue
            }
        }

        if (clazz.superclass != Object::class.java) {
            configureInstanceProperties(clazz.superclass)
        }
    }

    @JSThrows
    @ECMAImpl("[[GetPrototypeOf]]", "9.1.1")
    open fun getPrototype() = prototype

    @JSThrows
    @ECMAImpl("[[SetPrototypeOf]]", "9.1.2")
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
            checkError() ?: return false
        }

        prototype = p
        return true
    }

    @JSThrows
    fun hasProperty(property: String): Boolean = hasProperty(PropertyKey(property))

    @JSThrows
    @ECMAImpl("[[HasProperty]]", "9.1.7")
    fun hasProperty(property: PropertyKey): Boolean {
        val hasOwn = getOwnPropertyDescriptor(property)
        if (hasOwn != null)
            return true
        val parent = getPrototype()
        checkError() ?: return false
        if (parent != JSNull)
            return (parent as JSObject).hasProperty(property)
        return false
    }

    @JSThrows
    @ECMAImpl("[[IsExtensible]]", "9.1.3")
    open fun isExtensible() = extensible

    @JSThrows
    @ECMAImpl("[[PreventExtensions]]", "9.1.4")
    open fun preventExtensions(): Boolean {
        extensible = false
        return true
    }

    fun getOwnPropertyDescriptor(property: String) = getOwnPropertyDescriptor(PropertyKey(property))
    open fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        return properties[property]
    }

    @JSThrows
    fun getOwnProperty(property: String) = getOwnProperty(PropertyKey(property))

    @JSThrows
    @ECMAImpl("[[GetOwnProperty]]", "9.1.5")
    open fun getOwnProperty(property: PropertyKey): JSValue {
        return properties[property]?.toObject(realm) ?: JSUndefined
    }

    @JSThrows
    fun defineOwnProperty(property: String, descriptor: Descriptor) = defineOwnProperty(PropertyKey(property), descriptor)

    @JSThrows
    @ECMAImpl("[[DefineOwnProperty]]", "9.1.6")
    open fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        return validateAndApplyPropertyDescriptor(property, descriptor)
    }

    @JSThrows
    private fun validateAndApplyPropertyDescriptor(property: PropertyKey, newDesc: Descriptor): Boolean {
        val extensible = isExtensible()
        checkError() ?: return false
        val currentDesc = getOwnPropertyDescriptor(property)

        if (currentDesc == null) {
            if (!extensible)
                return false
            properties[property] = newDesc.copy()
            return true
        }

        // Doesn't play nice with a manually set undefined
//        if (newDesc.isEmpty)
//            return true

        if (currentDesc.attributes.run { hasConfigurable && !isConfigurable }) {
            if (newDesc.attributes.isConfigurable)
                return false
            if (newDesc.attributes.hasEnumerable && currentDesc.attributes.isEnumerable != newDesc.attributes.isEnumerable)
                return false
        }

        if (currentDesc.isDataDescriptor != newDesc.isDataDescriptor) {
            if (currentDesc.attributes.run { hasConfigurable && !isConfigurable })
                return false
            if (currentDesc.isDataDescriptor) {
                properties[property] = Descriptor(
                    JSUndefined,
                    Attributes(currentDesc.attributes.num and (WRITABLE or HAS_WRITABLE).inv()),
                    newDesc.getter,
                    newDesc.setter,
                )
            } else {
                properties[property] = Descriptor(
                    newDesc.value,
                    Attributes(currentDesc.attributes.num and (WRITABLE or HAS_WRITABLE).inv()),
                    null,
                    null,
                )
            }
        } else if (currentDesc.isDataDescriptor && newDesc.isDataDescriptor) {
            if (currentDesc.attributes.run { hasConfigurable && hasWritable && !isConfigurable && !isWritable }) {
                if (newDesc.attributes.isWritable)
                    return false
                if (!newDesc.value.sameValue(currentDesc.value))
                    return false
            }
        } else if (currentDesc.attributes.run { hasConfigurable && !isConfigurable }) {
            val currentSetter = currentDesc.setter
            val newSetter = newDesc.setter
            if (newSetter != null && (currentSetter == null || !newSetter.sameValue(currentSetter)))
                return false
            val currentGetter = currentDesc.setter
            val newGetter = newDesc.setter
            if (newGetter != null && (currentGetter == null || !newGetter.sameValue(currentGetter)))
                return false
            return true
        }

        val d = properties[property]!!

        if (newDesc.isDataDescriptor) {
            // To distinguish undefined from a non-specified property
            d.value = newDesc.value
        }

        d.getter = newDesc.getter
        d.setter = newDesc.setter

        newDesc.attributes.apply {
            if (hasConfigurable)
                d.attributes.setConfigurable(isConfigurable)
            if (hasEnumerable)
                d.attributes.setEnumerable(isEnumerable)
            if (hasWritable)
                d.attributes.setWritable(isWritable)
        }

        return true
    }

    @JSThrows
    fun get(property: String, receiver: JSValue = this) = get(PropertyKey(property), receiver)
    @JSThrows
    fun get(property: JSSymbol, receiver: JSValue = this) = get(PropertyKey(property), receiver)

    @JSThrows
    @JvmOverloads @ECMAImpl("[[Get]]", "9.1.8")
    open fun get(property: PropertyKey, receiver: JSValue = this): JSValue {
        val desc = getOwnPropertyDescriptor(property)
        if (desc == null) {
            val parent = getPrototype()
            checkError() ?: return INVALID_VALUE
            if (parent == JSNull)
                return JSUndefined
            return (parent as JSObject).get(property, receiver)
        }
        if (desc.isDataDescriptor)
            return desc.value
        expect(desc.isAccessorDescriptor)
        val getter = desc.getter ?: return JSUndefined
        return Operations.call(getter, receiver)
    }

    @JSThrows
    fun set(property: String, value: JSValue, receiver: JSValue = this) = set(PropertyKey(property), value, receiver)

    @JSThrows
    @JvmOverloads @ECMAImpl("[[Set]]", "9.1.9")
    open fun set(property: PropertyKey, value: JSValue, receiver: JSValue = this): Boolean {
        val ownDesc = getOwnPropertyDescriptor(property)
        return ordinarySetWithOwnDescriptor(property, value, receiver, ownDesc)
    }

    @JSThrows
    @ECMAImpl("OrdinarySetWithOwnDescriptor", "9.1.9.2")
    private fun ordinarySetWithOwnDescriptor(property: PropertyKey, value: JSValue, receiver: JSValue, ownDesc_: Descriptor?): Boolean {
        var ownDesc = ownDesc_
        if (ownDesc == null) {
            val parent = getPrototype()
            if (parent != JSNull)
                return (parent as JSObject).set(property, value, receiver)
            ownDesc = Descriptor(JSUndefined, Attributes(Attributes.defaultAttributes))
        }
        if (ownDesc.isDataDescriptor) {
            if (!ownDesc.attributes.isWritable)
                return false
            if (receiver !is JSObject)
                return false
            val existingDescriptor = receiver.getOwnPropertyDescriptor(property)
            if (existingDescriptor != null) {
                if (existingDescriptor.isAccessorDescriptor)
                    return false
                if (!existingDescriptor.attributes.isWritable)
                    return false
                val valueDesc = Descriptor(value, Attributes(0))
                return receiver.defineOwnProperty(property, valueDesc)
            }
            return receiver.createDataProperty(property, value)
        }
        expect(ownDesc.isAccessorDescriptor)
        val setter = ownDesc.setter ?: return false
        Operations.call(setter, receiver, listOf(value))
        checkError() ?: return false
        return true
    }

    @JSThrows
    @ECMAImpl("CreateDataProperty", "7.3.5")
    private fun createDataProperty(property: PropertyKey, value: JSValue): Boolean {
        val newDesc = Descriptor(value, Attributes(Attributes.defaultAttributes))
        return defineOwnProperty(property, newDesc)
    }

    @JSThrows
    fun delete(property: String) = delete(PropertyKey(property))

    @ECMAImpl("[[Delete]]", "9.1.10")
    open fun delete(property: PropertyKey): Boolean {
        val desc = getOwnPropertyDescriptor(property) ?: return true
        if (desc.attributes.isConfigurable) {
            properties.remove(property)
            return true
        }
        return false
    }

    @JSThrows
    @ECMAImpl("[[OwnPropertyKeys]]", "9.1.11")
    open fun ownPropertyKeys(): List<PropertyKey> {
        // TODO: Ordering is wrong here
        return properties.keys.toList()
    }

    fun defineNativeProperty(key: PropertyKey, attributes: Attributes, getter: NativeGetterSignature?, setter: NativeSetterSignature?) {
        val property = JSNativeProperty(getter, setter)
        defineOwnProperty(key, Descriptor(property, attributes))
    }

    fun defineNativeFunction(key: PropertyKey, length: Int, attributes: Attributes, function: NativeFunctionSignature) {
        val name = if (key.isString) key.asString else "[${key.asSymbol.descriptiveString()}]"
        val obj = JSNativeFunction.fromLambda(realm, name, length, function)
        defineOwnProperty(key, Descriptor(obj, attributes))
    }

    enum class PropertyKind {
        Key,
        Value,
        KeyValue
    }

    companion object {
        val INVALID_OBJECT by lazy { JSObject(Agent.runningContext.realm) }

        @JvmStatic
        @JvmOverloads
        fun create(realm: Realm, proto: JSObject = realm.objectProto) = JSObject(realm, proto).also { it.init() }

        protected fun thisBinding(context: ExecutionContext): JSValue {
            val env = context.lexicalEnv ?: shouldThrowError()
            if (!env.hasThisBinding())
                shouldThrowError()
            if (env is FunctionEnvRecord)
                return env.getThisBinding()
            if (env is GlobalEnvRecord)
                return env.getThisBinding()
            unreachable()
        }
    }
}
