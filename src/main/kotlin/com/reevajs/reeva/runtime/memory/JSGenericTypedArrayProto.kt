package com.reevajs.reeva.runtime.memory

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.Operations
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.attrs
import com.reevajs.reeva.utils.toValue

open class JSGenericTypedArrayProto(
    realm: Realm,
    private val kind: Operations.TypedArrayKind,
) : JSObject(realm, realm.typedArrayProto) {
    override fun init() {
        super.init()

        defineOwnProperty("BYTES_PER_ELEMENT", kind.size.toValue(), 0)
        defineOwnProperty("constructor", kind.getCtor(), attrs { +conf; -enum; +writ })
    }
}

class JSInt8ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Int8) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSInt8ArrayProto(realm).initialize()
    }
}

class JSUint8ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint8) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint8ArrayProto(realm).initialize()
    }
}

class JSUint8CArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint8C) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint8CArrayProto(realm).initialize()
    }
}

class JSInt16ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Int16) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSInt16ArrayProto(realm).initialize()
    }
}

class JSUint16ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint16) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint16ArrayProto(realm).initialize()
    }
}

class JSInt32ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Int32) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSInt32ArrayProto(realm).initialize()
    }
}

class JSUint32ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Uint32) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSUint32ArrayProto(realm).initialize()
    }
}

class JSFloat32ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Float32) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFloat32ArrayProto(realm).initialize()
    }
}

class JSFloat64ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.Float64) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSFloat64ArrayProto(realm).initialize()
    }
}

class JSBigInt64ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.BigInt64) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBigInt64ArrayProto(realm).initialize()
    }
}

class JSBigUint64ArrayProto(realm: Realm) : JSGenericTypedArrayProto(realm, Operations.TypedArrayKind.BigUint64) {
    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSBigUint64ArrayProto(realm).initialize()
    }
}
