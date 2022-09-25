package com.reevajs.reeva.jvmcompat

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.collections.JSMapObject
import com.reevajs.reeva.runtime.collections.JSSetObject
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue
import java.lang.reflect.Executable
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.math.BigInteger

/**
 * Based on the [https://www-archive.mozilla.org/js/liveconnect/lc3_method_overloading.html]
 * specification.
 */
object JVMValueMapper {
    const val CONVERSION_FAILURE = -1

    fun getConversionWeight(value: JSValue, type: Class<*>) = when (value) {
        is JSUndefined -> 1
        is JSBoolean -> {
            when (type) {
                Boolean::class.javaPrimitiveType -> 1
                Boolean::class.javaObjectType -> 2
                Any::class.java -> 3
                String::class.java -> 4
                else -> CONVERSION_FAILURE
            }
        }
        is JSNumber -> {
            when (type) {
                Double::class.javaPrimitiveType -> 1
                Double::class.javaObjectType -> 2
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 3
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 4
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 5
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 6
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 7
                Char::class.javaPrimitiveType, Char::class.javaObjectType ->
                    if (value.isNaN) CONVERSION_FAILURE else 8
                String::class.java -> 9
                Any::class.java -> 10
                else -> CONVERSION_FAILURE
            }
        }
        is JSString -> {
            when (type) {
                String::class.java -> 1
                Any::class.java -> 2
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> 3
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> 4
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 5
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 6
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 8
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 9
                else -> CONVERSION_FAILURE
            }
        }
        is JSNull -> {
            if (!type.isPrimitive)
                1
            else
                CONVERSION_FAILURE
        }
        is JSClassInstanceObject -> {
            val javaObject = value.obj
            if (type.isInstance(javaObject))
                1
            else when (type) {
                String::class.java -> 2
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> 3
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 4
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 5
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 6
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 7
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 8
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> 9
                else -> CONVERSION_FAILURE
            }
        }
        is JSClassObject -> {
            when (type) {
                Class::class.java -> 1
                Any::class.java -> 2
                String::class.java -> 3
                else -> CONVERSION_FAILURE
            }
        }
        is JSArrayObject -> {
            when {
                type.isArray -> 1
                List::class.java.isAssignableFrom(type) -> 2
                else -> when (type) {
                    Any::class.java -> 3
                    String::class.java -> 4
                    else -> CONVERSION_FAILURE
                }
            }
        }
        is JSObject -> {
            when (type) {
                Any::class.java -> 1
                String::class.java -> 2
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> 3
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> 4
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 5
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 6
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> 7
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 8
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> 9
                else -> CONVERSION_FAILURE
            }
        }
        else -> CONVERSION_FAILURE
    }

    @JvmOverloads
    @JvmStatic
    fun jsToJvm(
        value: JSValue,
        targetClass: Class<*>,
        genericInfo: Array<Type>? = null,
    ): Any? = when (value) {
        is JSUndefined -> {
            if (targetClass == String::class.java) {
                "undefined"
            } else {
                null
            }
        }
        is JSBoolean -> {
            when (targetClass) {
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType, Any::class.java -> value.boolean
                String::class.java -> value.boolean.toString()
                else -> errorIfIncompatible(value, targetClass)
            }
        }
        is JSNumber -> {
            when (targetClass) {
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Any::class.java -> value.number
                // TODO: Perhaps these conversion algos need more fine tuning
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> value.number.toFloat()
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> value.number.toLong()
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> value.number.toInt()
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> value.number.toInt().toShort()
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> value.number.toInt().toByte()
                Char::class.javaPrimitiveType, Char::class.javaObjectType ->
                    if (value.isNaN) errorIfIncompatible(value, targetClass)
                    else value.number.toInt().toChar()
                String::class.java -> AOs.numericToString(value)
                else -> errorIfIncompatible(value, targetClass)
            }
        }
        is JSString -> {
            when (targetClass) {
                String::class.java, Any::class.java -> value.string
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Float::class.javaPrimitiveType,
                Float::class.javaObjectType, Long::class.javaPrimitiveType, Long::class.javaObjectType,
                Int::class.javaPrimitiveType, Int::class.javaObjectType, Short::class.javaPrimitiveType,
                Short::class.javaObjectType, Byte::class.javaPrimitiveType, Byte::class.javaObjectType ->
                    jsToJvm(value.toNumber(), targetClass)
                Char::class.javaPrimitiveType, Char::class.javaObjectType -> if (value.string.length == 1) {
                    value.string[0]
                } else jsToJvm(value.toNumber(), targetClass)
                else -> errorIfIncompatible(value, targetClass)
            }
        }
        is JSNull -> {
            if (!targetClass.isPrimitive)
                null
            else
                errorIfIncompatible(value, targetClass)
        }
        is JSArrayObject -> {
            if (targetClass == String::class.java) {
                value.toJSString().string
            } else if (List::class.java.isAssignableFrom(targetClass)) {
                val listInstance: MutableList<Any?> = if (targetClass == List::class.java) {
                    mutableListOf()
                } else targetClass.newInstance() as MutableList<Any?>
                val listType = genericInfo?.firstOrNull() as? Class<*> ?: Any::class.java

                value.indexedProperties.indices().forEach { index ->
                    if (index !in Int.MIN_VALUE..Int.MAX_VALUE)
                        TODO()
                    listInstance.add(index.toInt(), jsToJvm(value.get(index), listType))
                }

                listInstance
            } else if (targetClass.isArray || targetClass == Any::class.java) {
                val arrayType = if (targetClass.isArray) targetClass.componentType else Any::class.java
                val constructedArray = java.lang.reflect.Array.newInstance(
                    arrayType,
                    JSArrayObject.getLength(value).asInt,
                )
                value.indexedProperties.indices().forEach { index ->
                    if (index !in Int.MIN_VALUE..Int.MAX_VALUE)
                        TODO()
                    java.lang.reflect.Array.set(
                        constructedArray,
                        index.toInt(),
                        jsToJvm(value.get(index), arrayType),
                    )
                }
                constructedArray
            } else {
                errorIfIncompatible(value, targetClass)
            }
        }
        is JSClassInstanceObject -> {
            val javaObject = value.obj
            if (targetClass.isInstance(javaObject)) {
                javaObject
            } else when (targetClass) {
                String::class.java -> javaObject.toString()
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Float::class.javaPrimitiveType,
                Float::class.javaObjectType, Long::class.javaPrimitiveType, Long::class.javaObjectType,
                Int::class.javaPrimitiveType, Int::class.javaObjectType, Short::class.javaPrimitiveType,
                Short::class.javaObjectType, Byte::class.javaPrimitiveType, Byte::class.javaObjectType ->
                    jsToJvm(javaObject.toString().toValue(), targetClass)
                else -> errorIfIncompatible(value, targetClass)
            }
        }
        is JSClassObject -> {
            when (targetClass) {
                Class::class.java, Any::class.java -> value.clazz
                String::class.java -> value.clazz.toString()
                else -> errorIfIncompatible(value, targetClass)
            }
        }
        is JSObject -> {
            when (targetClass) {
                Any::class.java -> TODO("convert to map?")
                String::class.java -> value.toJSString().string
                Double::class.javaPrimitiveType, Double::class.javaObjectType, Float::class.javaPrimitiveType,
                Float::class.javaObjectType, Long::class.javaPrimitiveType, Long::class.javaObjectType,
                Int::class.javaPrimitiveType, Int::class.javaObjectType, Short::class.javaPrimitiveType,
                Short::class.javaObjectType, Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> jsToJvm(
                    value.toPrimitive(AOs.ToPrimitiveHint.AsNumber),
                    targetClass
                )
                else -> errorIfIncompatible(value, targetClass)
            }
        }
        else -> errorIfIncompatible(value, targetClass)
    }

    private fun errorIfIncompatible(value: JSValue, targetClass: Class<*>): JSValue {
        if (!targetClass.isInstance(value))
            Errors.JVMCompat.InconvertibleType(value, targetClass).throwTypeError()
        return value
    }

    fun <T : Executable> findMatchingSignature(executables: List<T>, arguments: List<JSValue>): List<T> {
        val weightedExecutables = executables.filter { it.parameterCount == arguments.size }.groupBy {
            val weights = it.parameterTypes.withIndex().map { (i, type) -> getConversionWeight(arguments[i], type) }

            if (weights.any { weight -> weight == CONVERSION_FAILURE })
                CONVERSION_FAILURE
            else
                weights.sum()
        }

        val minWeight = weightedExecutables.keys.filter { it != CONVERSION_FAILURE }.minOrNull() ?: return emptyList()
        return weightedExecutables.getValue(minWeight)
    }

    fun coerceArgumentsToSignature(realm: Realm, signature: Executable, arguments: List<JSValue>): List<Any?> {
        val genericTypes = signature.genericParameterTypes

        return arguments.mapIndexed { index, jsValue ->
            val genericType = genericTypes[index]
            if (genericType is ParameterizedType) {
                jsToJvm(
                    jsValue,
                    genericType.rawType as Class<*>,
                    genericInfo = genericType.actualTypeArguments,
                )
            } else jsToJvm(jsValue, genericType as Class<*>)
        }
    }

    @JvmStatic
    fun jvmToJS(instance: Any?): JSValue {
        return when (instance) {
            null -> JSUndefined
            is Boolean -> if (instance) JSTrue else JSFalse
            is String -> JSString(instance)
            is Double -> JSNumber(instance)
            is Float -> JSNumber(instance.toDouble())
            is Number -> JSNumber(instance)
            is BigInteger -> JSBigInt(instance)
            is Map<*, *> -> {
                val jsMap = JSMapObject.create()
                instance.forEach { (key, value) ->
                    val jsKey = jvmToJS(key)
                    jsMap.mapData.map[jsKey] = jvmToJS(value)
                    jsMap.mapData.keyInsertionOrder.add(jsKey)
                }
                jsMap
            }
            is Set<*> -> {
                val jsSet = JSSetObject.create()
                instance.forEach { key ->
                    val jsKey = jvmToJS(key)
                    jsSet.setData.set.add(jsKey)
                    jsSet.setData.insertionOrder.add(jsKey)
                }
                jsSet
            }
            is Collection<*> -> {
                val jsArray = JSArrayObject.create()
                instance.forEachIndexed { index, value ->
                    jsArray.set(index, jvmToJS(value))
                }
                jsArray
            }
            is Array<*> -> {
                val jsArray = JSArrayObject.create()
                instance.forEachIndexed { index, value ->
                    jsArray.set(index, jvmToJS(value))
                }
                jsArray
            }
            is Package -> JSPackageObject.create(instance.name)
            is Class<*> -> JSClassObject.create(instance)
            else -> JSClassInstanceObject.wrap(instance)
        }
    }
}
