package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

open class JSGenericTypedArrayProto(
    realm: Realm,
    private val kind: Operations.TypedArrayKind,
) : JSObject(realm.typedArrayProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty("BYTES_PER_ELEMENT", kind.size.toValue(), 0)
        defineOwnProperty(
            "constructor",
            kind.getCtor(Agent.activeAgent.getActiveRealm()),
            attrs { +conf; -enum; +writ },
        )
    }
}

class JSInt8ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Int8) {
    companion object {
        fun create(realm: Realm) = JSInt8ArrayProto(realm).initialize(realm)
    }
}

class JSUint8ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint8) {
    companion object {
        fun create(realm: Realm) = JSUint8ArrayProto(realm).initialize(realm)
    }
}

class JSUint8CArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint8C) {
    companion object {
        fun create(realm: Realm) = JSUint8CArrayProto(realm).initialize(realm)
    }
}

class JSInt16ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Int16) {
    companion object {
        fun create(realm: Realm) = JSInt16ArrayProto(realm).initialize(realm)
    }
}

class JSUint16ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint16) {
    companion object {
        fun create(realm: Realm) = JSUint16ArrayProto(realm).initialize(realm)
    }
}

class JSInt32ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Int32) {
    companion object {
        fun create(realm: Realm) = JSInt32ArrayProto(realm).initialize(realm)
    }
}

class JSUint32ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint32) {
    companion object {
        fun create(realm: Realm) = JSUint32ArrayProto(realm).initialize(realm)
    }
}

class JSFloat32ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Float32) {
    companion object {
        fun create(realm: Realm) = JSFloat32ArrayProto(realm).initialize(realm)
    }
}

class JSFloat64ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Float64) {
    companion object {
        fun create(realm: Realm) = JSFloat64ArrayProto(realm).initialize(realm)
    }
}

class JSBigInt64ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.BigInt64) {
    companion object {
        fun create(realm: Realm) = JSBigInt64ArrayProto(realm).initialize(realm)
    }
}

class JSBigUint64ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.BigUint64) {
    companion object {
        fun create(realm: Realm) = JSBigUint64ArrayProto(realm).initialize(realm)
    }
}
