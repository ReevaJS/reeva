package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.builtins.Builtin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSDataViewProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.dataViewCtor, attrs { +conf - enum + writ })
        defineOwnProperty(Realm.`@@toStringTag`, "DataView".toValue(), attrs { +conf - enum - writ })

        defineBuiltinAccessor("buffer", attrs { +conf - enum }, Builtin.DataViewProtoGetBuffer)
        defineBuiltinAccessor("byteLength", attrs { +conf - enum }, Builtin.DataViewProtoGetByteLength)
        defineBuiltinAccessor("byteOffset", attrs { +conf - enum }, Builtin.DataViewProtoGetByteOffset)

        defineBuiltin("getBigInt64", 1, Builtin.DataViewProtoGetBigInt64)
        defineBuiltin("getBigUint64", 1, Builtin.DataViewProtoGetBigUint64)
        defineBuiltin("getFloat32", 1, Builtin.DataViewProtoGetFloat32)
        defineBuiltin("getFloat64", 1, Builtin.DataViewProtoGetFloat64)
        defineBuiltin("getInt8", 1, Builtin.DataViewProtoGetInt8)
        defineBuiltin("getInt16", 1, Builtin.DataViewProtoGetInt16)
        defineBuiltin("getInt32", 1, Builtin.DataViewProtoGetInt32)
        defineBuiltin("getUint8", 1, Builtin.DataViewProtoGetUint8)
        defineBuiltin("getUint16", 1, Builtin.DataViewProtoGetUint16)
        defineBuiltin("getUint32", 1, Builtin.DataViewProtoGetUint32)
        defineBuiltin("setBigInt64", 2, Builtin.DataViewProtoSetBigInt64)
        defineBuiltin("setBigUint64", 2, Builtin.DataViewProtoSetBigUint64)
        defineBuiltin("setFloat32", 2, Builtin.DataViewProtoSetFloat32)
        defineBuiltin("setFloat64", 2, Builtin.DataViewProtoSetFloat64)
        defineBuiltin("setInt8", 2, Builtin.DataViewProtoSetInt8)
        defineBuiltin("setInt16", 2, Builtin.DataViewProtoSetInt16)
        defineBuiltin("setInt32", 2, Builtin.DataViewProtoSetInt32)
        defineBuiltin("setUint8", 2, Builtin.DataViewProtoSetUint8)
        defineBuiltin("setUint16", 2, Builtin.DataViewProtoSetUint16)
        defineBuiltin("setUint32", 2, Builtin.DataViewProtoSetUint32)
    }

    companion object {
        fun create(realm: Realm) = JSDataViewProto(realm).initialize()

        @ECMAImpl("25.3.4.1")
        @JvmStatic
        fun getBuffer(realm: Realm, thisValue: JSValue): JSValue {
            if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.buffer").throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.ViewedArrayBuffer)
        }

        @ECMAImpl("25.3.4.2")
        @JvmStatic
        fun getByteLength(realm: Realm, thisValue: JSValue): JSValue {
            if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError(realm)
            if (Operations.isDetachedBuffer(thisValue.getSlotAs(SlotName.ViewedArrayBuffer)))
                Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError(realm)
            return thisValue.getSlotAs<Int>(SlotName.ByteLength).toValue()
        }

        @ECMAImpl("25.3.4.3")
        @JvmStatic
        fun getByteOffset(realm: Realm, thisValue: JSValue): JSValue {
            if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError(realm)
            if (Operations.isDetachedBuffer(thisValue.getSlotAs(SlotName.ViewedArrayBuffer)))
                Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError(realm)
            return thisValue.getSlotAs<Int>(SlotName.ByteOffset).toValue()
        }

        @ECMAImpl("25.3.4.5")
        @JvmStatic
        fun getBigInt64(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian,
                Operations.TypedArrayKind.BigInt64
            )
        }

        @ECMAImpl("25.3.4.6")
        @JvmStatic
        fun getBigUint64(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian,
                Operations.TypedArrayKind.BigUint64
            )
        }

        @ECMAImpl("25.3.4.7")
        @JvmStatic
        fun getFloat32(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Float32
            )
        }

        @ECMAImpl("25.3.4.8")
        @JvmStatic
        fun getFloat64(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Float64
            )
        }

        @ECMAImpl("25.3.4.9")
        @JvmStatic
        fun getInt8(realm: Realm, arguments: JSArguments): JSValue {
            val byteOffset = arguments.argument(0)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                JSTrue,
                Operations.TypedArrayKind.Int8
            )
        }

        @ECMAImpl("25.3.4.10")
        @JvmStatic
        fun getInt16(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Int16
            )
        }

        @ECMAImpl("25.3.4.11")
        @JvmStatic
        fun getInt32(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Int32
            )
        }

        @ECMAImpl("25.3.4.12")
        @JvmStatic
        fun getUint8(realm: Realm, arguments: JSArguments): JSValue {
            val byteOffset = arguments.argument(0)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                JSTrue,
                Operations.TypedArrayKind.Uint8
            )
        }

        @ECMAImpl("25.3.4.13")
        @JvmStatic
        fun getUint16(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Uint16
            )
        }

        @ECMAImpl("25.3.4.14")
        @JvmStatic
        fun getUint32(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Uint32
            )
        }

        @ECMAImpl("25.3.4.15")
        @JvmStatic
        fun setBigInt64(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian,
                Operations.TypedArrayKind.BigInt64,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.16")
        @JvmStatic
        fun setBigUint64(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian,
                Operations.TypedArrayKind.BigUint64,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.17")
        @JvmStatic
        fun setFloat32(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Float32,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.18")
        @JvmStatic
        fun setFloat64(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Float64,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.19")
        @JvmStatic
        fun setInt8(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value) = arguments.takeArgs(0..1)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                JSTrue,
                Operations.TypedArrayKind.Int8,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.20")
        @JvmStatic
        fun setInt16(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Int16,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.21")
        @JvmStatic
        fun setInt32(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Int32,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.22")
        @JvmStatic
        fun setUint8(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value) = arguments.takeArgs(0..1)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                JSTrue,
                Operations.TypedArrayKind.Uint8,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.23")
        @JvmStatic
        fun setUint16(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Uint16,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.24")
        @JvmStatic
        fun setUint32(realm: Realm, arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
                realm,
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Uint32,
                value
            )
            return JSUndefined
        }
    }
}
