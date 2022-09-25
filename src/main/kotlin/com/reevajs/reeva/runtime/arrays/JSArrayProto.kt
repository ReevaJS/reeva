package com.reevajs.reeva.runtime.arrays

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObjectProto
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSNull
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.*
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

        val unscopables = create(realm, proto = JSNull)
        AOs.createDataPropertyOrThrow(unscopables, "at".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "copyWithin".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "entries".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "fill".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "find".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "findIndex".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "flat".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "flatMap".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "includes".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "keys".key(), JSTrue)
        AOs.createDataPropertyOrThrow(unscopables, "values".key(), JSTrue)
        defineOwnProperty(Realm.WellKnownSymbols.unscopables, unscopables, Descriptor.CONFIGURABLE)

        defineBuiltin("at", 1, ::at)
        defineBuiltin("concat", 1, ::concat)
        defineBuiltin("copyWithin", 2, ::copyWithin)
        defineBuiltin("entries", 0, ::entries)
        defineBuiltin("every", 1, ::every)
        defineBuiltin("fill", 1, ::fill)
        defineBuiltin("filter", 1, ::filter)
        defineBuiltin("find", 1, ::find)
        defineBuiltin("findIndex", 1, ::findIndex)
        defineBuiltin("flat", 0, ::flat)
        defineBuiltin("flatMap", 1, ::flatMap)
        defineBuiltin("forEach", 1, ::forEach)
        defineBuiltin("includes", 1, ::includes)
        defineBuiltin("indexOf", 1, ::indexOf)
        defineBuiltin("join", 1, ::join)
        defineBuiltin("keys", 1, ::keys)
        defineBuiltin("lastIndexOf", 1, ::lastIndexOf)
        defineBuiltin("map", 1, ::map)
        defineBuiltin("pop", 1, ::pop)
        defineBuiltin("push", 1, ::push)
        defineBuiltin("reduce", 1, ::reduce)
        defineBuiltin("reduceRight", 1, ::reduceRight)
        defineBuiltin("reverse", 1, ::reverse)
        defineBuiltin("shift", 1, ::shift)
        defineBuiltin("slice", 1, ::slice)
        defineBuiltin("some", 1, ::some)
        defineBuiltin("splice", 1, ::splice)
        defineBuiltin("toString", 1, ::toString)
        defineBuiltin("unshift", 1, ::unshift)
        defineBuiltin("values", 1, ::values)

        // "The initial values of the @@iterator property is the same function object as the initial
        // value of the Array.prototype.values property."
        // https://tc39.es/ecma262/#sec-array.prototype-@@iterator
        defineOwnProperty(Realm.WellKnownSymbols.iterator, internalGet("values".key())!!.getRawValue())
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayProto(realm).initialize()

        @JvmStatic
        fun at(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val len = AOs.lengthOfArrayLike(thisObj)
            val relativeIndex = arguments.argument(0).toIntegerOrInfinity()

            val k = if (relativeIndex.isPositiveInfinity || relativeIndex.asLong >= 0) {
                relativeIndex.asLong
            } else {
                len + relativeIndex.asLong
            }

            if (k < 0 || k >= len)
                return JSUndefined

            return thisObj.get(k)
        }

        @ECMAImpl("23.1.3.1")
        @JvmStatic
        fun concat(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val array = AOs.arraySpeciesCreate(thisObj, 0)

            val items = listOf(thisObj) + arguments
            var n = 0L
            items.forEach { item ->
                val isConcatSpreadable = if (item !is JSObject) {
                    false
                } else {
                    val isSpreadable = item.get(Realm.WellKnownSymbols.isConcatSpreadable)
                    if (isSpreadable != JSUndefined) {
                        isSpreadable.toBoolean()
                    } else AOs.isArray(item)
                }
                if (isConcatSpreadable) {
                    val length = AOs.lengthOfArrayLike(item)
                    if (length == 0L)
                        return@forEach
                    if (length + n > AOs.MAX_SAFE_INTEGER)
                        Errors.InvalidArrayLength(length + n).throwTypeError()

                    val indices = AOs.objectIndices(item as JSObject)

                    for (index in indices) {
                        if (index >= length)
                            break
                        AOs.createDataPropertyOrThrow(array, (n + index).key(), item.get(index))
                    }

                    n += length
                } else {
                    AOs.createDataPropertyOrThrow(array, n.key(), item)
                    n++
                }
            }
            AOs.set(array as JSObject, "length".key(), n.toValue(), true)
            return array
        }

        @ECMAImpl("23.1.3.3")
        @JvmStatic
        fun copyWithin(arguments: JSArguments): JSValue {
            val (target, start, end) = arguments.takeArgs(0..2)

            val thisObj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(thisObj)

            var to = AOs.mapWrappedArrayIndex(target.toIntegerOrInfinity(), length)
            var from = AOs.mapWrappedArrayIndex(start.toIntegerOrInfinity(), length)

            val relativeEnd = if (end == JSUndefined) {
                length.toValue()
            } else end.toIntegerOrInfinity()
            val final = AOs.mapWrappedArrayIndex(relativeEnd, length)

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
                } else if (!AOs.deletePropertyOrThrow(thisObj, to.key())) {
                    return INVALID_VALUE
                }
                from += direction
                to += direction
                count -= 1
            }
            return thisObj
        }

        @ECMAImpl("23.1.3.4")
        @JvmStatic
        fun entries(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            return AOs.createArrayIterator(obj, PropertyKind.KeyValue)
        }

        @ECMAImpl("23.1.3.5")
        @JvmStatic
        fun every(arguments: JSArguments): JSValue {
            return genericArrayEvery(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.6")
        @JvmStatic
        fun fill(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()

            val length = AOs.lengthOfArrayLike(thisObj)

            val (value, start, end) = arguments.takeArgs(0..2)
            var startIndex = AOs.mapWrappedArrayIndex(start.toIntegerOrInfinity(), length)

            val relativeEnd = if (end == JSUndefined) {
                length.toValue()
            } else end.toIntegerOrInfinity()

            val endIndex = AOs.mapWrappedArrayIndex(relativeEnd, length)

            while (startIndex < endIndex) {
                thisObj.set(startIndex, value)
                startIndex++
            }

            return thisObj
        }

        @ECMAImpl("23.1.3.7")
        @JvmStatic
        fun filter(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(thisObj)

            val (callback, thisArg) = arguments.takeArgs(0..2)
            if (!AOs.isCallable(callback))
                Errors.Array.CallableFirstArg("filter").throwTypeError()

            val array = AOs.arraySpeciesCreate(thisObj, 0)
            var toIndex = 0

            for (index in AOs.objectIndices(thisObj)) {
                if (index >= length)
                    break

                val value = thisObj.get(index)
                val callbackResult = AOs.call(callback, thisArg, listOf(value, index.toValue(), thisObj))
                val booleanResult = callbackResult.toBoolean()
                if (booleanResult) {
                    if (!AOs.createDataPropertyOrThrow(array, toIndex.key(), value))
                        return INVALID_VALUE
                    toIndex++
                }
            }

            return array
        }

        @ECMAImpl("23.1.3.8")
        @JvmStatic
        fun find(arguments: JSArguments): JSValue {
            return genericArrayFind(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.9")
        @JvmStatic
        fun findIndex(arguments: JSArguments): JSValue {
            return genericArrayFindIndex(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.10")
        @JvmStatic
        fun flat(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val sourceLength = AOs.lengthOfArrayLike(thisObj)
            val depth = if (arguments.isNotEmpty()) {
                arguments[0].toIntegerOrInfinity().asInt.coerceAtLeast(0)
            } else 1

            val array = AOs.arraySpeciesCreate(thisObj, 0)
            flattenIntoArray(array, thisObj, sourceLength, 0, depth)
            return array
        }

        @ECMAImpl("23.1.3.11")
        @JvmStatic
        fun flatMap(arguments: JSArguments): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val sourceLength = AOs.lengthOfArrayLike(thisObj)

            val (mapperFunction, thisArg) = arguments.takeArgs(0..1)
            if (!AOs.isCallable(mapperFunction))
                Errors.Array.CallableFirstArg("flatMap").throwTypeError()

            val array = AOs.arraySpeciesCreate(thisObj, 0)
            flattenIntoArray(array, thisObj, sourceLength, 0, 1, mapperFunction, thisArg)
            return array
        }

        @ECMAImpl("23.1.3.12")
        @JvmStatic
        fun forEach(arguments: JSArguments): JSValue {
            return genericArrayForEach(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.13")
        @JvmStatic
        fun includes(arguments: JSArguments): JSValue {
            return genericArrayIncludes(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.14")
        @JvmStatic
        fun indexOf(arguments: JSArguments): JSValue {
            return genericArrayIndexOf(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.15")
        @JvmStatic
        fun join(arguments: JSArguments): JSValue {
            return genericArrayJoin(arguments, AOs::lengthOfArrayLike)
        }

        @ECMAImpl("23.1.3.16")
        @JvmStatic
        fun keys(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            return AOs.createArrayIterator(obj, PropertyKind.Key)
        }

        @ECMAImpl("23.1.3.17")
        @JvmStatic
        fun lastIndexOf(arguments: JSArguments): JSValue {
            return genericArrayLastIndexOf(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.18")
        @JvmStatic
        fun map(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(obj)
            val (callback, thisArg) = arguments.takeArgs(0..1)
            if (!AOs.isCallable(callback))
                Errors.Array.CallableFirstArg("map").throwTypeError()

            val array = AOs.arraySpeciesCreate(obj, length)
            AOs.objectIndices(obj).filter {
                it < length
            }.forEach {
                val value = obj.get(it)
                val mappedValue = AOs.call(callback, thisArg, listOf(value, it.toValue(), obj))
                AOs.createDataPropertyOrThrow(array, it.key(), mappedValue)
            }

            return array
        }

        @ECMAImpl("23.1.3.19")
        @JvmStatic
        fun pop(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(obj)
            if (length == 0L)
                return JSUndefined

            val element = obj.get(length - 1)
            AOs.deletePropertyOrThrow(obj, (length - 1).key())
            obj.set("length", (length - 1).toValue())
            return element
        }

        @ECMAImpl("23.1.3.20")
        @JvmStatic
        fun push(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            var length = AOs.lengthOfArrayLike(obj)
            if (length + arguments.size >= AOs.MAX_SAFE_INTEGER)
                Errors.Array.GrowToInvalidLength.throwTypeError()
            arguments.forEach {
                AOs.set(obj, length.key(), it, true)
                length++
            }
            AOs.set(obj, "length".key(), length.toValue(), true)
            return length.toValue()
        }

        @ECMAImpl("23.1.3.21")
        @JvmStatic
        fun reduce(arguments: JSArguments): JSValue {
            return genericArrayReduce(arguments, AOs::lengthOfArrayLike, AOs::objectIndices, false)
        }

        @ECMAImpl("23.1.3.22")
        @JvmStatic
        fun reduceRight(arguments: JSArguments): JSValue {
            return genericArrayReduce(arguments, AOs::lengthOfArrayLike, AOs::objectIndices, true)
        }

        @ECMAImpl("23.1.3.23")
        @JvmStatic
        fun reverse(arguments: JSArguments): JSValue {
            return genericArrayReverse(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.24")
        @JvmStatic
        fun shift(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(obj)
            if (length == 0L)
                return JSUndefined

            val element = obj.indexedProperties.removeFirst(obj)
            AOs.set(obj, "length".key(), (length - 1).toValue(), true)
            return element.getActualValue(obj)
        }

        @ECMAImpl("23.1.3.25")
        @JvmStatic
        fun slice(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(obj)
            val (start, end) = arguments.takeArgs(0..1)

            val k = AOs.mapWrappedArrayIndex(start.toIntegerOrInfinity(), length)
            val relativeEnd = if (end == JSUndefined) {
                length.toValue()
            } else end.toIntegerOrInfinity()

            val final = AOs.mapWrappedArrayIndex(relativeEnd, length)

            val count = max(final - k, 0)
            val array = AOs.arraySpeciesCreate(obj, count)
            val range = k until final

            AOs.objectIndices(obj).filter {
                it in range
            }.forEach {
                AOs.createDataPropertyOrThrow(array, it.key(), obj.get(it))
            }

            AOs.set(array as JSObject, "length".key(), (count - 1).toValue(), true)

            return array
        }

        @ECMAImpl("23.1.3.26")
        @JvmStatic
        fun some(arguments: JSArguments): JSValue {
            return genericArraySome(arguments, AOs::lengthOfArrayLike, AOs::objectIndices)
        }

        @ECMAImpl("23.1.3.28")
        @JvmStatic
        fun splice(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(obj)
            val (start, deleteCount) = arguments.takeArgs(0..1)

            val actualStart = AOs.mapWrappedArrayIndex(start.toIntegerOrInfinity(), length)

            val (insertCount, actualDeleteCount) = when {
                arguments.isEmpty() -> 0L to 0L
                arguments.size == 1 -> 0L to length - actualStart
                else -> {
                    val dc = deleteCount.toIntegerOrInfinity().asLong.coerceIn(0L, length - actualStart)
                    (arguments.size - 2L) to dc
                }
            }

            if (length + insertCount - actualDeleteCount > AOs.MAX_SAFE_INTEGER)
                Errors.Array.GrowToInvalidLength.throwTypeError()

            val array = AOs.arraySpeciesCreate(obj, actualDeleteCount) as JSObject
            val objIndices = AOs.objectIndices(obj)

            objIndices.filter {
                it in actualStart..(actualStart + actualDeleteCount)
            }.forEach {
                AOs.createDataPropertyOrThrow(array, (it - actualStart).key(), obj.get(it))
            }

            AOs.set(array, "length".key(), actualDeleteCount.toValue(), true)

            val itemCount = (arguments.size - 2).coerceAtLeast(0)
            if (itemCount < actualDeleteCount) {
                var k = actualStart
                while (k < length - actualDeleteCount) {
                    val from = k + actualDeleteCount
                    val to = k + itemCount
                    if (AOs.hasProperty(obj, from.key())) {
                        AOs.set(obj, to.key(), obj.get(from.key()), true)
                    } else {
                        AOs.deletePropertyOrThrow(obj, to.key())
                    }
                    k++
                }

                k = length
                while (k > (length - actualDeleteCount + itemCount)) {
                    AOs.deletePropertyOrThrow(obj, (k - 1).key())
                    k--
                }
            } else if (itemCount > actualDeleteCount) {
                var k = length - actualDeleteCount
                while (k > actualStart) {
                    val from = k + actualDeleteCount - 1
                    val to = k + itemCount - 1
                    if (AOs.hasProperty(obj, from.key())) {
                        AOs.set(obj, to.key(), obj.get(from.key()), true)
                    } else {
                        AOs.deletePropertyOrThrow(obj, to.key())
                    }
                    k--
                }
            }

            if (arguments.size > 2) {
                arguments.subList(2, arguments.size).forEachIndexed { index, item ->
                    AOs.set(obj, (actualStart + index).key(), item, true)
                }
            }

            AOs.set(obj, "length".key(), (length - actualDeleteCount + itemCount).toValue(), true)
            return array
        }

        @ECMAImpl("23.1.3.30")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val func = obj.get("join")
            if (AOs.isCallable(func))
                return AOs.call(func, obj)
            return JSObjectProto.toString(arguments)
        }

        @ECMAImpl("23.1.3.31")
        @JvmStatic
        fun unshift(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = AOs.lengthOfArrayLike(obj)
            val argCount = arguments.size
            if (argCount > 0) {
                if (length + argCount >= AOs.MAX_SAFE_INTEGER)
                    Errors.Array.GrowToInvalidLength.throwTypeError()
                // TODO: Batch this insertion, as it is quite an expensive operation
                arguments.reversed().forEach {
                    obj.indexedProperties.insert(0, it)
                }
            }
            AOs.set(obj, "length".key(), (length + argCount).toValue(), true)
            return (length + argCount).toValue()
        }

        @ECMAImpl("23.1.3.32")
        @JvmStatic
        fun values(arguments: JSArguments): JSValue {
            val obj = arguments.thisValue.toObject()
            return AOs.createArrayIterator(obj, PropertyKind.Value)
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
                        element = AOs.call(
                            mapperFunction,
                            thisArg!!,
                            listOf(element, sourceIndex.toValue(), source)
                        )

                    val shouldFlatten = if (depth == null || depth > 0) AOs.isArray(element) else false
                    if (shouldFlatten) {
                        val newDepth = if (depth == null) null else depth - 1
                        val elementLength = AOs.lengthOfArrayLike(element)
                        // TODO: Spec says we should pass mapperFunction? Is that true?
                        targetIndex = flattenIntoArray(target, element, elementLength, targetIndex, newDepth)
                    } else {
                        if (targetIndex >= AOs.MAX_SAFE_INTEGER)
                            Errors.TODO("flattenIntoArray").throwTypeError()
                        AOs.createDataPropertyOrThrow(target, targetIndex.key(), element)
                        targetIndex++
                    }
                }
                sourceIndex++
            }

            return targetIndex
        }

        fun genericArrayEvery(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val (callback, thisArg) = arguments.takeArgs(0..1)

            if (!AOs.isCallable(callback))
                Errors.Array.CallableFirstArg("every").throwTypeError()

            val thisObj = arguments.thisValue.toObject()
            val length = lengthProducer(thisObj)

            for (index in indicesProducer(thisObj)) {
                if (index >= length)
                    break

                val value = thisObj.get(index)
                val testResult = AOs.call(callback, thisArg, listOf(value, index.toValue(), thisObj))
                val testBoolean = testResult.toBoolean()
                if (!testBoolean)
                    return JSFalse
            }

            return JSTrue
        }

        fun genericArrayFind(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val length = lengthProducer(thisObj)
            val (predicate, thisArg) = arguments.takeArgs(0..1)

            if (!AOs.isCallable(predicate))
                Errors.Array.CallableFirstArg("find").throwTypeError()

            for (index in indicesProducer(thisObj)) {
                if (index >= length)
                    break

                val value = thisObj.get(index)
                val testResult = AOs.call(predicate, thisArg, listOf(value, index.toValue(), thisObj))
                val booleanResult = testResult.toBoolean()
                if (booleanResult)
                    return value
            }

            return JSUndefined
        }

        fun genericArrayFindIndex(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val thisObj = arguments.thisValue.toObject()
            val length = lengthProducer(thisObj)
            val (predicate, thisArg) = arguments.takeArgs(0..1)

            if (!AOs.isCallable(predicate))
                Errors.Array.CallableFirstArg("findIndex").throwTypeError()

            for (index in indicesProducer(thisObj)) {
                if (index >= length)
                    break

                val value = thisObj.get(index)
                val testResult = AOs.call(predicate, thisArg, listOf(value, index.toValue(), thisObj))
                val booleanResult = testResult.toBoolean()
                if (booleanResult)
                    return index.toValue()
            }

            return JSUndefined
        }

        fun genericArrayForEach(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)

            val callbackFn = arguments.argument(0)
            if (!AOs.isCallable(callbackFn))
                Errors.Array.CallableFirstArg("forEach").throwTypeError()

            for (index in indicesProducer(obj)) {
                if (index >= length)
                    break

                val value = obj.get(index)
                AOs.call(callbackFn, arguments.argument(1), listOf(value, index.toValue(), obj))
            }

            return JSUndefined
        }

        fun genericArrayIncludes(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)

            if (length == 0L)
                return JSFalse

            val (searchElement, fromIndex) = arguments.takeArgs(0..1)
            val k = fromIndex.toIntegerOrInfinity().let {
                when {
                    it.isPositiveInfinity -> return JSFalse
                    it.isNegativeInfinity -> 0
                    it.asLong < 0 -> max(0, length + it.asLong)
                    else -> it.asLong
                }
            }

            val indices = indicesProducer(obj).filter { it in k..length }
            if (searchElement == JSUndefined)
                return (indices.count().toLong() != length).toValue()

            return indices.map(obj::get).any {
                searchElement.sameValueZero(it)
            }.toValue()
        }

        fun genericArrayJoin(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)
            val sep = if (arguments.isEmpty()) "," else arguments[0].toJSString().string

            return buildString {
                for (i in 0 until length) {
                    if (i > 0)
                        append(sep)
                    val element = obj.get(i)
                    if (!element.isNullish)
                        append(element.toJSString().string)
                }
            }.toValue()
        }

        fun genericArrayIndexOf(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)
            if (length == 0L)
                return (-1).toValue()

            val (searchElement, fromIndex) = arguments.takeArgs(0..1)
            val n = fromIndex.toIntegerOrInfinity().let {
                when {
                    it.isPositiveInfinity -> return (-1).toValue()
                    it.isNegativeInfinity -> 0L
                    else -> it.asLong
                }
            }

            val k = if (n >= 0L) n else length + n

            indicesProducer(obj).filter {
                it >= k
            }.forEach {
                val value = obj.get(it)
                if (AOs.isLooselyEqual(searchElement, value) == JSTrue)
                    return it.toValue()
            }

            return (-1).toValue()
        }

        fun genericArrayLastIndexOf(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)
            if (length == 0L)
                return (-1).toValue()

            val (searchElement, fromIndex) = arguments.takeArgs(0..1)
            val fromNumber = if (fromIndex == JSUndefined) {
                length - 1L
            } else fromIndex.toIntegerOrInfinity().let {
                if (it.isNegativeInfinity)
                    return (-1).toValue()
                it.asLong
            }

            val limit = if (fromNumber >= 0L) {
                min(fromNumber, length - 1L)
            } else length + fromNumber

            // TODO: Don't collect all indices to call .asReversed()
            indicesProducer(obj).filter {
                it <= limit
            }.toList().asReversed().forEach {
                val value = obj.get(it)
                if (AOs.isLooselyEqual(searchElement, value) == JSTrue)
                    return it.toValue()
            }

            return (-1).toValue()
        }

        fun genericArrayReduce(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>,
            isRight: Boolean
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)
            val (callback, initialValue) = arguments.takeArgs(0..1)
            if (!AOs.isCallable(callback))
                Errors.Array.CallableFirstArg("reduceRight").throwTypeError()

            if (length == 0L && arguments.size == 1)
                Errors.Array.ReduceEmptyArray.throwTypeError()

            // TODO: Don't collect all indices to call .asReversed()
            val indices = indicesProducer(obj).toList().let {
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
                accumulator =
                    AOs.call(callback, JSUndefined, listOf(accumulator, value, it.toValue(), obj))
            }

            return accumulator
        }

        fun genericArrayReverse(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)
            val middle = floor(length / 2.0)
            val indices = indicesProducer(obj)

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
                    AOs.set(obj, upperIndex.key(), lowerVal, true)
                    AOs.set(obj, lowerIndex.key(), upperVal, true)
                    upperIndices.remove(upperIndex)
                } else {
                    AOs.set(obj, upperIndex.key(), lowerVal, true)
                    AOs.deletePropertyOrThrow(obj, lowerIndex.key())
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
                    AOs.set(obj, lowerIndex.key(), upperVal, true)
                    AOs.set(obj, upperIndex.key(), lowerVal, true)
                    lowerIndices.remove(lowerIndex)
                } else {
                    AOs.set(obj, lowerIndex.key(), upperVal, true)
                    AOs.deletePropertyOrThrow(obj, upperIndex.key())
                }

                upperIt.remove()
            }

            expect(lowerIndices.isEmpty() && upperIndices.isEmpty())

            return obj
        }

        fun genericArraySome(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = arguments.thisValue.toObject()
            val length = lengthProducer(obj)
            val (callback, thisArg) = arguments.takeArgs(0..1)

            if (!AOs.isCallable(callback))
                Errors.Array.CallableFirstArg("some").throwTypeError()

            indicesProducer(obj).forEach {
                if (it >= length)
                    return@forEach

                val value = obj.get(it)
                val testResult = AOs.call(callback, thisArg, listOf(value, it.toValue(), obj)).toBoolean()
                if (testResult)
                    return JSTrue
            }

            return JSFalse
        }
    }
}
