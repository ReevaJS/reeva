package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.AOs
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.Slot
import com.reevajs.reeva.runtime.primitives.JSFalse
import com.reevajs.reeva.runtime.primitives.JSTrue
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

class JSDataViewProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineOwnProperty("constructor", realm.dataViewCtor, attrs { +conf; -enum; +writ })
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "DataView".toValue(), attrs { +conf; -enum; -writ })

        defineBuiltinGetter("buffer", ::getBuffer, attrs { +conf; -enum })
        defineBuiltinGetter("byteLength", ::getByteLength, attrs { +conf; -enum })
        defineBuiltinGetter("byteOffset", ::getByteOffset, attrs { +conf; -enum })

        defineBuiltin("getBigInt64", 1, ::getBigInt64)
        defineBuiltin("getBigUint64", 1, ::getBigUint64)
        defineBuiltin("getFloat32", 1, ::getFloat32)
        defineBuiltin("getFloat64", 1, ::getFloat64)
        defineBuiltin("getInt8", 1, ::getInt8)
        defineBuiltin("getInt16", 1, ::getInt16)
        defineBuiltin("getInt32", 1, ::getInt32)
        defineBuiltin("getUint8", 1, ::getUint8)
        defineBuiltin("getUint16", 1, ::getUint16)
        defineBuiltin("getUint32", 1, ::getUint32)
        defineBuiltin("setBigInt64", 2, ::setBigInt64)
        defineBuiltin("setBigUint64", 2, ::setBigUint64)
        defineBuiltin("setFloat32", 2, ::setFloat32)
        defineBuiltin("setFloat64", 2, ::setFloat64)
        defineBuiltin("setInt8", 2, ::setInt8)
        defineBuiltin("setInt16", 2, ::setInt16)
        defineBuiltin("setInt32", 2, ::setInt32)
        defineBuiltin("setUint8", 2, ::setUint8)
        defineBuiltin("setUint16", 2, ::setUint16)
        defineBuiltin("setUint32", 2, ::setUint32)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSDataViewProto(realm).initialize()

        @ECMAImpl("25.3.4.1")
        @JvmStatic
        fun getBuffer(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.buffer").throwTypeError()
            return thisValue[Slot.ViewedArrayBuffer]
        }

        @ECMAImpl("25.3.4.2")
        @JvmStatic
        fun getByteLength(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError()
            if (AOs.isDetachedBuffer(thisValue[Slot.ViewedArrayBuffer]))
                Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError()
            return thisValue[Slot.ByteLength].toValue()
        }

        @ECMAImpl("25.3.4.3")
        @JvmStatic
        fun getByteOffset(arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (!AOs.requireInternalSlot(thisValue, Slot.DataView))
                Errors.IncompatibleMethodCall("DataView.prototype.byteLength").throwTypeError()
            if (AOs.isDetachedBuffer(thisValue[Slot.ViewedArrayBuffer]))
                Errors.TODO("DataView.prototype.byteLength isDetachedBuffer").throwTypeError()
            return thisValue[Slot.ByteOffset].toValue()
        }

        @ECMAImpl("25.3.4.5")
        @JvmStatic
        fun getBigInt64(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian,
                AOs.TypedArrayKind.BigInt64
            )
        }

        @ECMAImpl("25.3.4.6")
        @JvmStatic
        fun getBigUint64(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian,
                AOs.TypedArrayKind.BigUint64
            )
        }

        @ECMAImpl("25.3.4.7")
        @JvmStatic
        fun getFloat32(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Float32
            )
        }

        @ECMAImpl("25.3.4.8")
        @JvmStatic
        fun getFloat64(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Float64
            )
        }

        @ECMAImpl("25.3.4.9")
        @JvmStatic
        fun getInt8(arguments: JSArguments): JSValue {
            val byteOffset = arguments.argument(0)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                JSTrue,
                AOs.TypedArrayKind.Int8
            )
        }

        @ECMAImpl("25.3.4.10")
        @JvmStatic
        fun getInt16(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Int16
            )
        }

        @ECMAImpl("25.3.4.11")
        @JvmStatic
        fun getInt32(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Int32
            )
        }

        @ECMAImpl("25.3.4.12")
        @JvmStatic
        fun getUint8(arguments: JSArguments): JSValue {
            val byteOffset = arguments.argument(0)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                JSTrue,
                AOs.TypedArrayKind.Uint8
            )
        }

        @ECMAImpl("25.3.4.13")
        @JvmStatic
        fun getUint16(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Uint16
            )
        }

        @ECMAImpl("25.3.4.14")
        @JvmStatic
        fun getUint32(arguments: JSArguments): JSValue {
            val (byteOffset, littleEndian) = arguments.takeArgs(0..1)
            return AOs.getViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Uint32
            )
        }

        @ECMAImpl("25.3.4.15")
        @JvmStatic
        fun setBigInt64(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian,
                AOs.TypedArrayKind.BigInt64,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.16")
        @JvmStatic
        fun setBigUint64(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian,
                AOs.TypedArrayKind.BigUint64,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.17")
        @JvmStatic
        fun setFloat32(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Float32,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.18")
        @JvmStatic
        fun setFloat64(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Float64,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.19")
        @JvmStatic
        fun setInt8(arguments: JSArguments): JSValue {
            val (byteOffset, value) = arguments.takeArgs(0..1)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                JSTrue,
                AOs.TypedArrayKind.Int8,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.20")
        @JvmStatic
        fun setInt16(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Int16,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.21")
        @JvmStatic
        fun setInt32(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Int32,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.22")
        @JvmStatic
        fun setUint8(arguments: JSArguments): JSValue {
            val (byteOffset, value) = arguments.takeArgs(0..1)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                JSTrue,
                AOs.TypedArrayKind.Uint8,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.23")
        @JvmStatic
        fun setUint16(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Uint16,
                value
            )
            return JSUndefined
        }

        @ECMAImpl("25.3.4.24")
        @JvmStatic
        fun setUint32(arguments: JSArguments): JSValue {
            val (byteOffset, value, littleEndian) = arguments.takeArgs(0..2)
            AOs.setViewValue(
                arguments.thisValue,
                byteOffset,
                littleEndian.ifUndefined(JSFalse),
                AOs.TypedArrayKind.Uint32,
                value
            )
            return JSUndefined
        }
    }
}
