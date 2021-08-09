package com.reevajs.reeva.runtime.objects

enum class SlotName {
    ArrayBufferData,
    ArrayBufferByteLength,
    ArrayBufferDetachKey,
    ArrayLength,
    BigIntData,
    BooleanData,
    ByteLength,
    ByteOffset,
    ContentType,
    DataView,
    DateValue,
    Description,
    ErrorData,
    MapData,
    NumberData,
    OriginalSource,
    OriginalFlags,
    ParameterMap,
    PromiseFulfillReactions,
    PromiseIsHandled,
    PromiseState,
    PromiseRejectReactions,
    PromiseResult,
    ProxyHandler,
    ProxyTarget,
    RegExpMatcher,
    SetData,
    StringData,
    SymbolData,
    TypedArrayKind, // non-standard
    TypedArrayName,
    ViewedArrayBuffer,

    // Intl slots
    InitializedLocale, // JSUndefined
    InitializedNumberFormat, // JSUndefined
    Locale, // String
    ULocale, // ULocale
    NumberFormatter, // LocalizedNumberFormatter
    BoundFormat, // JSFunction
}
