package com.reevajs.reeva.runtime.iterators

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.objects.JSObject

class JSArrayIterator private constructor(
    realm: Realm,
    internal var iteratedArrayLike: JSObject?,
    internal var arrayLikeNextIndex: Int,
    internal val arrayLikeIterationKind: PropertyKind,
) : JSObject(realm, realm.arrayIteratorProto) {
    companion object {
        fun create(
            realm: Realm,
            iteratedArrayLike: JSObject,
            arrayLikeNextIndex: Int,
            arrayLikeIterationKind: PropertyKind
        ) = JSArrayIterator(realm, iteratedArrayLike, arrayLikeNextIndex, arrayLikeIterationKind).initialize()
    }
}
