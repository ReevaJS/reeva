package me.mattco.reeva.runtime.memory

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.Operations
import me.mattco.reeva.runtime.SlotName
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSFalse
import me.mattco.reeva.runtime.primitives.JSTrue
import me.mattco.reeva.runtime.primitives.JSUndefined
import me.mattco.reeva.utils.Errors
import me.mattco.reeva.utils.attrs
import me.mattco.reeva.utils.toValue

class JSDataViewProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.dataViewCtor, attrs { +conf -enum +writ })
        defineOwnProperty(Realm.`@@toStringTag`, "DataView".toValue(), attrs { +conf -enum -writ })

        defineNativeAccessor("buffer", attrs { +conf -enum }, ::getBuffer)
        defineNativeAccessor("byteLength", attrs { +conf -enum }, ::getByteLength)
        defineNativeAccessor("byteOffset", attrs { +conf -enum }, ::getByteOffset)

        defineNativeFunction("getBigInt64", 1, ::getBigInt64)
        defineNativeFunction("getBigUint64", 1, ::getBigUint64)
        defineNativeFunction("getFloat32", 1, ::getFloat32)
        defineNativeFunction("getFloat64", 1, ::getFloat64)
        defineNativeFunction("getInt8", 1, ::getInt8)
        defineNativeFunction("getInt16", 1, ::getInt16)
        defineNativeFunction("getInt32", 1, ::getInt32)
        defineNativeFunction("getUint8", 1, ::getUint8)
        defineNativeFunction("getUint16", 1, ::getUint16)
        defineNativeFunction("getUint32", 1, ::getUint32)
        defineNativeFunction("setBigInt64", 2, ::setBigInt64)
        defineNativeFunction("setBigUint64", 2, ::setBigUint64)
        defineNativeFunction("setFloat32", 2, ::setFloat32)
        defineNativeFunction("setFloat64", 2, ::setFloat64)
        defineNativeFunction("setInt8", 2, ::setInt8)
        defineNativeFunction("setInt16", 2, ::setInt16)
        defineNativeFunction("setInt32", 2, ::setInt32)
        defineNativeFunction("setUint8", 2, ::setUint8)
        defineNativeFunction("setUint16", 2, ::setUint16)
        defineNativeFunction("setUint32", 2, ::setUint32)

    }

    private fun getBuffer(realm: Realm, thisValue: JSValue): JSValue {
        if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
            Errors.IncompatibleMethodCall("DataView.prototype.buffer").throwTypeError(realm)
        return thisValue.getSlotAs(SlotName.ViewedArrayBuffer)
    }

    private fun getByteLength(realm: Realm, thisValue: JSValue): JSValue {
        if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
            Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError(realm)
        if (Operations.isDetachedBuffer(thisValue.getSlotAs(SlotName.ViewedArrayBuffer)))
            Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError(realm)
        return thisValue.getSlotAs<Int>(SlotName.ByteLength).toValue()
    }

    private fun getByteOffset(realm: Realm, thisValue: JSValue): JSValue {
        if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
            Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError(realm)
        if (Operations.isDetachedBuffer(thisValue.getSlotAs(SlotName.ViewedArrayBuffer)))
            Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError(realm)
        return thisValue.getSlotAs<Int>(SlotName.ByteOffset).toValue()
    }

    private fun getBigInt64(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian, Operations.TypedArrayKind.BigInt64)
    }

    private fun getBigUint64(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian, Operations.TypedArrayKind.BigUint64)
    }

    private fun getFloat32(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Float32)
    }

    private fun getFloat64(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Float64)
    }

    private fun getInt8(realm: Realm, arguments: JSArguments): JSValue {
        val byteOffset = arguments.argument(0)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, JSTrue, Operations.TypedArrayKind.Int8)
    }

    private fun getInt16(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Int16)
    }

    private fun getInt32(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Int32)
    }

    private fun getUint8(realm: Realm, arguments: JSArguments): JSValue {
        val byteOffset = arguments.argument(0)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, JSTrue, Operations.TypedArrayKind.Uint8)
    }

    private fun getUint16(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Uint16)
    }

    private fun getUint32(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
        return Operations.getViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Uint32)
    }

    private fun setBigInt64(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian, Operations.TypedArrayKind.BigInt64, value)
        return JSUndefined
    }

    private fun setBigUint64(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian, Operations.TypedArrayKind.BigUint64, value)
        return JSUndefined
    }

    private fun setFloat32(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Float32, value)
        return JSUndefined
    }

    private fun setFloat64(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Float64, value)
        return JSUndefined
    }

    private fun setInt8(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value) = arguments.takeArgs(0..1)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, JSTrue, Operations.TypedArrayKind.Int8, value)
        return JSUndefined
    }

    private fun setInt16(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Int16, value)
        return JSUndefined
    }

    private fun setInt32(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Int32, value)
        return JSUndefined
    }

    private fun setUint8(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value) = arguments.takeArgs(0..1)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, JSTrue, Operations.TypedArrayKind.Uint8, value)
        return JSUndefined
    }

    private fun setUint16(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Uint16, value)
        return JSUndefined
    }

    private fun setUint32(realm: Realm, arguments: JSArguments): JSValue {
        val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
        Operations.setViewValue(realm, arguments.thisValue, byteOffset, littleEndian.ifUndefined(JSFalse), Operations.TypedArrayKind.Uint32, value)
        return JSUndefined
    }

    companion object {
        fun create(realm: Realm) = JSDataViewProto(realm).initialize()
    }
}
