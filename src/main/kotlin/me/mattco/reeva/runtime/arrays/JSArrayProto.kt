package me.mattco.reeva.runtime.arrays

import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.annotations.ECMAImpl
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.iterators.JSArrayIterator
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSNull
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class JSArrayProto private constructor(realm: Realm) : JSArrayObject(realm, realm.objectProto) {
    override fun init() {
        // No super call to avoid prototype complications

        setPrototype(realm.objectProto)
        defineOwnProperty("prototype", realm.objectProto, Descriptor.HAS_BASIC)
        defineOwnProperty("constructor", realm.arrayCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        // Inherit length getter/setter
        defineNativeProperty("length".key(), Descriptor.WRITABLE, ::getLength, ::setLength)

        val unscopables = create(realm, JSNull)
        Operations.createDataPropertyOrThrow(unscopables, "copyWithin".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "entries".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "fill".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "find".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "findIndex".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "flat".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "flatMap".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "includes".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "keys".key(), JSTrue)
        Operations.createDataPropertyOrThrow(unscopables, "values".key(), JSTrue)
        defineOwnProperty(Realm.`@@unscopables`, unscopables, Descriptor.CONFIGURABLE)

        defineNativeFunction("concat", 1, ::concat)
        defineNativeFunction("copyWithin", 2, ::copyWithin)
        defineNativeFunction("entries", 0, ::entries)
        defineNativeFunction("every", 1, ::every)
        defineNativeFunction("fill", 1, ::fill)
        defineNativeFunction("filter", 1, ::filter)
        defineNativeFunction("find", 1, ::find)
        defineNativeFunction("findIndex", 1, ::findIndex)
        defineNativeFunction("flat", 1, ::flat)
        defineNativeFunction("flatMap", 1, ::flatMap)
        defineNativeFunction("forEach", 1, ::forEach)
        defineNativeFunction("join", 1, ::join)
        defineNativeFunction("keys", 1, ::keys)
        defineNativeFunction("lastIndexOf", 1, ::lastIndexOf)
        defineNativeFunction("map", 1, ::map)
        defineNativeFunction("pop", 1, ::pop)
        defineNativeFunction("push", 1, ::push)
        defineNativeFunction("reduce", 1, ::reduce)
        defineNativeFunction("reduceRight", 1, ::reduceRight)
        defineNativeFunction("reverse", 1, ::reverse)
        defineNativeFunction("shift", 1, ::shift)
        defineNativeFunction("slice", 1, ::slice)
        defineNativeFunction("some", 1, ::some)
        defineNativeFunction("splice", 1, ::splice)
        defineNativeFunction("toString", 1, ::toString)
        defineNativeFunction("unshift", 1, ::unshift)
        defineNativeFunction("values", 1, ::values)

        // "The initial values of the @@iterator property is the same function object as the initial
        // value of the Array.prototype.values property.
        // https://tc39.es/ecma262/#sec-array.prototype-@@iterator
        defineOwnProperty(Realm.`@@iterator`, internalGet("values".key())!!.getRawValue())
    }

    fun concat(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        val array = Operations.arraySpeciesCreate(thisObj, 0)

        val items = listOf(thisObj) + arguments
        var n = 0L
        items.forEach { item ->
            val isConcatSpreadable = if (item !is JSObject) {
                false
            } else {
                val isSpreadable = item.get(Realm.`@@isConcatSpreadable`)
                if (isSpreadable != JSUndefined) {
                    Operations.toBoolean(isSpreadable)
                } else Operations.isArray(item)
            }
            if (isConcatSpreadable) {
                val length = Operations.lengthOfArrayLike(item)
                if (length == 0L)
                    return@forEach
                val indices = (item as JSObject).indexedProperties.indices()

                for (index in indices) {
                    if (index >= length)
                        break
                    Operations.createDataPropertyOrThrow(array, (n + index).key(), item.get(index))
                }

                n += length
            } else {
                Operations.createDataPropertyOrThrow(array, n.key(), item)
            }
        }
        Operations.set(array as JSObject, "length".key(), n.toValue(), true)
        return array
    }

    fun copyWithin(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (target, start, end) = arguments.takeArgs(0..2)

        val thisObj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(thisObj)

        val relativeTarget = Operations.toIntegerOrInfinity(target)
        var to = when {
            relativeTarget.isNegativeInfinity -> 0
            relativeTarget.asLong < 0 -> max(length + relativeTarget.asLong, 0L)
            else -> min(relativeTarget.asLong, length)
        }
        val relativeStart = Operations.toIntegerOrInfinity(start)

        var from = when {
            relativeStart.isNegativeInfinity -> 0
            relativeStart.asLong < 0 -> max(length + relativeStart.asLong, 0L)
            else -> min(relativeStart.asLong, length)
        }
        val relativeEnd = if (end == JSUndefined) {
            length.toValue()
        } else Operations.toIntegerOrInfinity(end)

        val final = when {
            relativeEnd.isNegativeInfinity -> 0
            relativeEnd.asLong < 0L -> max(length + relativeEnd.asLong, 0L)
            else -> min(relativeEnd.asLong, length)
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
                if (!thisObj.set(to, fromVal))
                    Errors.Array.CopyWithinFailedSet(from, to).throwTypeError()
            } else if (!Operations.deletePropertyOrThrow(thisObj, to.key())) {
                return INVALID_VALUE
            }
            from += direction
            to += direction
            count -= 1
        }
        return thisObj
    }

    fun entries(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        return createArrayIterator(realm, obj, PropertyKind.KeyValue)
    }

    fun every(thisValue: JSValue, arguments: JSArguments): JSValue {
        val (callback, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(callback))
            Errors.Array.CallableFirstArg("every").throwTypeError()

        val thisObj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(thisObj)

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            val testResult = Operations.call(callback, thisArg, listOf(value, index.toValue(), thisObj))
            val testBoolean = Operations.toBoolean(testResult)
            if (!testBoolean)
                return JSFalse
        }

        return JSTrue
    }

    fun fill(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)

        val length = Operations.lengthOfArrayLike(thisObj)

        val (value, start, end) = arguments.takeArgs(0..2)
        val relativeStart = Operations.toIntegerOrInfinity(start)

        var startIndex = when {
            relativeStart.isNegativeInfinity -> 0
            relativeStart.asLong < 0L -> max(length + relativeStart.asLong, 0L)
            else -> min(relativeStart.asLong, length)
        }

        val relativeEnd = if (end == JSUndefined) {
            length.toValue()
        } else Operations.toIntegerOrInfinity(end)

        val endIndex = when {
            relativeEnd.isNegativeInfinity -> 0
            relativeEnd.asLong < 0L -> max(length + relativeEnd.asLong, 0L)
            else -> min(relativeEnd.asLong, length)
        }

        while (startIndex < endIndex) {
            thisObj.set(startIndex, value)
            startIndex++
        }

        return thisObj
    }

    fun filter(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(thisObj)

        val (callback, thisArg) = arguments.takeArgs(0..2)
        if (!Operations.isCallable(callback))
            Errors.Array.CallableFirstArg("filter").throwTypeError()

        val array = Operations.arraySpeciesCreate(thisObj, 0)
        var toIndex = 0

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            val callbackResult = Operations.call(callback, thisArg, listOf(value, index.toValue(), thisObj))
            val booleanResult = Operations.toBoolean(callbackResult)
            if (booleanResult) {
                if (!Operations.createDataPropertyOrThrow(array, toIndex.key(), value))
                    return INVALID_VALUE
                toIndex++
            }
        }

        return array
    }

    fun find(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(thisObj)
        val (predicate, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(predicate))
            Errors.Array.CallableFirstArg("find").throwTypeError()

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            val testResult = Operations.call(predicate, thisArg, listOf(value, index.toValue(), thisObj))
            val booleanResult = Operations.toBoolean(testResult)
            if (booleanResult)
                return value
        }

        return JSUndefined
    }

    fun findIndex(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(thisObj)
        val (predicate, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(predicate))
            Errors.Array.CallableFirstArg("findIndex").throwTypeError()

        for (index in thisObj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = thisObj.get(index)
            val testResult = Operations.call(predicate, thisArg, listOf(value, index.toValue(), thisObj))
            val booleanResult = Operations.toBoolean(testResult)
            if (booleanResult)
                return index.toValue()
        }

        return JSUndefined
    }

    fun flat(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        val sourceLength = Operations.lengthOfArrayLike(thisObj)
        val depth = if (arguments.isNotEmpty()) {
            Operations.toIntegerOrInfinity(arguments[0]).asInt.coerceAtLeast(0)
        } else 1

        val array = Operations.arraySpeciesCreate(thisObj, 0)
        flattenIntoArray(array, thisObj, sourceLength, 0, depth)
        return array
    }

    fun flatMap(thisValue: JSValue, arguments: JSArguments): JSValue {
        val thisObj = Operations.toObject(thisValue)
        val sourceLength = Operations.lengthOfArrayLike(thisObj)

        val (mapperFunction, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(mapperFunction))
            Errors.Array.CallableFirstArg("flatMap").throwTypeError()

        val array = Operations.arraySpeciesCreate(thisObj, 0)
        flattenIntoArray(array, thisObj, sourceLength, 0, 1, mapperFunction, thisArg)
        return array
    }

    fun forEach(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)

        val callbackFn = arguments.argument(0)
        if (!Operations.isCallable(callbackFn))
            Errors.Array.CallableFirstArg("forEach").throwTypeError()

        for (index in obj.indexedProperties.indices()) {
            if (index >= length)
                break

            val value = obj.indexedProperties.get(thisValue, index)
            Operations.call(callbackFn, arguments.argument(1), listOf(value, index.toValue(), obj))
        }

        return JSUndefined
    }

    fun join(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val sep = if (arguments.isEmpty()) "," else Operations.toString(arguments[0]).string

        return buildString {
            for (i in 0 until length) {
                if (i > 0)
                    append(sep)
                val element = obj.get(i)
                if (!element.isNullish)
                    append(Operations.toString(element).string)
            }
        }.toValue()
    }

    fun keys(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        return createArrayIterator(realm, obj, PropertyKind.Key)
    }

    fun lastIndexOf(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        if (length == 0L)
            return (-1).toValue()

        val (searchElement, fromIndex) = arguments.takeArgs(0..1)
        val fromNumber = if (fromIndex == JSUndefined) {
            length - 1L
        } else Operations.toIntegerOrInfinity(fromIndex).let {
            if (it.isNegativeInfinity)
                return (-1).toValue()
            it.asLong
        }

        val limit = if (fromNumber >= 0L) {
            min(fromNumber, length - 1L)
        } else length + fromNumber

        obj.indexedProperties.indices().asReversed().filter {
            it <= limit
        }.forEach {
            val value = obj.get(it)
            if (Operations.strictEqualityComparison(searchElement, value) == JSTrue)
                return it.toValue()
        }

        return (-1).toValue()
    }

    fun map(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val (callback, thisArg) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Array.CallableFirstArg("map").throwTypeError()

        val array = Operations.arraySpeciesCreate(obj, length)
        obj.indexedProperties.indices().filter {
            it < length
        }.forEach {
            val value = obj.get(it)
            val mappedValue = Operations.call(callback, thisArg, listOf(value, it.toValue(), obj))
            Operations.createDataPropertyOrThrow(array, it.key(), mappedValue)
        }

        return array
    }

    fun pop(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        if (length == 0L)
            return JSUndefined

        val element = obj.get(length - 1)
        Operations.deletePropertyOrThrow(obj, (length - 1).key())
        obj.set("length", (length - 1).toValue())
        return element
    }

    fun push(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        var length = Operations.lengthOfArrayLike(obj)
        if (length + arguments.size >= Operations.MAX_SAFE_INTEGER)
            Errors.Array.GrowToInvalidLength.throwTypeError()
        arguments.forEach {
            Operations.set(obj, length.key(), it, true)
            length++
        }
        Operations.set(obj, "length".key(), length.toValue(), true)
        return length.toValue()
    }

    fun reduce(thisValue: JSValue, arguments: JSArguments): JSValue {
        return reduceHelper(thisValue, arguments, false)
    }

    fun reduceRight(thisValue: JSValue, arguments: JSArguments): JSValue {
        return reduceHelper(thisValue, arguments, true)
    }

    fun reverse(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        var length = Operations.lengthOfArrayLike(obj)
        val middle = floor(length / 2.0)
        val indices = obj.indexedProperties.indices()

        val lowerIndices = mutableSetOf<Long>()
        val upperIndices = mutableSetOf<Long>()
        indices.forEach {
            if (it < middle) {
                lowerIndices.add(it)
            } else {
                upperIndices.add(it)
            }
        }

        val lowerIt = lowerIndices.iterator()
        while (lowerIt.hasNext()) {
            val lowerIndex = lowerIt.next()
            val lowerVal = obj.get(lowerIndex)
            val upperIndex = length - lowerIndex - 1

            if (upperIndex in upperIndices) {
                val upperVal = obj.get(upperIndex)
                Operations.set(obj, upperIndex.key(), lowerVal, true)
                Operations.set(obj, lowerIndex.key(), upperVal, true)
                upperIndices.remove(upperIndex)
            } else {
                Operations.set(obj, upperIndex.key(), lowerVal, true)
                Operations.deletePropertyOrThrow(obj, lowerIndex.key())
            }

            lowerIt.remove()
        }

        val upperIt = upperIndices.iterator()
        while (upperIt.hasNext()) {
            val upperIndex = upperIt.next()
            val upperVal = obj.get(upperIndex)
            val lowerIndex = length - upperIndex - 1

            if (lowerIndex in lowerIndices) {
                val lowerVal = obj.get(lowerIndex)
                Operations.set(obj, lowerIndex.key(), upperVal, true)
                Operations.set(obj, upperIndex.key(), lowerVal, true)
                lowerIndices.remove(lowerIndex)
            } else {
                Operations.set(obj, lowerIndex.key(), upperVal, true)
                Operations.deletePropertyOrThrow(obj, upperIndex.key())
            }

            upperIt.remove()
        }

        expect(lowerIndices.isEmpty() && upperIndices.isEmpty())

        return obj
    }

    fun shift(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        if (length == 0L)
            return JSUndefined

        val element = obj.indexedProperties.removeFirst(obj)
        Operations.set(obj, "length".key(), (length - 1).toValue(), true)
        return element.getActualValue(obj)
    }

    fun slice(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val (start, end) = arguments.takeArgs(0..1)

        val k = Operations.toIntegerOrInfinity(start).let {
            when {
                it.isNegativeInfinity -> 0
                it.asLong < 0L -> max(length + it.asLong, 0L)
                else -> min(it.asLong, length)
            }
        }

        val final = (if (end == JSUndefined) length else Operations.toIntegerOrInfinity(end).asLong).let {
            when {
                it == Long.MIN_VALUE -> 0
                it < 0L -> max(length + it, 0L)
                else -> min(it, length)
            }
        }

        val count = max(final - k, 0)
        val array = Operations.arraySpeciesCreate(obj, count)
        val range = k until final

        obj.indexedProperties.indices().filter {
            it in range
        }.forEach {
            Operations.createDataPropertyOrThrow(array, it.key(), obj.get(it))
        }

        Operations.set(array as JSObject, "length".key(), (count - 1).toValue(), true)

        return array
    }

    fun some(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val (callback, thisArg) = arguments.takeArgs(0..1)

        if (!Operations.isCallable(callback))
            Errors.Array.CallableFirstArg("some").throwTypeError()

        obj.indexedProperties.indices().forEach {
            if (it >= length)
                return@forEach

            val value = obj.get(it)
            val testResult = Operations.toBoolean(Operations.call(callback, thisArg, listOf(value, it.toValue(), obj)))
            if (testResult)
                return JSTrue
        }

        return JSFalse
    }

    fun splice(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val (start, deleteCount) = arguments.takeArgs(0..1)

        val actualStart = Operations.toIntegerOrInfinity(start).let {
            when {
                it.isNegativeInfinity -> 0L
                it.asLong < 0L -> max(length + it.asLong, 0L)
                else -> min(it.asLong, length)
            }
        }

        val (insertCount, actualDeleteCount) = when {
            arguments.isEmpty() -> 0L to 0L
            arguments.size == 1 -> 0L to length - actualStart
            else -> {
                val dc = Operations.toIntegerOrInfinity(deleteCount).asLong.coerceIn(0L, length - actualStart)
                (arguments.size - 2L) to dc
            }
        }

        if (length + insertCount - actualDeleteCount > Operations.MAX_SAFE_INTEGER)
            Errors.Array.GrowToInvalidLength.throwTypeError()

        val array = Operations.arraySpeciesCreate(obj, actualDeleteCount) as JSObject
        val objIndices = obj.indexedProperties.indices()

        objIndices.filter {
            it in actualStart..(actualStart + actualDeleteCount)
        }.forEach {
            Operations.createDataPropertyOrThrow(array, (it - actualStart).key(), obj.get(it))
        }

        Operations.set(array, "length".key(), actualDeleteCount.toValue(), true)

        val itemCount = (arguments.size - 2).coerceAtLeast(0)
        if (itemCount < actualDeleteCount) {
            var k = actualStart
            while (k < length - actualDeleteCount) {
                val from = k + actualDeleteCount
                val to = k + itemCount
                if (Operations.hasProperty(obj, from.key())) {
                    Operations.set(obj, to.key(), obj.get(from.key()), true)
                } else {
                    Operations.deletePropertyOrThrow(obj, to.key())
                }
                k++
            }

            k = length
            while (k > (length - actualDeleteCount + itemCount)) {
                Operations.deletePropertyOrThrow(obj, (k - 1).key())
                k--
            }
        } else if (itemCount > actualDeleteCount) {
            var k = length - actualDeleteCount
            while (k > actualStart) {
                val from = k + actualDeleteCount - 1
                val to = k + itemCount - 1
                if (Operations.hasProperty(obj, from.key())) {
                    Operations.set(obj, to.key(), obj.get(from.key()), true)
                } else {
                    Operations.deletePropertyOrThrow(obj, to.key())
                }
                k--
            }
        }

        if (arguments.size > 2) {
            arguments.subList(2, arguments.size).forEachIndexed { index, item ->
                Operations.set(obj, (actualStart + index).key(), item, true)

            }
        }

        Operations.set(obj, "length".key(), (length - actualDeleteCount + itemCount).toValue(), true)
        return array
    }

    fun toString(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val func = obj.get("join")
        if (Operations.isCallable(func))
            return Operations.call(func, obj)
        return realm.objectProto.toString(thisValue, arguments)
    }

    fun unshift(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val argCount = arguments.size
        if (argCount > 0) {
            if (length + argCount >= Operations.MAX_SAFE_INTEGER)
                Errors.Array.GrowToInvalidLength.throwTypeError()
            // TODO: Batch this insertion, as it is quite an expensive operation
            arguments.reversed().forEach {
                obj.indexedProperties.insert(0, it)
            }
        }
        Operations.set(obj, "length".key(), (length + argCount).toValue(), true)
        return (length + argCount).toValue()
    }

    fun values(thisValue: JSValue, arguments: JSArguments): JSValue {
        val obj = Operations.toObject(thisValue)
        return createArrayIterator(realm, obj, PropertyKind.Value)
    }

    private fun flattenIntoArray(
        target: JSValue,
        source: JSValue,
        sourceLength: Long,
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

                val shouldFlatten = if (depth == null || depth > 0) Operations.isArray(element) else false
                if (shouldFlatten) {
                    val newDepth = if (depth == null) null else depth - 1
                    val elementLength = Operations.lengthOfArrayLike(element)
                    // TODO: Spec says we should pass mapperFunction? Is that true?
                    targetIndex = flattenIntoArray(target, element, elementLength, targetIndex, newDepth)
                } else {
                    if (targetIndex >= Operations.MAX_SAFE_INTEGER)
                        Errors.TODO("flattenIntoArray").throwTypeError()
                    Operations.createDataPropertyOrThrow(target, targetIndex.key(), element)
                    targetIndex++
                }
            }
            sourceIndex++
        }

        return targetIndex
    }

    private fun reduceHelper(thisValue: JSValue, arguments: JSArguments, isRight: Boolean): JSValue {
        val obj = Operations.toObject(thisValue)
        val length = Operations.lengthOfArrayLike(obj)
        val (callback, initialValue) = arguments.takeArgs(0..1)
        if (!Operations.isCallable(callback))
            Errors.Array.CallableFirstArg("reduceRight").throwTypeError()

        if (length == 0L && arguments.size == 1)
            Errors.Array.ReduceEmptyArray.throwTypeError()

        val indices = obj.indexedProperties.indices().let {
            if (isRight) it.reversed() else it
        }.toMutableList()

        var accumulator = if (arguments.size > 1) {
            initialValue
        } else {
            if (indices.isEmpty())
                Errors.Array.ReduceEmptyArray.throwTypeError()
            obj.get(indices.removeFirst())
        }

        indices.forEach {
            val value = obj.get(it)
            accumulator = Operations.call(callback, JSUndefined, listOf(accumulator, value, it.toValue(), obj))
        }

        return accumulator
    }

    companion object {
        fun create(realm: Realm) = JSArrayProto(realm).initialize()

        @ECMAImpl("22.1.5.1")
        private fun createArrayIterator(realm: Realm, array: JSObject, kind: PropertyKind): JSValue {
            return JSArrayIterator.create(realm, array, 0, kind)
        }
    }
}
