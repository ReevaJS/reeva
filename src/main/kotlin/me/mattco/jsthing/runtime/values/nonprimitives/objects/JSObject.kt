package me.mattco.jsthing.runtime.values.nonprimitives.objects

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.annotations.ECMAImpl
import me.mattco.jsthing.runtime.annotations.JSMethod
import me.mattco.jsthing.runtime.annotations.JSNativePropertyGetter
import me.mattco.jsthing.runtime.annotations.JSNativePropertySetter
import me.mattco.jsthing.runtime.contexts.ExecutionContext
import me.mattco.jsthing.runtime.environment.FunctionEnvRecord
import me.mattco.jsthing.runtime.environment.GlobalEnvRecord
import me.mattco.jsthing.runtime.values.JSValue
import me.mattco.jsthing.runtime.values.nonprimitives.functions.*
import me.mattco.jsthing.runtime.values.primitives.JSNumber
import me.mattco.jsthing.runtime.values.primitives.JSString
import me.mattco.jsthing.runtime.values.primitives.JSUndefined
import me.mattco.jsthing.utils.ecmaAssert
import me.mattco.jsthing.utils.expect
import me.mattco.jsthing.utils.shouldThrowError
import me.mattco.jsthing.utils.unreachable

open class JSObject protected constructor(
    private val realm: Realm,
    private var prototype: JSValue
) : JSValue() {
    private val properties = mutableMapOf<PropertyKey, Descriptor>()
    private var extensible: Boolean = true

    data class NativeMethodPair(
        var attributes: Int,
        var getter: NativeGetterSignature? = null,
        var setter: NativeSetterSignature? = null,
    )

    open fun init() {
        // TODO: This is terrible for performance, but very cool :)
        // A better way to do it would be to use an annotation processor, and bake
        // these properties into the class's "init" method as direct calls to the
        // appropriate "defineXYZ" method intead of having to do all this reflection
        // every single time a property is instantiated

        val nativeProperties = mutableMapOf<String, NativeMethodPair>()

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSNativePropertyGetter::class.java)
        }.forEach { method ->
            val getter = method.getAnnotation(JSNativePropertyGetter::class.java)
            expect(getter.name !in nativeProperties)
            val methodPair = NativeMethodPair(attributes = getter.attributes, getter = {
                method.invoke(this, it) as JSValue
            })
            nativeProperties[getter.name] = methodPair
        }

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSNativePropertySetter::class.java)
        }.forEach { method ->
            val setter = method.getAnnotation(JSNativePropertySetter::class.java)
            val methodPair = if (setter.name in nativeProperties) {
                nativeProperties[setter.name]!!.also {
                    expect(it.attributes == setter.attributes)
                }
            } else {
                val t = NativeMethodPair(setter.attributes)
                nativeProperties[setter.name] = t
                t
            }
            methodPair.setter = { context, value ->
                method.invoke(this, context, value)
            }
        }

        nativeProperties.forEach { (name, methods) ->
            defineNativeProperty(PropertyKey(name), Attributes(methods.attributes), methods.getter, methods.setter)
        }

        this::class.java.declaredMethods.filter {
            it.isAnnotationPresent(JSMethod::class.java)
        }.forEach {
            val annotation = it.getAnnotation(JSMethod::class.java)
            defineNativeFunction(
                PropertyKey(annotation.name),
                annotation.length,
                Attributes(annotation.attributes)
            ) { context, arguments ->
                it.invoke(this, context, arguments) as JSValue
            }
        }
    }

    @ECMAImpl("[[GetPrototypeOf]]", "9.1.1")
    open fun getPrototype() = prototype

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
        }

        prototype = p
        return true
    }

    fun hasOwnProperty(property: String): Boolean = hasOwnProperty(PropertyKey(property))

    @ECMAImpl("[[HasProperty]]", "9.1.7")
    fun hasOwnProperty(property: PropertyKey): Boolean {
        TODO()
    }

    @ECMAImpl("[[IsExtensible]]", "9.1.3")
    open fun isExtensible() = extensible

    @ECMAImpl("[[PreventExtensions]]", "9.1.4")
    open fun preventExtensions(): Boolean {
        extensible = false
        return true
    }

    fun getOwnPropertyDescriptor(property: String) = getOwnPropertyDescriptor(PropertyKey(property))
    open fun getOwnPropertyDescriptor(property: PropertyKey): Descriptor? {
        TODO()
    }

    fun getOwnPropertyDescriptorObject(property: String) = getOwnPropertyDescriptorObject(PropertyKey(property))
    fun getOwnPropertyDescriptorObject(property: PropertyKey): JSValue {
        TODO()
    }

    fun getOwnProperty(property: String) = getOwnProperty(PropertyKey(property))

    @ECMAImpl("[[GetOwnProperty]]", "9.1.5")
    open fun getOwnProperty(property: PropertyKey): JSValue {
        if (property !in properties)
            return JSUndefined

        TODO()
    }

    fun defineOwnProperty(property: String, descriptor: Descriptor) = defineOwnProperty(PropertyKey(property), descriptor)

    @ECMAImpl("[[DefineOwnProperty]]", "9.1.6")
    open fun defineOwnProperty(property: PropertyKey, descriptor: Descriptor): Boolean {
        TODO()
    }

    fun hasProperty(property: String) = hasProperty(PropertyKey(property))
    open fun hasProperty(property: PropertyKey): Boolean {
        TODO()
    }

    fun get(property: String, receiver: JSValue = this) = get(PropertyKey(property), receiver)

    @ECMAImpl("[[Get]]", "9.1.8")
    open fun get(property: PropertyKey, receiver: JSValue = this): JSValue {
        TODO()
    }

    fun set(property: String, value: JSValue, receiver: JSValue = this) = set(PropertyKey(property), value, receiver)

    @ECMAImpl("[[Set]]", "9.1.9")
    open fun set(property: PropertyKey, value: JSValue, receiver: JSValue = this): Boolean {
        TODO()
    }

    fun delete(property: String) = delete(PropertyKey(property))

    @ECMAImpl("[[Delete]]", "9.1.10")
    open fun delete(property: PropertyKey): Boolean {
        TODO()
    }

    @ECMAImpl("[[OwnPropertyKeys]]", "9.1.11")
    open fun ownPropertyKeys(): List<JSValue> {
        TODO()
    }

    fun defineNativeProperty(key: PropertyKey, attributes: Attributes, getter: NativeGetterSignature?, setter: NativeSetterSignature?) {
        val property = JSNativeProperty(getter, setter)
        defineOwnProperty(key, Descriptor(property, attributes))
    }

    fun defineNativeFunction(key: PropertyKey, length: Int, attributes: Attributes, function: NativeFunctionSignature) {
        val obj = JSNativeFunction.fromLambda(realm, key.asString, function)
        obj.defineOwnProperty("length", Descriptor(JSNumber(length), Attributes(Attributes.CONFIGURABLE)))
        obj.defineOwnProperty("name", Descriptor(JSString(key.asString), Attributes(Attributes.CONFIGURABLE)))
        defineOwnProperty(key, Descriptor(obj, attributes))
    }

    companion object {
        fun create(realm: Realm, prototype: JSValue) = JSObject(realm, prototype).also { it.init() }

        @JvmStatic
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
