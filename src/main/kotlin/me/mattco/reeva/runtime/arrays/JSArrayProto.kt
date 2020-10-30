package me.mattco.reeva.runtime.arrays

import me.mattco.reeva.core.Agent.Companion.ifError
import me.mattco.reeva.core.Agent.Companion.throwError
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.annotations.JSMethod
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.JSTypeErrorObject
import me.mattco.reeva.runtime.iterators.JSArrayIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*
import kotlin.math.max
import kotlin.math.min

class JSArrayProto private constructor(realm: Realm) : JSArrayObject(realm, realm.objectProto) {
    override fun init() {
        // No super call to avoid prototype complications

        internalSetPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, 0)
        configureInstanceProperties()

        defineOwnProperty("constructor", realm.arrayCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        // "The initial values of the @@iterator property is the same function object as the initial
        // value of the Array.prototype.values property.
        // https://tc39.es/ecma262/#sec-array.prototype-@@iterator
        defineOwnProperty(Realm.`@@iterator`, internalGet("values".key())!!.getRawValue())
    }

    @JSMethod("concat", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun concat(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val array = Operations.arraySpeciesCreate(thisObj, 0)
        ifError { return INVALID_VALUE }

        val items = listOf(thisObj) + arguments
        var n = 0
        items.forEach { item ->
            val isConcatSpreadable = if (item !is JSObject) {
                false
            } else {
                val isSpreadable = item.get(Realm.`@@isConcatSpreadable`)
                if (isSpreadable != JSUndefined) {
                    Operations.toBoolean(isSpreadable) == JSTrue
                } else Operations.isArray(item)
            }
            if (isConcatSpreadable) {
                val length = Operations.lengthOfArrayLike(item)
                ifError { return INVALID_VALUE }
                val indices = (item as JSObject).indexedProperties.indices()

                for (index in indices) {
                    if (index >= length)
                        break
                    Operations.createDataPropertyOrThrow(array, (n + index).key(), item.get(index))
                    ifError { return INVALID_VALUE }
                }

                n += indices.last()
            } else {
                Operations.createDataPropertyOrThrow(array, n.key(), item)
                ifError { return INVALID_VALUE }
            }
        }
        return array
    }

    @JSMethod("copyWithin", 2, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun copyWithin(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, start, end) = arguments.takeArgs(0..2)

        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val length = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }

        val relativeTarget = Operations.toIntegerOrInfinity(target)
        ifError { return INVALID_VALUE }
        var to = when {
            relativeTarget.isNegativeInfinity -> 0
            relativeTarget.asInt < 0 -> max(length + relativeTarget.asInt, 0)
            else -> min(relativeTarget.asInt, length)
        }
        val relativeStart = Operations.toIntegerOrInfinity(start)
        ifError { return INVALID_VALUE }

        var from = when {
            relativeStart.isNegativeInfinity -> 0
            relativeStart.asInt < 0 -> max(length + relativeStart.asInt, 0)
            else -> min(relativeStart.asInt, length)
        }
        val relativeEnd = if (end == JSUndefined) {
            length.toValue()
        } else Operations.toIntegerOrInfinity(end)
        ifError { return INVALID_VALUE }

        val final = when {
            relativeEnd.isNegativeInfinity -> 0
            relativeEnd.asInt < 0 -> max(length + relativeEnd.asInt, 0)
            else -> min(relativeEnd.asInt, length)
        }

        var count = min(final - from, length - to)
        val direction = if (from < to && to < from + count) {
            from = from + count - 1
            to = to + count - 1
            -1
        } else 1

        while (count > 0) {
            if (thisObj.hasProperty(from)) {
                val fromVal = thisObj.get(from)
                if (!thisObj.set(to, fromVal)) {
                    throwError<JSTypeErrorObject>("TODO: message")
                    return INVALID_VALUE
                }
            } else if (!Operations.deletePropertyOrThrow(thisObj, to.key())) {
                return INVALID_VALUE
            }
            from += direction
            to += direction
            count -= 1
        }
        return thisObj
    }

    @JSMethod("every", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun every(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (callback, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(callback)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.every must be callable")
            return INVALID_VALUE
        }

        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val length = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            val testResult = Operations.call(callback, thisArg, listOf(value, index.toValue(), thisObj))
            ifError { return INVALID_VALUE }
            val testBoolean = Operations.toBoolean(testResult)
            ifError { return INVALID_VALUE }
            if (testBoolean == JSFalse)
                return JSFalse
        }

        return JSTrue
    }

    @JSMethod("fill", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun fill(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }

        val length = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }

        val (value, start, end) = arguments.takeArgs(0..2)
        val relativeStart = Operations.toIntegerOrInfinity(start)
        ifError { return INVALID_VALUE }

        var startIndex = when {
            relativeStart.isNegativeInfinity -> 0
            relativeStart.asInt < 0 -> max(length + relativeStart.asInt, 0)
            else -> min(relativeStart.asInt, length)
        }

        val relativeEnd = if (end == JSUndefined) {
            length.toValue()
        } else Operations.toIntegerOrInfinity(end).also {
            ifError { return INVALID_VALUE }
        }

        val endIndex = when {
            relativeEnd.isNegativeInfinity -> 0
            relativeEnd.asInt < 0 -> max(length + relativeEnd.asInt, 0)
            else -> min(relativeEnd.asInt, length)
        }

        while (startIndex < endIndex) {
            thisObj.set(startIndex, value)
            startIndex++
        }

        return thisObj
    }

    @JSMethod("filter", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun filter(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val length = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }

        val (callback, thisArg) = arguments.takeArgs(0..2)
        if (!Operations.isCallable(callback)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.filter must be callable")
            return INVALID_VALUE
        }

        val array = Operations.arraySpeciesCreate(thisObj, 0)
        var toIndex = 0

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            val callbackResult = Operations.call(callback, thisArg, listOf(value, index.toValue(), thisObj))
            ifError { return INVALID_VALUE }
            val booleanResult = Operations.toBoolean(callbackResult)
            ifError { return INVALID_VALUE }
            if (booleanResult == JSTrue) {
                if (!Operations.createDataPropertyOrThrow(array, toIndex.key(), value))
                    return INVALID_VALUE
                toIndex++
            }
        }

        return array
    }

    @JSMethod("find", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun find(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val length = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }
        val (predicate, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(predicate)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.find must be callable")
            return INVALID_VALUE
        }

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            ifError { return INVALID_VALUE }
            val testResult = Operations.call(predicate, thisArg, listOf(value, index.toValue(), thisObj))
            ifError { return INVALID_VALUE }
            val booleanResult = Operations.toBoolean(testResult)
            ifError { return INVALID_VALUE }
            if (booleanResult == JSTrue)
                return value
        }

        return JSUndefined
    }

    @JSMethod("findIndex", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun findIndex(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val length = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }
        val (predicate, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(predicate)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.find must be callable")
            return INVALID_VALUE
        }

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            ifError { return INVALID_VALUE }
            val testResult = Operations.call(predicate, thisArg, listOf(value, index.toValue(), thisObj))
            ifError { return INVALID_VALUE }
            val booleanResult = Operations.toBoolean(testResult)
            ifError { return INVALID_VALUE }
            if (booleanResult == JSTrue)
                return index.toValue()
        }

        return JSUndefined
    }

    @JSMethod("flat", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun flat(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val sourceLength = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }
        val depth = if (arguments.isNotEmpty()) {
            Operations.toIntegerOrInfinity(arguments[0]).let {
                ifError { return INVALID_VALUE }
                it.asInt.coerceAtLeast(0)
            }
        } else 1

        val array = Operations.arraySpeciesCreate(thisObj, 0)
        flattenIntoArray(array, thisObj, sourceLength, 0, depth)
        return array
    }

    @JSMethod("flatMap", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun flatMap(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val sourceLength = Operations.lengthOfArrayLike(thisObj)
        ifError { return INVALID_VALUE }

        val (mapperFunction, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(mapperFunction)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.flatMap must be callable")
            return INVALID_VALUE
        }

        val array = Operations.arraySpeciesCreate(thisObj, 0)
        ifError { return INVALID_VALUE }
        flattenIntoArray(array, thisObj, sourceLength, 0, 1, mapperFunction, thisArg)
        ifError { return INVALID_VALUE }
        return array
    }

    @JSMethod("forEach", 1, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun forEach(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        val length = Operations.lengthOfArrayLike(obj)
        ifError { return INVALID_VALUE }

        val callbackFn = arguments.argument(0)
        if (!Operations.isCallable(callbackFn)) {
            throwError<JSTypeErrorObject>("the first argument to Array.prototype.forEach must be callable")
            return INVALID_VALUE
        }

        for (index in obj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = obj.indexedProperties.get(thisValue, index)
            ifError { return INVALID_VALUE }
            Operations.call(callbackFn, arguments.argument(1), listOf(value, index.toValue(), obj))
        }

        return JSUndefined
    }

    @JSMethod("keys", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun keys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        return createArrayIterator(realm, obj, PropertyKind.Key)
    }

    @JSMethod("entries", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        return createArrayIterator(realm, obj, PropertyKind.KeyValue)
    }

    @JSMethod("values", 0, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
    fun values(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        ifError { return INVALID_VALUE }
        return createArrayIterator(realm, obj, PropertyKind.Value)
    }

    private fun flattenIntoArray(
        target: JSValue,
        source: JSValue,
        sourceLength: Int,
        start: Int,
        depth: Int?, // null indicates +Infinity
        mapperFunction: JSValue? = null,
        thisArg: JSValue? = null
    ): Int {
        ecmaAssert(target is JSObject)
        ecmaAssert(source is JSObject)

        if (mapperFunction != null) {
            ecmaAssert(thisArg != null)
            ecmaAssert(depth == 1)
        }

        var targetIndex = start
        var sourceIndex = 0

        while (sourceIndex < sourceLength) {
            if (source.hasProperty(sourceIndex)) {
                var element = source.get(sourceIndex)
                if (mapperFunction != null)
                    element = Operations.call(mapperFunction, thisArg!!, listOf(element, sourceIndex.toValue(), source))
                ifError { return -1 }

                val shouldFlatten = if (depth == null || depth > 0) Operations.isArray(element) else false
                if (shouldFlatten) {
                    val newDepth = if (depth == null) null else depth - 1
                    val elementLength = Operations.lengthOfArrayLike(element)
                    // TODO: Spec says we should pass mapperFunction? Is that true?
                    targetIndex = flattenIntoArray(target, element, elementLength, targetIndex, newDepth)
                } else {
                    if (targetIndex >= Operations.MAX_SAFE_INTEGER) {
                        throwError<JSTypeErrorObject>("TODO: message")
                        return -1
                    }
                    if (!Operations.createDataPropertyOrThrow(target, targetIndex.key(), element))
                        return -1
                    targetIndex++
                }
            }
            sourceIndex++
        }

        return targetIndex
    }

    companion object {
        fun create(realm: Realm) = JSArrayProto(realm).also { it.init() }

        @ECMAImpl("22.1.5.1")
        private fun createArrayIterator(realm: Realm, array: JSObject, kind: PropertyKind): JSValue {
            return JSArrayIterator.create(realm, array, 0, kind)
        }
    }
}
