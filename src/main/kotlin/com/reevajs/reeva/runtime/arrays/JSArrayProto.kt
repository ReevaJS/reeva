package com.reevajs.reeva.runtime.arrays

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
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

        val unscopables = create(proto = JSNull)
        Operations.createDataPropertyOrThrow(unscopables, "at".key(), JSTrue)
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
        defineOwnProperty(Realm.WellKnownSymbols.unscopables, unscopables, Descriptor.CONFIGURABLE)

        defineBuiltin("at", 1, ReevaBuiltin.ArrayProtoAt)
        defineBuiltin("concat", 1, ReevaBuiltin.ArrayProtoConcat)
        defineBuiltin("copyWithin", 2, ReevaBuiltin.ArrayProtoCopyWithin)
        defineBuiltin("entries", 0, ReevaBuiltin.ArrayProtoEntries)
        defineBuiltin("every", 1, ReevaBuiltin.ArrayProtoEvery)
        defineBuiltin("fill", 1, ReevaBuiltin.ArrayProtoFill)
        defineBuiltin("filter", 1, ReevaBuiltin.ArrayProtoFilter)
        defineBuiltin("find", 1, ReevaBuiltin.ArrayProtoFind)
        defineBuiltin("findIndex", 1, ReevaBuiltin.ArrayProtoFindIndex)
        defineBuiltin("flat", 0, ReevaBuiltin.ArrayProtoFlat)
        defineBuiltin("flatMap", 1, ReevaBuiltin.ArrayProtoFlatMap)
        defineBuiltin("forEach", 1, ReevaBuiltin.ArrayProtoForEach)
        defineBuiltin("includes", 1, ReevaBuiltin.ArrayProtoIncludes)
        defineBuiltin("indexOf", 1, ReevaBuiltin.ArrayProtoIndexOf)
        defineBuiltin("join", 1, ReevaBuiltin.ArrayProtoJoin)
        defineBuiltin("keys", 1, ReevaBuiltin.ArrayProtoKeys)
        defineBuiltin("lastIndexOf", 1, ReevaBuiltin.ArrayProtoLastIndexOf)
        defineBuiltin("map", 1, ReevaBuiltin.ArrayProtoMap)
        defineBuiltin("pop", 1, ReevaBuiltin.ArrayProtoPop)
        defineBuiltin("push", 1, ReevaBuiltin.ArrayProtoPush)
        defineBuiltin("reduce", 1, ReevaBuiltin.ArrayProtoReduce)
        defineBuiltin("reduceRight", 1, ReevaBuiltin.ArrayProtoReduceRight)
        defineBuiltin("reverse", 1, ReevaBuiltin.ArrayProtoReverse)
        defineBuiltin("shift", 1, ReevaBuiltin.ArrayProtoShift)
        defineBuiltin("slice", 1, ReevaBuiltin.ArrayProtoSlice)
        defineBuiltin("some", 1, ReevaBuiltin.ArrayProtoSome)
        defineBuiltin("splice", 1, ReevaBuiltin.ArrayProtoSplice)
        defineBuiltin("toString", 1, ReevaBuiltin.ArrayProtoToString)
        defineBuiltin("unshift", 1, ReevaBuiltin.ArrayProtoUnshift)
        defineBuiltin("values", 1, ReevaBuiltin.ArrayProtoValues)

        // "The initial values of the @@iterator property is the same function object as the initial
        // value of the Array.prototype.values property."
        // https://tc39.es/ecma262/#sec-array.prototype-@@iterator
        defineOwnProperty(Realm.WellKnownSymbols.iterator, internalGet("values".key())!!.getRawValue())
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSArrayProto(realm).initialize()

        @JvmStatic
        fun at(arguments: JSArguments): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)
            val len = Operations.lengthOfArrayLike(thisObj)
            val relativeIndex = Operations.toIntegerOrInfinity(arguments.argument(0))

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
            val thisObj = Operations.toObject(arguments.thisValue)
            val array = Operations.arraySpeciesCreate(thisObj, 0)

            val items = listOf(thisObj) + arguments
            var n = 0L
            items.forEach { item ->
                val isConcatSpreadable = if (item !is JSObject) {
                    false
                } else {
                    val isSpreadable = item.get(Realm.WellKnownSymbols.isConcatSpreadable)
                    if (isSpreadable != JSUndefined) {
                        Operations.toBoolean(isSpreadable)
                    } else Operations.isArray(item)
                }
                if (isConcatSpreadable) {
                    val length = Operations.lengthOfArrayLike(item)
                    if (length == 0L)
                        return@forEach
                    if (length + n > Operations.MAX_SAFE_INTEGER)
                        Errors.InvalidArrayLength(length + n).throwTypeError()

                    val indices = Operations.objectIndices(item as JSObject)

                    for (index in indices) {
                        if (index >= length)
                            break
                        Operations.createDataPropertyOrThrow(array, (n + index).key(), item.get(index))
                    }

                    n += length
                } else {
                    Operations.createDataPropertyOrThrow(array, n.key(), item)
                    n++
                }
            }
            Operations.set(array as JSObject, "length".key(), n.toValue(), true)
            return array
        }

        @ECMAImpl("23.1.3.3")
        @JvmStatic
        fun copyWithin(arguments: JSArguments): JSValue {
            val (target, start, end) = arguments.takeArgs(0..2)

            val thisObj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(thisObj)

            var to = Operations.mapWrappedArrayIndex(Operations.toIntegerOrInfinity(target), length)
            var from = Operations.mapWrappedArrayIndex(Operations.toIntegerOrInfinity(start), length)

            val relativeEnd = if (end == JSUndefined) {
                length.toValue()
            } else Operations.toIntegerOrInfinity(end)
            val final = Operations.mapWrappedArrayIndex(relativeEnd, length)

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

        @ECMAImpl("23.1.3.4")
        @JvmStatic
        fun entries(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            return Operations.createArrayIterator(obj, PropertyKind.KeyValue)
        }

        @ECMAImpl("23.1.3.5")
        @JvmStatic
        fun every(arguments: JSArguments): JSValue {
            return genericArrayEvery(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.6")
        @JvmStatic
        fun fill(arguments: JSArguments): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)

            val length = Operations.lengthOfArrayLike(thisObj)

            val (value, start, end) = arguments.takeArgs(0..2)
            var startIndex = Operations.mapWrappedArrayIndex(Operations.toIntegerOrInfinity(start), length)

            val relativeEnd = if (end == JSUndefined) {
                length.toValue()
            } else Operations.toIntegerOrInfinity(end)

            val endIndex = Operations.mapWrappedArrayIndex(relativeEnd, length)

            while (startIndex < endIndex) {
                thisObj.set(startIndex, value)
                startIndex++
            }

            return thisObj
        }

        @ECMAImpl("23.1.3.7")
        @JvmStatic
        fun filter(arguments: JSArguments): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(thisObj)

            val (callback, thisArg) = arguments.takeArgs(0..2)
            if (!Operations.isCallable(callback))
                Errors.Array.CallableFirstArg("filter").throwTypeError()

            val array = Operations.arraySpeciesCreate(thisObj, 0)
            var toIndex = 0

            for (index in Operations.objectIndices(thisObj)) {
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

        @ECMAImpl("23.1.3.8")
        @JvmStatic
        fun find(arguments: JSArguments): JSValue {
            return genericArrayFind(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.9")
        @JvmStatic
        fun findIndex(arguments: JSArguments): JSValue {
            return genericArrayFindIndex(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.10")
        @JvmStatic
        fun flat(arguments: JSArguments): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)
            val sourceLength = Operations.lengthOfArrayLike(thisObj)
            val depth = if (arguments.isNotEmpty()) {
                Operations.toIntegerOrInfinity(arguments[0]).asInt.coerceAtLeast(0)
            } else 1

            val array = Operations.arraySpeciesCreate(thisObj, 0)
            flattenIntoArray(array, thisObj, sourceLength, 0, depth)
            return array
        }

        @ECMAImpl("23.1.3.11")
        @JvmStatic
        fun flatMap(arguments: JSArguments): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)
            val sourceLength = Operations.lengthOfArrayLike(thisObj)

            val (mapperFunction, thisArg) = arguments.takeArgs(0..1)
            if (!Operations.isCallable(mapperFunction))
                Errors.Array.CallableFirstArg("flatMap").throwTypeError()

            val array = Operations.arraySpeciesCreate(thisObj, 0)
            flattenIntoArray(array, thisObj, sourceLength, 0, 1, mapperFunction, thisArg)
            return array
        }

        @ECMAImpl("23.1.3.12")
        @JvmStatic
        fun forEach(arguments: JSArguments): JSValue {
            return genericArrayForEach(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.13")
        @JvmStatic
        fun includes(arguments: JSArguments): JSValue {
            return genericArrayIncludes(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.14")
        @JvmStatic
        fun indexOf(arguments: JSArguments): JSValue {
            return genericArrayIndexOf(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.15")
        @JvmStatic
        fun join(arguments: JSArguments): JSValue {
            return genericArrayJoin(arguments, Operations::lengthOfArrayLike)
        }

        @ECMAImpl("23.1.3.16")
        @JvmStatic
        fun keys(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            return Operations.createArrayIterator(obj, PropertyKind.Key)
        }

        @ECMAImpl("23.1.3.17")
        @JvmStatic
        fun lastIndexOf(arguments: JSArguments): JSValue {
            return genericArrayLastIndexOf(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.18")
        @JvmStatic
        fun map(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(obj)
            val (callback, thisArg) = arguments.takeArgs(0..1)
            if (!Operations.isCallable(callback))
                Errors.Array.CallableFirstArg("map").throwTypeError()

            val array = Operations.arraySpeciesCreate(obj, length)
            Operations.objectIndices(obj).filter {
                it < length
            }.forEach {
                val value = obj.get(it)
                val mappedValue = Operations.call(callback, thisArg, listOf(value, it.toValue(), obj))
                Operations.createDataPropertyOrThrow(array, it.key(), mappedValue)
            }

            return array
        }

        @ECMAImpl("23.1.3.19")
        @JvmStatic
        fun pop(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(obj)
            if (length == 0L)
                return JSUndefined

            val element = obj.get(length - 1)
            Operations.deletePropertyOrThrow(obj, (length - 1).key())
            obj.set("length", (length - 1).toValue())
            return element
        }

        @ECMAImpl("23.1.3.20")
        @JvmStatic
        fun push(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
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

        @ECMAImpl("23.1.3.21")
        @JvmStatic
        fun reduce(arguments: JSArguments): JSValue {
            return genericArrayReduce(arguments, Operations::lengthOfArrayLike, Operations::objectIndices, false)
        }

        @ECMAImpl("23.1.3.22")
        @JvmStatic
        fun reduceRight(arguments: JSArguments): JSValue {
            return genericArrayReduce(arguments, Operations::lengthOfArrayLike, Operations::objectIndices, true)
        }

        @ECMAImpl("23.1.3.23")
        @JvmStatic
        fun reverse(arguments: JSArguments): JSValue {
            return genericArrayReverse(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.24")
        @JvmStatic
        fun shift(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(obj)
            if (length == 0L)
                return JSUndefined

            val element = obj.indexedProperties.removeFirst(obj)
            Operations.set(obj, "length".key(), (length - 1).toValue(), true)
            return element.getActualValue(obj)
        }

        @ECMAImpl("23.1.3.25")
        @JvmStatic
        fun slice(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(obj)
            val (start, end) = arguments.takeArgs(0..1)

            val k = Operations.mapWrappedArrayIndex(start.toIntegerOrInfinity(), length)
            val relativeEnd = if (end == JSUndefined) {
                length.toValue()
            } else Operations.toIntegerOrInfinity(end)

            val final = Operations.mapWrappedArrayIndex(relativeEnd, length)

            val count = max(final - k, 0)
            val array = Operations.arraySpeciesCreate(obj, count)
            val range = k until final

            Operations.objectIndices(obj).filter {
                it in range
            }.forEach {
                Operations.createDataPropertyOrThrow(array, it.key(), obj.get(it))
            }

            Operations.set(array as JSObject, "length".key(), (count - 1).toValue(), true)

            return array
        }

        @ECMAImpl("23.1.3.26")
        @JvmStatic
        fun some(arguments: JSArguments): JSValue {
            return genericArraySome(arguments, Operations::lengthOfArrayLike, Operations::objectIndices)
        }

        @ECMAImpl("23.1.3.28")
        @JvmStatic
        fun splice(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = Operations.lengthOfArrayLike(obj)
            val (start, deleteCount) = arguments.takeArgs(0..1)

            val actualStart = Operations.mapWrappedArrayIndex(start.toIntegerOrInfinity(), length)

            val (insertCount, actualDeleteCount) = when {
                arguments.isEmpty() -> 0L to 0L
                arguments.size == 1 -> 0L to length - actualStart
                else -> {
                    val dc =
                        Operations.toIntegerOrInfinity(deleteCount).asLong.coerceIn(0L, length - actualStart)
                    (arguments.size - 2L) to dc
                }
            }

            if (length + insertCount - actualDeleteCount > Operations.MAX_SAFE_INTEGER)
                Errors.Array.GrowToInvalidLength.throwTypeError()

            val array = Operations.arraySpeciesCreate(obj, actualDeleteCount) as JSObject
            val objIndices = Operations.objectIndices(obj)

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

        @ECMAImpl("23.1.3.30")
        @JvmStatic
        fun toString(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val func = obj.get("join")
            if (Operations.isCallable(func))
                return Operations.call(func, obj)
            return JSObjectProto.toString(arguments)
        }

        @ECMAImpl("23.1.3.31")
        @JvmStatic
        fun unshift(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
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

        @ECMAImpl("23.1.3.32")
        @JvmStatic
        fun values(arguments: JSArguments): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            return Operations.createArrayIterator(obj, PropertyKind.Value)
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
                        element = Operations.call(
                            mapperFunction,
                            thisArg!!,
                            listOf(element, sourceIndex.toValue(), source)
                        )

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

        fun genericArrayEvery(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val (callback, thisArg) = arguments.takeArgs(0..1)

            if (!Operations.isCallable(callback))
                Errors.Array.CallableFirstArg("every").throwTypeError()

            val thisObj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(thisObj)

            for (index in indicesProducer(thisObj)) {
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

        fun genericArrayFind(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(thisObj)
            val (predicate, thisArg) = arguments.takeArgs(0..1)

            if (!Operations.isCallable(predicate))
                Errors.Array.CallableFirstArg("find").throwTypeError()

            for (index in indicesProducer(thisObj)) {
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

        fun genericArrayFindIndex(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val thisObj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(thisObj)
            val (predicate, thisArg) = arguments.takeArgs(0..1)

            if (!Operations.isCallable(predicate))
                Errors.Array.CallableFirstArg("findIndex").throwTypeError()

            for (index in indicesProducer(thisObj)) {
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

        fun genericArrayForEach(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(obj)

            val callbackFn = arguments.argument(0)
            if (!Operations.isCallable(callbackFn))
                Errors.Array.CallableFirstArg("forEach").throwTypeError()

            for (index in indicesProducer(obj)) {
                if (index >= length)
                    break

                val value = obj.get(index)
                Operations.call(callbackFn, arguments.argument(1), listOf(value, index.toValue(), obj))
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
            val obj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(obj)
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

        fun genericArrayIndexOf(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(obj)
            if (length == 0L)
                return (-1).toValue()

            val (searchElement, fromIndex) = arguments.takeArgs(0..1)
            val n = Operations.toIntegerOrInfinity(fromIndex).let {
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
                if (Operations.isLooselyEqual(searchElement, value) == JSTrue)
                    return it.toValue()
            }

            return (-1).toValue()
        }

        fun genericArrayLastIndexOf(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(obj)
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

            // TODO: Don't collect all indices to call .asReversed()
            indicesProducer(obj).filter {
                it <= limit
            }.toList().asReversed().forEach {
                val value = obj.get(it)
                if (Operations.isLooselyEqual(searchElement, value) == JSTrue)
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
            val obj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(obj)
            val (callback, initialValue) = arguments.takeArgs(0..1)
            if (!Operations.isCallable(callback))
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
                    Operations.call(callback, JSUndefined, listOf(accumulator, value, it.toValue(), obj))
            }

            return accumulator
        }

        fun genericArrayReverse(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
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

        fun genericArraySome(
            arguments: JSArguments,
            lengthProducer: (obj: JSObject) -> Long,
            indicesProducer: (obj: JSObject) -> Sequence<Long>
        ): JSValue {
            val obj = Operations.toObject(arguments.thisValue)
            val length = lengthProducer(obj)
            val (callback, thisArg) = arguments.takeArgs(0..1)

            if (!Operations.isCallable(callback))
                Errors.Array.CallableFirstArg("some").throwTypeError()

            indicesProducer(obj).forEach {
                if (it >= length)
                    return@forEach

                val value = obj.get(it)
                val testResult =
                    Operations.toBoolean(Operations.call(callback, thisArg, listOf(value, it.toValue(), obj)))
                if (testResult)
                    return JSTrue
            }

            return JSFalse
        }
    }
}
