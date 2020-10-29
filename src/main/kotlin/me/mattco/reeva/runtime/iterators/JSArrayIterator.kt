package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject

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
        ) = JSArrayIterator(realm, iteratedArrayLike, arrayLikeNextIndex, arrayLikeIterationKind).also { it.init() }
    }
}
