package com.reevajs.reeva.core

import com.reevajs.reeva.core.environment.GlobalEnvRecord
import com.reevajs.reeva.jvmcompat.JSClassProto
import com.reevajs.reeva.jvmcompat.JSPackageObject
import com.reevajs.reeva.jvmcompat.JSPackageProto
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.arrays.JSArrayCtor
import com.reevajs.reeva.runtime.arrays.JSArrayProto
import com.reevajs.reeva.runtime.collections.JSMapCtor
import com.reevajs.reeva.runtime.collections.JSMapProto
import com.reevajs.reeva.runtime.collections.JSSetCtor
import com.reevajs.reeva.runtime.collections.JSSetProto
import com.reevajs.reeva.runtime.errors.*
import com.reevajs.reeva.runtime.functions.*
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorFunctionCtor
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorFunctionProto
import com.reevajs.reeva.runtime.functions.generators.JSGeneratorObjectProto
import com.reevajs.reeva.runtime.global.JSConsole
import com.reevajs.reeva.runtime.global.JSConsoleProto
import com.reevajs.reeva.runtime.iterators.*
import com.reevajs.reeva.runtime.memory.*
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.JSObjectCtor
import com.reevajs.reeva.runtime.objects.JSObjectProto
import com.reevajs.reeva.runtime.other.JSDateCtor
import com.reevajs.reeva.runtime.other.JSDateProto
import com.reevajs.reeva.runtime.other.JSProxyCtor
import com.reevajs.reeva.runtime.primitives.JSAccessor
import com.reevajs.reeva.runtime.primitives.JSSymbol
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.promises.JSPromiseCtor
import com.reevajs.reeva.runtime.promises.JSPromiseProto
import com.reevajs.reeva.runtime.regexp.JSRegExpCtor
import com.reevajs.reeva.runtime.regexp.JSRegExpProto
import com.reevajs.reeva.runtime.regexp.JSRegExpStringIteratorProto
import com.reevajs.reeva.runtime.singletons.JSMathObject
import com.reevajs.reeva.runtime.singletons.JSONObject
import com.reevajs.reeva.runtime.singletons.JSReflectObject
import com.reevajs.reeva.runtime.temporal.*
import com.reevajs.reeva.runtime.wrappers.*
import com.reevajs.reeva.runtime.wrappers.strings.JSStringCtor
import com.reevajs.reeva.runtime.wrappers.strings.JSStringIteratorProto
import com.reevajs.reeva.runtime.wrappers.strings.JSStringProto
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.ecmaAssert
import com.reevajs.reeva.utils.expect
import com.reevajs.reeva.utils.key
import java.util.concurrent.ConcurrentHashMap

class Realm {
    lateinit var globalObject: JSObject
        private set

    lateinit var globalEnv: GlobalEnvRecord

    internal val moduleTree = ModuleTree()

    // Special objects that have to be handled manually
    lateinit var objectProto: JSObjectProto private set
    lateinit var functionProto: JSFunctionProto private set
    lateinit var symbolProto: JSSymbolProto private set
    lateinit var symbolCtor: JSSymbolCtor private set

    lateinit var numberProto: JSNumberProto private set
    lateinit var bigIntProto: JSBigIntProto private set
    lateinit var booleanProto: JSBooleanProto private set
    lateinit var stringProto: JSStringProto private set
    lateinit var regExpProto: JSRegExpProto private set
    lateinit var arrayProto: JSArrayProto private set
    lateinit var setProto: JSSetProto private set
    lateinit var mapProto: JSMapProto private set
    lateinit var promiseProto: JSPromiseProto private set
    lateinit var dateProto: JSDateProto private set
    lateinit var iteratorProto: JSIteratorProto private set
    lateinit var stringIteratorProto: JSStringIteratorProto private set
    lateinit var arrayIteratorProto: JSArrayIteratorProto private set
    lateinit var setIteratorProto: JSSetIteratorProto private set
    lateinit var mapIteratorProto: JSMapIteratorProto private set
    lateinit var objectPropertyIteratorProto: JSObjectPropertyIteratorProto private set
    lateinit var listIteratorProto: JSListIteratorProto private set
    lateinit var regExpStringIteratorProto: JSRegExpStringIteratorProto private set
    lateinit var generatorObjectProto: JSGeneratorObjectProto private set
    lateinit var generatorFunctionProto: JSGeneratorFunctionProto private set
    lateinit var asyncFunctionProto: JSAsyncFunctionProto private set
    lateinit var consoleProto: JSConsoleProto private set

    lateinit var dataViewProto: JSDataViewProto private set
    lateinit var arrayBufferProto: JSArrayBufferProto private set
    lateinit var typedArrayProto: JSTypedArrayProto private set
    lateinit var int8ArrayProto: JSInt8ArrayProto private set
    lateinit var uint8ArrayProto: JSUint8ArrayProto private set
    lateinit var uint8CArrayProto: JSUint8CArrayProto private set
    lateinit var int16ArrayProto: JSInt16ArrayProto private set
    lateinit var uint16ArrayProto: JSUint16ArrayProto private set
    lateinit var int32ArrayProto: JSInt32ArrayProto private set
    lateinit var uint32ArrayProto: JSUint32ArrayProto private set
    lateinit var float32ArrayProto: JSFloat32ArrayProto private set
    lateinit var float64ArrayProto: JSFloat64ArrayProto private set
    lateinit var bigInt64ArrayProto: JSBigInt64ArrayProto private set
    lateinit var bigUint64ArrayProto: JSBigUint64ArrayProto private set

    lateinit var errorProto: JSErrorProto private set
    lateinit var evalErrorProto: JSEvalErrorProto private set
    lateinit var internalErrorProto: JSInternalErrorProto private set
    lateinit var typeErrorProto: JSTypeErrorProto private set
    lateinit var rangeErrorProto: JSRangeErrorProto private set
    lateinit var referenceErrorProto: JSReferenceErrorProto private set
    lateinit var syntaxErrorProto: JSSyntaxErrorProto private set
    lateinit var uriErrorProto: JSURIErrorProto private set

    lateinit var calendarProto: JSCalendarProto private set
    lateinit var durationProto: JSDurationProto private set
    lateinit var instantProto: JSInstantProto private set

    lateinit var objectCtor: JSObjectCtor private set
    lateinit var numberCtor: JSNumberCtor private set
    lateinit var bigIntCtor: JSBigIntCtor private set
    lateinit var booleanCtor: JSBooleanCtor private set
    lateinit var stringCtor: JSStringCtor private set
    lateinit var regExpCtor: JSRegExpCtor private set
    lateinit var functionCtor: JSFunctionCtor private set
    lateinit var generatorFunctionCtor: JSGeneratorFunctionCtor private set
    lateinit var asyncFunctionCtor: JSAsyncFunctionCtor private set
    lateinit var arrayCtor: JSArrayCtor private set
    lateinit var setCtor: JSSetCtor private set
    lateinit var mapCtor: JSMapCtor private set
    lateinit var proxyCtor: JSProxyCtor private set
    lateinit var promiseCtor: JSPromiseCtor private set
    lateinit var dateCtor: JSDateCtor private set

    lateinit var dataViewCtor: JSDataViewCtor private set
    lateinit var arrayBufferCtor: JSArrayBufferCtor private set
    lateinit var typedArrayCtor: JSTypedArrayCtor private set
    lateinit var int8ArrayCtor: JSInt8ArrayCtor private set
    lateinit var uint8ArrayCtor: JSUint8ArrayCtor private set
    lateinit var uint8CArrayCtor: JSUint8CArrayCtor private set
    lateinit var int16ArrayCtor: JSInt16ArrayCtor private set
    lateinit var uint16ArrayCtor: JSUint16ArrayCtor private set
    lateinit var int32ArrayCtor: JSInt32ArrayCtor private set
    lateinit var uint32ArrayCtor: JSUint32ArrayCtor private set
    lateinit var float32ArrayCtor: JSFloat32ArrayCtor private set
    lateinit var float64ArrayCtor: JSFloat64ArrayCtor private set
    lateinit var bigInt64ArrayCtor: JSBigInt64ArrayCtor private set
    lateinit var bigUint64ArrayCtor: JSBigUint64ArrayCtor private set

    lateinit var errorCtor: JSErrorCtor private set
    lateinit var evalErrorCtor: JSEvalErrorCtor private set
    lateinit var internalErrorCtor: JSInternalErrorCtor private set
    lateinit var typeErrorCtor: JSTypeErrorCtor private set
    lateinit var rangeErrorCtor: JSRangeErrorCtor private set
    lateinit var referenceErrorCtor: JSReferenceErrorCtor private set
    lateinit var syntaxErrorCtor: JSSyntaxErrorCtor private set
    lateinit var uriErrorCtor: JSURIErrorCtor private set

    lateinit var calendarCtor: JSCalendarCtor private set
    lateinit var durationCtor: JSDurationCtor private set
    lateinit var instantCtor: JSInstantCtor private set

    lateinit var throwTypeError: JSFunction private set

    lateinit var mathObj: JSMathObject private set
    lateinit var reflectObj: JSReflectObject private set
    lateinit var jsonObj: JSONObject private set
    lateinit var temporalObj: JSTemporalObject private set
    lateinit var consoleObj: JSConsole private set

    lateinit var packageProto: JSPackageProto private set
    lateinit var classProto: JSClassProto private set
    lateinit var packageObj: JSPackageObject private set

    @ECMAImpl("9.3.3")
    internal fun setGlobalObject(globalObject: JSValue, thisValue: JSValue) {
        // 1. If globalObj is undefined, then
        val theGlobalObject = if (globalObject == JSUndefined) {
            // a. Let intrinsics be realmRec.[[Intrinsics]].
            // b. Set globalObj to OrdinaryObjectCreate(intrinsics.[[%Object.prototype%]]).
            JSObject.create(this)
        } else globalObject

        // 2. Assert: Type(globalObj) is Object.
        ecmaAssert(theGlobalObject is JSObject)

        // 3. If thisValue is undefined, set thisValue to globalObj.
        val theThisValue = if (thisValue == JSUndefined) {
            theGlobalObject
        } else thisValue

        // 4. Set realmRec.[[GlobalObject]] to globalObj.
        this.globalObject = theGlobalObject

        // 5. Let newGlobalEnv be NewGlobalEnvironment(globalObj, thisValue).
        // 6. Set realmRec.[[GlobalEnv]] to newGlobalEnv.
        globalEnv = AOs.newGlobalEnvironment(this, theGlobalObject, theThisValue)

        // 7. Return unused.
    }

    @ECMAImpl("9.3.2")
    fun createIntrinsics() {
        // Guard against multiple initialization
        expect(!::objectProto.isInitialized) { "Duplicate call to Realm::createIntrinsics" }

        // 1. Set realmRec.[[Intrinsics]] to a new Record.
        // Note: We do not have a separate Intrinsics objects, as it just adds an extra
        //       property access that isn't really necessary

        // 2. Set fields of realmRec.[[Intrinsics]] with the values listed in Table 6. The field names are the names
        //    listed in column one of the table. The value of each field is a new object value fully and recursively
        //    populated with property values as defined by the specification of each object in clauses 19 through 28.
        //    All object property values are newly created object values. All values that are built-in function objects
        //    are created by performing CreateBuiltinFunction(steps, length, name, slots, realmRec, prototype) where
        //    steps is the definition of that function provided by this specification, name is the initial value of the
        //    function's name property, length is the initial value of the function's length property, slots is a list
        //    of the names, if any, of the function's specified internal slots, and prototype is the specified value of
        //    the function's [[Prototype]] internal slot. The creation of the intrinsics and their properties must be
        //    ordered to avoid any dependencies upon objects that have not yet been created.

        // Special objects: Create and initialize separately
        objectProto = JSObjectProto.create(this)
        functionProto = JSFunctionProto.create(this)
        objectCtor = JSObjectCtor.create(this)
        objectProto.init()
        functionProto.init()
        objectCtor.init()

        numberCtor = JSNumberCtor.create(this)
        bigIntCtor = JSBigIntCtor.create(this)
        booleanCtor = JSBooleanCtor.create(this)
        stringCtor = JSStringCtor.create(this)
        regExpCtor = JSRegExpCtor.create(this)
        functionCtor = JSFunctionCtor.create(this)
        generatorFunctionCtor = JSGeneratorFunctionCtor.create(this)
        asyncFunctionCtor = JSAsyncFunctionCtor.create(this)
        arrayCtor = JSArrayCtor.create(this)
        setCtor = JSSetCtor.create(this)
        mapCtor = JSMapCtor.create(this)
        proxyCtor = JSProxyCtor.create(this)
        promiseCtor = JSPromiseCtor.create(this)
        dateCtor = JSDateCtor.create(this)

        dataViewCtor = JSDataViewCtor.create(this)
        arrayBufferCtor = JSArrayBufferCtor.create(this)
        typedArrayCtor = JSTypedArrayCtor.create(this)
        int8ArrayCtor = JSInt8ArrayCtor.create(this)
        uint8ArrayCtor = JSUint8ArrayCtor.create(this)
        uint8CArrayCtor = JSUint8CArrayCtor.create(this)
        int16ArrayCtor = JSInt16ArrayCtor.create(this)
        uint16ArrayCtor = JSUint16ArrayCtor.create(this)
        int32ArrayCtor = JSInt32ArrayCtor.create(this)
        uint32ArrayCtor = JSUint32ArrayCtor.create(this)
        float32ArrayCtor = JSFloat32ArrayCtor.create(this)
        float64ArrayCtor = JSFloat64ArrayCtor.create(this)
        bigInt64ArrayCtor = JSBigInt64ArrayCtor.create(this)
        bigUint64ArrayCtor = JSBigUint64ArrayCtor.create(this)

        errorCtor = JSErrorCtor.create(this)
        evalErrorCtor = JSEvalErrorCtor.create(this)
        internalErrorCtor = JSInternalErrorCtor.create(this)
        typeErrorCtor = JSTypeErrorCtor.create(this)
        rangeErrorCtor = JSRangeErrorCtor.create(this)
        referenceErrorCtor = JSReferenceErrorCtor.create(this)
        syntaxErrorCtor = JSSyntaxErrorCtor.create(this)
        uriErrorCtor = JSURIErrorCtor.create(this)

        calendarCtor = JSCalendarCtor.create(this)
        durationCtor = JSDurationCtor.create(this)
        instantCtor = JSInstantCtor.create(this)

        symbolCtor = JSSymbolCtor.create(this)
        symbolProto = JSSymbolProto.create(this)

        numberProto = JSNumberProto.create(this)
        bigIntProto = JSBigIntProto.create(this)
        booleanProto = JSBooleanProto.create(this)
        stringProto = JSStringProto.create(this)
        regExpProto = JSRegExpProto.create(this)
        arrayProto = JSArrayProto.create(this)
        setProto = JSSetProto.create(this)
        mapProto = JSMapProto.create(this)
        promiseProto = JSPromiseProto.create(this)
        dateProto = JSDateProto.create(this)
        iteratorProto = JSIteratorProto.create(this)
        stringIteratorProto = JSStringIteratorProto.create(this)
        arrayIteratorProto = JSArrayIteratorProto.create(this)
        setIteratorProto = JSSetIteratorProto.create(this)
        mapIteratorProto = JSMapIteratorProto.create(this)
        objectPropertyIteratorProto = JSObjectPropertyIteratorProto.create(this)
        listIteratorProto = JSListIteratorProto.create(this)
        regExpStringIteratorProto = JSRegExpStringIteratorProto.create(this)
        generatorObjectProto = JSGeneratorObjectProto.create(this)
        generatorFunctionProto = JSGeneratorFunctionProto.create(this)
        asyncFunctionProto = JSAsyncFunctionProto.create(this)
        consoleProto = JSConsoleProto.create(this)

        dataViewProto = JSDataViewProto.create(this)
        arrayBufferProto = JSArrayBufferProto.create(this)
        typedArrayProto = JSTypedArrayProto.create(this)
        int8ArrayProto = JSInt8ArrayProto.create(this)
        uint8ArrayProto = JSUint8ArrayProto.create(this)
        uint8CArrayProto = JSUint8CArrayProto.create(this)
        int16ArrayProto = JSInt16ArrayProto.create(this)
        uint16ArrayProto = JSUint16ArrayProto.create(this)
        int32ArrayProto = JSInt32ArrayProto.create(this)
        uint32ArrayProto = JSUint32ArrayProto.create(this)
        float32ArrayProto = JSFloat32ArrayProto.create(this)
        float64ArrayProto = JSFloat64ArrayProto.create(this)
        bigInt64ArrayProto = JSBigInt64ArrayProto.create(this)
        bigUint64ArrayProto = JSBigUint64ArrayProto.create(this)

        errorProto = JSErrorProto.create(this)
        evalErrorProto = JSEvalErrorProto.create(this)
        internalErrorProto = JSInternalErrorProto.create(this)
        typeErrorProto = JSTypeErrorProto.create(this)
        rangeErrorProto = JSRangeErrorProto.create(this)
        referenceErrorProto = JSReferenceErrorProto.create(this)
        syntaxErrorProto = JSSyntaxErrorProto.create(this)
        uriErrorProto = JSURIErrorProto.create(this)

        calendarProto = JSCalendarProto.create(this)
        durationProto = JSDurationProto.create(this)
        instantProto = JSInstantProto.create(this)

        throwTypeError = JSRunnableFunction.create("", 0, this) {
            Errors.CalleePropertyAccess.throwTypeError(this)
        }
        AOs.setIntegrityLevel(throwTypeError, AOs.IntegrityLevel.Frozen)

        mathObj = JSMathObject.create(this)
        reflectObj = JSReflectObject.create(this)
        jsonObj = JSONObject.create(this)
        temporalObj = JSTemporalObject.create(this)
        consoleObj = JSConsole.create(this)

        packageProto = JSPackageProto.create(this)
        classProto = JSClassProto.create(this)
        packageObj = JSPackageObject.create(null, this)

        // These can't be in the init method of the objects due to circularity
        objectCtor.defineOwnProperty("prototype", objectProto, Descriptor.HAS_BASIC)
        functionCtor.defineOwnProperty("prototype", functionProto, Descriptor.HAS_BASIC)
        generatorFunctionCtor.defineOwnProperty("prototype", generatorFunctionProto, Descriptor.HAS_BASIC)
        asyncFunctionCtor.defineOwnProperty("prototype", asyncFunctionProto, Descriptor.HAS_BASIC)
        numberCtor.defineOwnProperty("prototype", numberProto, Descriptor.HAS_BASIC)
        bigIntCtor.defineOwnProperty("prototype", bigIntProto, Descriptor.HAS_BASIC)
        stringCtor.defineOwnProperty("prototype", stringProto, Descriptor.HAS_BASIC)
        regExpCtor.defineOwnProperty("prototype", regExpProto, Descriptor.HAS_BASIC)
        booleanCtor.defineOwnProperty("prototype", booleanProto, Descriptor.HAS_BASIC)
        symbolCtor.defineOwnProperty("prototype", symbolProto, Descriptor.HAS_BASIC)
        arrayCtor.defineOwnProperty("prototype", arrayProto, Descriptor.HAS_BASIC)
        setCtor.defineOwnProperty("prototype", setProto, Descriptor.HAS_BASIC)
        mapCtor.defineOwnProperty("prototype", mapProto, Descriptor.HAS_BASIC)
        dateCtor.defineOwnProperty("prototype", dateProto, Descriptor.HAS_BASIC)
        promiseCtor.defineOwnProperty("prototype", promiseProto, Descriptor.HAS_BASIC)

        dataViewCtor.defineOwnProperty("prototype", dataViewProto, Descriptor.HAS_BASIC)
        arrayBufferCtor.defineOwnProperty("prototype", arrayBufferProto, Descriptor.HAS_BASIC)
        typedArrayCtor.defineOwnProperty("prototype", typedArrayProto, Descriptor.HAS_BASIC)
        int8ArrayCtor.defineOwnProperty("prototype", int8ArrayProto, Descriptor.HAS_BASIC)
        uint8ArrayCtor.defineOwnProperty("prototype", uint8ArrayProto, Descriptor.HAS_BASIC)
        uint8CArrayCtor.defineOwnProperty("prototype", uint8CArrayProto, Descriptor.HAS_BASIC)
        int16ArrayCtor.defineOwnProperty("prototype", int16ArrayProto, Descriptor.HAS_BASIC)
        uint16ArrayCtor.defineOwnProperty("prototype", uint16ArrayProto, Descriptor.HAS_BASIC)
        int32ArrayCtor.defineOwnProperty("prototype", int32ArrayProto, Descriptor.HAS_BASIC)
        uint32ArrayCtor.defineOwnProperty("prototype", uint32ArrayProto, Descriptor.HAS_BASIC)
        float32ArrayCtor.defineOwnProperty("prototype", float32ArrayProto, Descriptor.HAS_BASIC)
        float64ArrayCtor.defineOwnProperty("prototype", float64ArrayProto, Descriptor.HAS_BASIC)
        bigInt64ArrayCtor.defineOwnProperty("prototype", bigInt64ArrayProto, Descriptor.HAS_BASIC)
        bigUint64ArrayCtor.defineOwnProperty("prototype", bigUint64ArrayProto, Descriptor.HAS_BASIC)

        errorCtor.defineOwnProperty("prototype", errorProto, Descriptor.HAS_BASIC)
        evalErrorCtor.defineOwnProperty("prototype", evalErrorProto, Descriptor.HAS_BASIC)
        internalErrorCtor.defineOwnProperty("prototype", internalErrorProto, Descriptor.HAS_BASIC)
        typeErrorCtor.defineOwnProperty("prototype", typeErrorProto, Descriptor.HAS_BASIC)
        rangeErrorCtor.defineOwnProperty("prototype", rangeErrorProto, Descriptor.HAS_BASIC)
        referenceErrorCtor.defineOwnProperty("prototype", referenceErrorProto, Descriptor.HAS_BASIC)
        syntaxErrorCtor.defineOwnProperty("prototype", syntaxErrorProto, Descriptor.HAS_BASIC)
        uriErrorCtor.defineOwnProperty("prototype", uriErrorProto, Descriptor.HAS_BASIC)

        calendarCtor.defineOwnProperty("prototype", calendarProto, Descriptor.HAS_BASIC)
        durationCtor.defineOwnProperty("prototype", durationProto, Descriptor.HAS_BASIC)
        instantCtor.defineOwnProperty("prototype", instantProto, Descriptor.HAS_BASIC)

        functionProto.defineOwnProperty("constructor", functionCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)

        // 3. Perform AddRestrictedFunctionProperties(realmRec.[[Intrinsics]].[[%Function.prototype%]], realmRec).
        val desc = Descriptor(JSAccessor(throwTypeError, throwTypeError), Descriptor.CONFIGURABLE)
        AOs.definePropertyOrThrow(functionProto, "caller".key(), desc)
        AOs.definePropertyOrThrow(functionProto, "arguments".key(), desc)

        // 4. Return unused.
    }

    object WellKnownSymbols {
        val asyncIterator = JSSymbol("Symbol.asyncIterator")
        val hasInstance = JSSymbol("Symbol.hasInstance")
        val isConcatSpreadable = JSSymbol("Symbol.isConcatSpreadable")
        val iterator = JSSymbol("Symbol.iterator")
        val match = JSSymbol("Symbol.match")
        val matchAll = JSSymbol("Symbol.matchAll")
        val replace = JSSymbol("Symbol.replace")
        val search = JSSymbol("Symbol.search")
        val species = JSSymbol("Symbol.species")
        val split = JSSymbol("Symbol.split")
        val toPrimitive = JSSymbol("Symbol.toPrimitive")
        val toStringTag = JSSymbol("Symbol.toStringTag")
        val unscopables = JSSymbol("Symbol.unscopables")
    }

    object InternalSymbols {
        val classInstanceFields = JSSymbol("Symbol.classInstanceFields")
        val isClassInstanceFieldInitializer = JSSymbol("Symbol.isClassInstanceFieldInitializer")
    }

    companion object {
        val globalSymbolRegistry = ConcurrentHashMap<String, JSSymbol>()

        @ECMAImpl("9.3.1")
        fun create(): Realm {
            // 1. Let realmRec be a new Realm Record.
            val realm = Realm()

            // 2. Perform CreateIntrinsics(realmRec).
            realm.createIntrinsics()

            // 3. Set realmRec.[[GlobalObject]] to undefined.
            // 4. Set realmRec.[[GlobalEnv]] to undefined.
            // 5. Set realmRec.[[TemplateMap]] to a new empty List.
            // Note: These fields are simply left as uninitialized lateinit vars instead of being
            //       explicitly set to undefined

            // 6. Return realmRec.
            return realm
        }
    }
}
