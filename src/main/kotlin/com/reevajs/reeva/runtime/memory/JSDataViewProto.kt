package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSDataViewProto private constructor(realm: Realm) : JSObject(realm.objectProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty("constructor", realm.dataViewCtor, attrs { +conf; -enum; +writ })
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "DataView".toValue(), attrs { +conf; -enum; -writ })

        defineBuiltinGetter(realm, "buffer", ::getBuffer, attrs { +conf; -enum })
        defineBuiltinGetter(realm, "byteLength", ::getByteLength, attrs { +conf; -enum })
        defineBuiltinGetter(realm, "byteOffset", ::getByteOffset, attrs { +conf; -enum })

        defineBuiltin(realm, "getBigInt64", 1, ::getBigInt64)
        defineBuiltin(realm, "getBigUint64", 1, ::getBigUint64)
        defineBuiltin(realm, "getFloat32", 1, ::getFloat32)
        defineBuiltin(realm, "getFloat64", 1, ::getFloat64)
        defineBuiltin(realm, "getInt8", 1, ::getInt8)
        defineBuiltin(realm, "getInt16", 1, ::getInt16)
        defineBuiltin(realm, "getInt32", 1, ::getInt32)
        defineBuiltin(realm, "getUint8", 1, ::getUint8)
        defineBuiltin(realm, "getUint16", 1, ::getUint16)
        defineBuiltin(realm, "getUint32", 1, ::getUint32)
        defineBuiltin(realm, "setBigInt64", 2, ::setBigInt64)
        defineBuiltin(realm, "setBigUint64", 2, ::setBigUint64)
        defineBuiltin(realm, "setFloat32", 2, ::setFloat32)
        defineBuiltin(realm, "setFloat64", 2, ::setFloat64)
        defineBuiltin(realm, "setInt8", 2, ::setInt8)
        defineBuiltin(realm, "setInt16", 2, ::setInt16)
        defineBuiltin(realm, "setInt32", 2, ::setInt32)
        defineBuiltin(realm, "setUint8", 2, ::setUint8)
        defineBuiltin(realm, "setUint16", 2, ::setUint16)
        defineBuiltin(realm, "setUint32", 2, ::setUint32)
    }

    companion object {
        fun create(realm: Realm) = JSDataViewProto(realm).initialize(realm)

        @ECMAImpl("25.3.4.1")
        @JvmStatic
        fun getBuffer(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.buffer").throwTypeError()
            return thisValue.getSlot(SlotName.ViewedArrayBuffer)
        }

        @ECMAImpl("25.3.4.2")
        @JvmStatic
        fun getByteLength(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError()
            if (Operations.isDetachedBuffer(thisValue.getSlot(SlotName.ViewedArrayBuffer)))
                Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError()
            return thisValue.getSlot(SlotName.ByteLength).toValue()
        }

        @ECMAImpl("25.3.4.3")
        @JvmStatic
        fun getByteOffset(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!Operations.requireInternalSlot(thisValue, SlotName.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError()
            if (Operations.isDetachedBuffer(thisValue.getSlot(SlotName.ViewedArrayBuffer)))
                Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError()
            return thisValue.getSlot(SlotName.ByteOffset).toValue()
        }

        @ECMAImpl("25.3.4.5")
        @JvmStatic
        fun getBigInt64(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian,
                Operations.TypedArrayKind.BigInt64
            )
        }

        @ECMAImpl("25.3.4.6")
        @JvmStatic
        fun getBigUint64(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian,
                Operations.TypedArrayKind.BigUint64
            )
        }

        @ECMAImpl("25.3.4.7")
        @JvmStatic
        fun getFloat32(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Float32
            )
        }

        @ECMAImpl("25.3.4.8")
        @JvmStatic
        fun getFloat64(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Float64
            )
        }

        @ECMAImpl("25.3.4.9")
        @JvmStatic
        fun getInt8(arguments: JSArguments): JSValue {
            val byteOffset = arguments.argument(0)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                JSTrue,
                Operations.TypedArrayKind.Int8
            )
        }

        @ECMAImpl("25.3.4.10")
        @JvmStatic
        fun getInt16(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Int16
            )
        }

        @ECMAImpl("25.3.4.11")
        @JvmStatic
        fun getInt32(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Int32
            )
        }

        @ECMAImpl("25.3.4.12")
        @JvmStatic
        fun getUint8(arguments: JSArguments): JSValue {
            val byteOffset = arguments.argument(0)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                JSTrue,
                Operations.TypedArrayKind.Uint8
            )
        }

        @ECMAImpl("25.3.4.13")
        @JvmStatic
        fun getUint16(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Uint16
            )
        }

        @ECMAImpl("25.3.4.14")
        @JvmStatic
        fun getUint32(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return Operations.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                Operations.TypedArrayKind.Uint32
            )
        }

        @ECMAImpl("25.3.4.15")
        @JvmStatic
        fun setBigInt64(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setBigUint64(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setFloat32(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setFloat64(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setInt8(arguments: JSArguments): JSValue {
            val (byteOffset, value) = arguments.takeArgs(0..1)
            Operations.setViewValue(
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
        fun setInt16(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setInt32(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setUint8(arguments: JSArguments): JSValue {
            val (byteOffset, value) = arguments.takeArgs(0..1)
            Operations.setViewValue(
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
        fun setUint16(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
        fun setUint32(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            Operations.setViewValue(
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
