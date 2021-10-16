package com.reevajs.reeva.runtime.builtins

import com.reevajs.reeva.jvmcompat.JSClassObject
import com.reevajs.reeva.jvmcompat.JSPackageProto
import com.reevajs.reeva.runtime.JSGlobalObject
import com.reevajs.reeva.runtime.arrays.JSArrayCtor
import com.reevajs.reeva.runtime.arrays.JSArrayProto
import com.reevajs.reeva.runtime.collections.*
import com.reevajs.reeva.runtime.errors.JSErrorProto
import com.reevajs.reeva.runtime.functions.JSFunctionProto
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObjectProto
import com.reevajs.reeva.runtime.global.JSConsoleProto
import com.reevajs.reeva.runtime.iterators.*
import com.reevajs.reeva.runtime.memory.*
import com.reevajs.reeva.runtime.objects.JSObjectCtor
import com.reevajs.reeva.runtime.objects.JSObjectProto
import com.reevajs.reeva.runtime.other.JSDateCtor
import com.reevajs.reeva.runtime.other.JSDateProto
import com.reevajs.reeva.runtime.other.JSProxyCtor
import com.reevajs.reeva.runtime.promises.JSPromiseCtor
import com.reevajs.reeva.runtime.promises.JSPromiseProto
import com.reevajs.reeva.runtime.regexp.JSRegExpCtor
import com.reevajs.reeva.runtime.regexp.JSRegExpProto
import com.reevajs.reeva.runtime.regexp.JSRegExpStringIteratorProto
import com.reevajs.reeva.runtime.singletons.JSMathObject
import com.reevajs.reeva.runtime.singletons.JSONObject
import com.reevajs.reeva.runtime.singletons.JSReflectObject
import com.reevajs.reeva.runtime.wrappers.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

enum class ReevaBuiltin(clazz: Class<*>, name: String) : Builtin {
    ArrayCtorGetSymbolSpecies(JSArrayCtor::class.java, "get@@species"),
    ArrayCtorIsArray(JSArrayCtor::class.java, "isArray"),
    ArrayCtorFrom(JSArrayCtor::class.java, "from"),
    ArrayCtorOf(JSArrayCtor::class.java, "of"),
    ArrayProtoAt(JSArrayProto::class.java, "at"),
    ArrayProtoConcat(JSArrayProto::class.java, "concat"),
    ArrayProtoCopyWithin(JSArrayProto::class.java, "copyWithin"),
    ArrayProtoEntries(JSArrayProto::class.java, "entries"),
    ArrayProtoEvery(JSArrayProto::class.java, "every"),
    ArrayProtoFill(JSArrayProto::class.java, "fill"),
    ArrayProtoFilter(JSArrayProto::class.java, "filter"),
    ArrayProtoFind(JSArrayProto::class.java, "find"),
    ArrayProtoFindIndex(JSArrayProto::class.java, "findIndex"),
    ArrayProtoFlat(JSArrayProto::class.java, "flat"),
    ArrayProtoFlatMap(JSArrayProto::class.java, "flatMap"),
    ArrayProtoForEach(JSArrayProto::class.java, "forEach"),
    ArrayProtoIncludes(JSArrayProto::class.java, "includes"),
    ArrayProtoIndexOf(JSArrayProto::class.java, "indexOf"),
    ArrayProtoJoin(JSArrayProto::class.java, "join"),
    ArrayProtoKeys(JSArrayProto::class.java, "keys"),
    ArrayProtoLastIndexOf(JSArrayProto::class.java, "lastIndexOf"),
    ArrayProtoMap(JSArrayProto::class.java, "map"),
    ArrayProtoPop(JSArrayProto::class.java, "pop"),
    ArrayProtoPush(JSArrayProto::class.java, "push"),
    ArrayProtoReduce(JSArrayProto::class.java, "reduce"),
    ArrayProtoReduceRight(JSArrayProto::class.java, "reduceRight"),
    ArrayProtoReverse(JSArrayProto::class.java, "reverse"),
    ArrayProtoShift(JSArrayProto::class.java, "shift"),
    ArrayProtoSlice(JSArrayProto::class.java, "slice"),
    ArrayProtoSome(JSArrayProto::class.java, "some"),
    ArrayProtoSplice(JSArrayProto::class.java, "splice"),
    ArrayProtoToString(JSArrayProto::class.java, "toString"),
    ArrayProtoUnshift(JSArrayProto::class.java, "unshift"),
    ArrayProtoValues(JSArrayProto::class.java, "values"),

    ArrayBufferCtorIsView(JSArrayBufferCtor::class.java, "isView"),
    ArrayBufferCtorGetSymbolSpecies(JSArrayBufferCtor::class.java, "get@@species"),
    ArrayBufferProtoGetByteLength(JSArrayBufferProto::class.java, "getByteLength"),
    ArrayBufferProtoSlice(JSArrayBufferProto::class.java, "slice"),

    ArrayIteratorProtoNext(JSArrayIteratorProto::class.java, "next"),

    BigIntCtorAsIntN(JSBigIntCtor::class.java, "asIntN"),
    BigIntCtorAsUintN(JSBigIntCtor::class.java, "asUintN"),
    BigIntProtoToString(JSBigIntProto::class.java, "toString"),
    BigIntProtoValueOf(JSBigIntProto::class.java, "valueOf"),

    BooleanProtoToString(JSBooleanProto::class.java, "toString"),
    BooleanProtoValueOf(JSBooleanProto::class.java, "valueOf"),

    DataViewProtoGetBuffer(JSDataViewProto::class.java, "getBuffer"),
    DataViewProtoGetByteLength(JSDataViewProto::class.java, "getByteLength"),
    DataViewProtoGetByteOffset(JSDataViewProto::class.java, "getByteOffset"),
    DataViewProtoGetBigInt64(JSDataViewProto::class.java, "getBigInt64"),
    DataViewProtoGetBigUint64(JSDataViewProto::class.java, "getBigUint64"),
    DataViewProtoGetFloat32(JSDataViewProto::class.java, "getFloat32"),
    DataViewProtoGetFloat64(JSDataViewProto::class.java, "getFloat64"),
    DataViewProtoGetInt8(JSDataViewProto::class.java, "getInt8"),
    DataViewProtoGetInt16(JSDataViewProto::class.java, "getInt16"),
    DataViewProtoGetInt32(JSDataViewProto::class.java, "getInt32"),
    DataViewProtoGetUint8(JSDataViewProto::class.java, "getUint8"),
    DataViewProtoGetUint16(JSDataViewProto::class.java, "getUint16"),
    DataViewProtoGetUint32(JSDataViewProto::class.java, "getUint32"),
    DataViewProtoSetBigInt64(JSDataViewProto::class.java, "setBigInt64"),
    DataViewProtoSetBigUint64(JSDataViewProto::class.java, "setBigUint64"),
    DataViewProtoSetFloat32(JSDataViewProto::class.java, "setFloat32"),
    DataViewProtoSetFloat64(JSDataViewProto::class.java, "setFloat64"),
    DataViewProtoSetInt8(JSDataViewProto::class.java, "setInt8"),
    DataViewProtoSetInt16(JSDataViewProto::class.java, "setInt16"),
    DataViewProtoSetInt32(JSDataViewProto::class.java, "setInt32"),
    DataViewProtoSetUint8(JSDataViewProto::class.java, "setUint8"),
    DataViewProtoSetUint16(JSDataViewProto::class.java, "setUint16"),
    DataViewProtoSetUint32(JSDataViewProto::class.java, "setUint32"),

    DateCtorNow(JSDateCtor::class.java, "now"),
    DateCtorParse(JSDateCtor::class.java, "parse"),
    DateCtorUTC(JSDateCtor::class.java, "utc"),
    DateProtoGetDate(JSDateProto::class.java, "getDate"),
    DateProtoGetDay(JSDateProto::class.java, "getDay"),
    DateProtoGetFullYear(JSDateProto::class.java, "getFullYear"),
    DateProtoGetHours(JSDateProto::class.java, "getHours"),
    DateProtoGetMilliseconds(JSDateProto::class.java, "getMilliseconds"),
    DateProtoGetMinutes(JSDateProto::class.java, "getMinutes"),
    DateProtoGetMonth(JSDateProto::class.java, "getMonth"),
    DateProtoGetSeconds(JSDateProto::class.java, "getSeconds"),
    DateProtoGetTime(JSDateProto::class.java, "getTime"),
    DateProtoGetTimezoneOffset(JSDateProto::class.java, "getTimezoneOffset"),
    DateProtoGetUTCDate(JSDateProto::class.java, "getUTCDate"),
    DateProtoGetUTCFullYear(JSDateProto::class.java, "getUTCFullYear"),
    DateProtoGetUTCHours(JSDateProto::class.java, "getUTCHours"),
    DateProtoGetUTCMilliseconds(JSDateProto::class.java, "getUTCMilliseconds"),
    DateProtoGetUTCMinutes(JSDateProto::class.java, "getUTCMinutes"),
    DateProtoGetUTCMonth(JSDateProto::class.java, "getUTCMonth"),
    DateProtoGetUTCSeconds(JSDateProto::class.java, "getUTCSeconds"),
    DateProtoSetDate(JSDateProto::class.java, "setDate"),
    DateProtoSetFullYear(JSDateProto::class.java, "setFullYear"),
    DateProtoSetHours(JSDateProto::class.java, "setHours"),
    DateProtoSetMilliseconds(JSDateProto::class.java, "setMilliseconds"),
    DateProtoSetMonth(JSDateProto::class.java, "setMonth"),
    DateProtoSetSeconds(JSDateProto::class.java, "setSeconds"),
    DateProtoSetTime(JSDateProto::class.java, "setTime"),
    DateProtoSetUTCDate(JSDateProto::class.java, "setUTCDate"),
    DateProtoSetUTCFullYear(JSDateProto::class.java, "setUTCFullYear"),
    DateProtoSetUTCHours(JSDateProto::class.java, "setUTCHours"),
    DateProtoSetUTCMilliseconds(JSDateProto::class.java, "setUTCMilliseconds"),
    DateProtoSetUTCMinutes(JSDateProto::class.java, "setUTCMinutes"),
    DateProtoSetUTCMonth(JSDateProto::class.java, "setUTCMonth"),
    DateProtoSetUTCSeconds(JSDateProto::class.java, "setUTCSeconds"),
    DateProtoToDateString(JSDateProto::class.java, "toDateString"),
    DateProtoToISOString(JSDateProto::class.java, "toISOString"),
    DateProtoToJSON(JSDateProto::class.java, "toJSON"),
    DateProtoToString(JSDateProto::class.java, "toString"),
    DateProtoToTimeString(JSDateProto::class.java, "toTimeString"),
    DateProtoToUTCString(JSDateProto::class.java, "toUTCString"),
    DateProtoValueOf(JSDateProto::class.java, "valueOf"),
    DateProtoSymbolToPrimitive(JSDateProto::class.java, "@@toPrimitive"),

    ErrorProtoToString(JSErrorProto::class.java, "toString"),

    FunctionProtoApply(JSFunctionProto::class.java, "apply"),
    FunctionProtoBind(JSFunctionProto::class.java, "bind"),
    FunctionProtoCall(JSFunctionProto::class.java, "call"),

    GeneratorObjectProtoNext(JSGeneratorObjectProto::class.java, "next"),
    GeneratorObjectProtoReturn(JSGeneratorObjectProto::class.java, "return"),
    GeneratorObjectProtoThrow(JSGeneratorObjectProto::class.java, "throw"),

    GlobalEval(JSGlobalObject::class.java, "eval"),
    GlobalParseInt(JSGlobalObject::class.java, "parseInt"),

    IteratorProtoSymbolIterator(JSIteratorProto::class.java, "@@iterator"),

    JSONParse(JSONObject::class.java, "parse"),
    JSONStringify(JSONObject::class.java, "stringify"),
    JSONGetSymbolToStringTag(JSONObject::class.java, "get@@toStringTag"),

    ListIteratorProtoNext(JSListIteratorProto::class.java, "next"),

    MapCtorGetSymbolSpecies(JSMapCtor::class.java, "get@@species"),
    MapProtoClear(JSMapProto::class.java, "clear"),
    MapProtoDelete(JSMapProto::class.java, "delete"),
    MapProtoEntries(JSMapProto::class.java, "entries"),
    MapProtoForEach(JSMapProto::class.java, "forEach"),
    MapProtoGet(JSMapProto::class.java, "get"),
    MapProtoHas(JSMapProto::class.java, "has"),
    MapProtoKeys(JSMapProto::class.java, "keys"),
    MapProtoSet(JSMapProto::class.java, "set"),
    MapProtoValues(JSMapProto::class.java, "values"),
    MapProtoGetSize(JSMapProto::class.java, "getSize"),

    MapIteratorProtoNext(JSMapIteratorProto::class.java, "next"),

    MathAbs(JSMathObject::class.java, "abs"),
    MathAcos(JSMathObject::class.java, "acos"),
    MathAcosh(JSMathObject::class.java, "acosh"),
    MathAsin(JSMathObject::class.java, "asin"),
    MathAsinh(JSMathObject::class.java, "asinh"),
    MathAtan(JSMathObject::class.java, "atan"),
    MathAtanh(JSMathObject::class.java, "atanh"),
    MathAtan2(JSMathObject::class.java, "atan2"),
    MathCbrt(JSMathObject::class.java, "cbrt"),
    MathCeil(JSMathObject::class.java, "ceil"),
    MathClz32(JSMathObject::class.java, "clz32"),
    MathCos(JSMathObject::class.java, "cos"),
    MathCosh(JSMathObject::class.java, "cosh"),
    MathExp(JSMathObject::class.java, "exp"),
    MathExpm1(JSMathObject::class.java, "expm1"),
    MathFloor(JSMathObject::class.java, "floor"),
    MathFround(JSMathObject::class.java, "fround"),
    MathHypot(JSMathObject::class.java, "hypot"),
    MathImul(JSMathObject::class.java, "imul"),
    MathLog(JSMathObject::class.java, "log"),
    MathLog1p(JSMathObject::class.java, "log1p"),
    MathLog10(JSMathObject::class.java, "log10"),
    MathLog2(JSMathObject::class.java, "log2"),
    MathMax(JSMathObject::class.java, "max"),
    MathMin(JSMathObject::class.java, "min"),
    MathPow(JSMathObject::class.java, "pow"),
    MathRandom(JSMathObject::class.java, "random"),
    MathRound(JSMathObject::class.java, "round"),
    MathSign(JSMathObject::class.java, "sign"),
    MathSin(JSMathObject::class.java, "sin"),
    MathSinh(JSMathObject::class.java, "sinh"),
    MathSqrt(JSMathObject::class.java, "sqrt"),
    MathTan(JSMathObject::class.java, "tan"),
    MathTanh(JSMathObject::class.java, "tanh"),
    MathTrunc(JSMathObject::class.java, "trunc"),

    NumberCtorIsFinite(JSNumberCtor::class.java, "isFinite"),
    NumberCtorIsInteger(JSNumberCtor::class.java, "isInteger"),
    NumberCtorIsNaN(JSNumberCtor::class.java, "isNaN"),
    NumberCtorIsSafeInteger(JSNumberCtor::class.java, "isSafeInteger"),
    NumberCtorParseFloat(JSNumberCtor::class.java, "parseFloat"),
    NumberCtorParseInt(JSNumberCtor::class.java, "parseInt"),
    NumberProtoToExponential(JSNumberProto::class.java, "toExponential"),
    NumberProtoToFixed(JSNumberProto::class.java, "toFixed"),
    NumberProtoToLocaleString(JSNumberProto::class.java, "toLocaleString"),
    NumberProtoToPrecision(JSNumberProto::class.java, "toPrecision"),
    NumberProtoToString(JSNumberProto::class.java, "toString"),
    NumberProtoValueOf(JSNumberProto::class.java, "valueOf"),

    ObjectCtorAssign(JSObjectCtor::class.java, "assign"),
    ObjectCtorCreate(JSObjectCtor::class.java, "create"),
    ObjectCtorDefineProperties(JSObjectCtor::class.java, "defineProperties"),
    ObjectCtorDefineProperty(JSObjectCtor::class.java, "defineProperty"),
    ObjectCtorEntries(JSObjectCtor::class.java, "entries"),
    ObjectCtorFreeze(JSObjectCtor::class.java, "freeze"),
    ObjectCtorFromEntries(JSObjectCtor::class.java, "fromEntries"),
    ObjectCtorGetOwnPropertyDescriptor(JSObjectCtor::class.java, "getOwnPropertyDescriptor"),
    ObjectCtorGetOwnPropertyDescriptors(JSObjectCtor::class.java, "getOwnPropertyDescriptors"),
    ObjectCtorGetOwnPropertyNames(JSObjectCtor::class.java, "getOwnPropertyNames"),
    ObjectCtorGetOwnPropertySymbols(JSObjectCtor::class.java, "getOwnPropertySymbols"),
    ObjectCtorGetPrototypeOf(JSObjectCtor::class.java, "getPrototypeOf"),
    ObjectCtorIs(JSObjectCtor::class.java, "is"),
    ObjectCtorIsExtensible(JSObjectCtor::class.java, "isExtensible"),
    ObjectCtorIsFrozen(JSObjectCtor::class.java, "isFrozen"),
    ObjectCtorIsSealed(JSObjectCtor::class.java, "isSealed"),
    ObjectCtorPreventExtensions(JSObjectCtor::class.java, "preventExtensions"),
    ObjectCtorSeal(JSObjectCtor::class.java, "seal"),
    ObjectCtorSetPrototypeOf(JSObjectCtor::class.java, "setPrototypeOf"),
    ObjectCtorValues(JSObjectCtor::class.java, "values"),
    ObjectProtoGetProto(JSObjectProto::class.java, "getProto"),
    ObjectProtoSetProto(JSObjectProto::class.java, "setProto"),
    ObjectProtoDefineGetter(JSObjectProto::class.java, "defineGetter"),
    ObjectProtoDefineSetter(JSObjectProto::class.java, "defineSetter"),
    ObjectProtoLookupGetter(JSObjectProto::class.java, "lookupGetter"),
    ObjectProtoLookupSetter(JSObjectProto::class.java, "lookupSetter"),
    ObjectProtoHasOwnProperty(JSObjectProto::class.java, "hasOwnProperty"),
    ObjectProtoIsPrototypeOf(JSObjectProto::class.java, "isPrototypeOf"),
    ObjectProtoPropertyIsEnumerable(JSObjectProto::class.java, "propertyIsEnumerable"),
    ObjectProtoToLocaleString(JSObjectProto::class.java, "toLocaleString"),
    ObjectProtoToString(JSObjectProto::class.java, "toString"),
    ObjectProtoValueOf(JSObjectProto::class.java, "valueOf"),

    ObjectPropertyIteratorProtoNext(JSObjectPropertyIteratorProto::class.java, "next"),

    PromiseCtorAll(JSPromiseCtor::class.java, "all"),
    PromiseCtorAllSettled(JSPromiseCtor::class.java, "allSettled"),
    PromiseCtorResolve(JSPromiseCtor::class.java, "resolve"),
    PromiseCtorReject(JSPromiseCtor::class.java, "reject"),
    PromiseProtoCatch(JSPromiseProto::class.java, "catch"),
    PromiseProtoFinally(JSPromiseProto::class.java, "finally"),
    PromiseProtoThen(JSPromiseProto::class.java, "then"),

    ProxyCtorRevocable(JSProxyCtor::class.java, "revocable"),

    ReflectApply(JSReflectObject::class.java, "apply"),
    ReflectConstruct(JSReflectObject::class.java, "construct"),
    ReflectDefineProperty(JSReflectObject::class.java, "defineProperty"),
    ReflectDeleteProperty(JSReflectObject::class.java, "deleteProperty"),
    ReflectGet(JSReflectObject::class.java, "get"),
    ReflectGetOwnPropertyDescriptor(JSReflectObject::class.java, "getOwnPropertyDescriptor"),
    ReflectHas(JSReflectObject::class.java, "has"),
    ReflectIsExtensible(JSReflectObject::class.java, "isExtensible"),
    ReflectOwnKeys(JSReflectObject::class.java, "ownKeys"),
    ReflectPreventExtensions(JSReflectObject::class.java, "preventExtensions"),
    ReflectSet(JSReflectObject::class.java, "set"),
    ReflectSetPrototypeOf(JSReflectObject::class.java, "setPrototypeOf"),

    RegExpCtorGetSpecies(JSRegExpCtor::class.java, "get@@species"),
    RegExpProtoGetDotAll(JSRegExpProto::class.java, "getDotAll"),
    RegExpProtoGetFlags(JSRegExpProto::class.java, "getFlags"),
    RegExpProtoGetGlobal(JSRegExpProto::class.java, "getGlobal"),
    RegExpProtoGetIgnoreCase(JSRegExpProto::class.java, "getIgnoreCase"),
    RegExpProtoGetMultiline(JSRegExpProto::class.java, "getMultiline"),
    RegExpProtoGetSource(JSRegExpProto::class.java, "getSource"),
    RegExpProtoGetSticky(JSRegExpProto::class.java, "getSticky"),
    RegExpProtoGetUnicode(JSRegExpProto::class.java, "getUnicode"),
    RegExpProtoMatch(JSRegExpProto::class.java, "@@match"),
    RegExpProtoMatchAll(JSRegExpProto::class.java, "@@matchAll"),
    RegExpProtoReplace(JSRegExpProto::class.java, "@@replace"),
    RegExpProtoSearch(JSRegExpProto::class.java, "@@search"),
    RegExpProtoSplit(JSRegExpProto::class.java, "@@split"),
    RegExpProtoExec(JSRegExpProto::class.java, "exec"),
    RegExpProtoTest(JSRegExpProto::class.java, "test"),
    RegExpProtoToString(JSRegExpProto::class.java, "toString"),

    RegExpStringIteratorProtoNext(JSRegExpStringIteratorProto::class.java, "next"),

    SetCtorGetSymbolSpecies(JSSetCtor::class.java, "get@@species"),
    SetProtoGetSize(JSSetProto::class.java, "getSize"),
    SetProtoAdd(JSSetProto::class.java, "add"),
    SetProtoClear(JSSetProto::class.java, "clear"),
    SetProtoDelete(JSSetProto::class.java, "delete"),
    SetProtoEntries(JSSetProto::class.java, "entries"),
    SetProtoForEach(JSSetProto::class.java, "forEach"),
    SetProtoHas(JSSetProto::class.java, "has"),
    SetProtoValues(JSSetProto::class.java, "values"),

    SetIteratorProtoNext(JSSetIteratorProto::class.java, "next"),

    StringCtorFromCharCode(JSStringCtor::class.java, "fromCharCode"),
    StringCtorFromCodePoint(JSStringCtor::class.java, "fromCodePoint"),
    StringProtoAt(JSStringProto::class.java, "at"),
    StringProtoCharAt(JSStringProto::class.java, "charAt"),
    StringProtoCharCodeAt(JSStringProto::class.java, "charCodeAt"),
    StringProtoCodePointAt(JSStringProto::class.java, "codePointAt"),
    StringProtoConcat(JSStringProto::class.java, "concat"),
    StringProtoEndsWith(JSStringProto::class.java, "endsWith"),
    StringProtoIncludes(JSStringProto::class.java, "includes"),
    StringProtoIndexOf(JSStringProto::class.java, "indexOf"),
    StringProtoLastIndexOf(JSStringProto::class.java, "lastIndexOf"),
    StringProtoPadEnd(JSStringProto::class.java, "padEnd"),
    StringProtoPadStart(JSStringProto::class.java, "padStart"),
    StringProtoRepeat(JSStringProto::class.java, "repeat"),
    StringProtoReplace(JSStringProto::class.java, "replace"),
    StringProtoSlice(JSStringProto::class.java, "slice"),
    StringProtoSplit(JSStringProto::class.java, "split"),
    StringProtoStartsWith(JSStringProto::class.java, "startsWith"),
    StringProtoSubstring(JSStringProto::class.java, "substring"),
    StringProtoToLowerCase(JSStringProto::class.java, "toLowerCase"),
    StringProtoToString(JSStringProto::class.java, "toString"),
    StringProtoToUpperCase(JSStringProto::class.java, "toUpperCase"),
    StringProtoTrim(JSStringProto::class.java, "trim"),
    StringProtoTrimEnd(JSStringProto::class.java, "trimEnd"),
    StringProtoTrimStart(JSStringProto::class.java, "trimStart"),
    StringProtoValueOf(JSStringProto::class.java, "valueOf"),

    SymbolCtorFor(JSSymbolCtor::class.java, "for"),
    SymbolCtorKeyFor(JSSymbolCtor::class.java, "keyFor"),
    SymbolProtoToString(JSSymbolProto::class.java, "toString"),
    SymbolProtoToValue(JSSymbolProto::class.java, "toValue"),
    SymbolProtoSymbolToPrimitive(JSSymbolProto::class.java, "@@toPrimitive"),

    TypedArrayCtorFrom(JSTypedArrayCtor::class.java, "from"),
    TypedArrayCtorOf(JSTypedArrayCtor::class.java, "of"),
    TypedArrayProtoGetSymbolToStringTag(JSTypedArrayProto::class.java, "get@@toStringTag"),
    TypedArrayProtoGetBuffer(JSTypedArrayProto::class.java, "getBuffer"),
    TypedArrayProtoGetByteLength(JSTypedArrayProto::class.java, "getByteLength"),
    TypedArrayProtoGetByteOffset(JSTypedArrayProto::class.java, "getByteOffset"),
    TypedArrayProtoGetLength(JSTypedArrayProto::class.java, "getLength"),
    TypedArrayProtoAt(JSTypedArrayProto::class.java, "at"),
    TypedArrayProtoCopyWithin(JSTypedArrayProto::class.java, "copyWithin"),
    TypedArrayProtoEntries(JSTypedArrayProto::class.java, "entries"),
    TypedArrayProtoEvery(JSTypedArrayProto::class.java, "every"),
    TypedArrayProtoFill(JSTypedArrayProto::class.java, "fill"),
    TypedArrayProtoFilter(JSTypedArrayProto::class.java, "filter"),
    TypedArrayProtoFind(JSTypedArrayProto::class.java, "find"),
    TypedArrayProtoFindIndex(JSTypedArrayProto::class.java, "findIndex"),
    TypedArrayProtoForEach(JSTypedArrayProto::class.java, "forEach"),
    TypedArrayProtoIncludes(JSTypedArrayProto::class.java, "includes"),
    TypedArrayProtoIndexOf(JSTypedArrayProto::class.java, "indexOf"),
    TypedArrayProtoJoin(JSTypedArrayProto::class.java, "join"),
    // TypedArrayProtoKeys(JSTypedArrayProto::class.java, "keys"),
    TypedArrayProtoLastIndexOf(JSTypedArrayProto::class.java, "lastIndexOf"),
    // TypedArrayProtoMap(JSTypedArrayProto::class.java, "map"),
    TypedArrayProtoReduce(JSTypedArrayProto::class.java, "reduce"),
    TypedArrayProtoReduceRight(JSTypedArrayProto::class.java, "reduceRight"),
    TypedArrayProtoReverse(JSTypedArrayProto::class.java, "reverse"),
    // TypedArrayProtoSet(JSTypedArrayProto::class.java, "set"),
    // TypedArrayProtoSlice(JSTypedArrayProto::class.java, "slice"),
    TypedArrayProtoSome(JSTypedArrayProto::class.java, "some"),
    // TypedArrayProtoSort(JSTypedArrayProto::class.java, "sort"),
    // TypedArrayProtoSubarray(JSTypedArrayProto::class.java, "subarray"),
    // TypedArrayProtoToString(JSTypedArrayProto::class.java, "toString"),
    // TypedArrayProtoValues(JSTypedArrayProto::class.java, "values"),

    // Non-standard builtins

    ClassObjectToString(JSClassObject::class.java, "toString"),
    ConsoleProtoLog(JSConsoleProto::class.java, "log"),
    GlobalIsNaN(JSGlobalObject::class.java, "isNaN"),
    GlobalId(JSGlobalObject::class.java, "id"),
    GlobalJvm(JSGlobalObject::class.java, "jvm"),
    GlobalInspect(JSGlobalObject::class.java, "inspect"),
    PackageProtoToString(JSPackageProto::class.java, "toString");

    override val handle: MethodHandle = MethodHandles.publicLookup().findStatic(clazz, name, Builtin.METHOD_TYPE)
}
