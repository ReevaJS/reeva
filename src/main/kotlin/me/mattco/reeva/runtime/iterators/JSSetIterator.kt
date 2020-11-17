package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.builtins.JSSetObject
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.expect

class JSSetIterator private constructor(
    realm: Realm,
    set: JSSetObject,
    val iterationKind: PropertyKind
) : JSObject(realm, realm.setIteratorProto) {
    var iteratedSet: JSSetObject? = set
    var nextIndex = 0

    init {
        expect(iterationKind != PropertyKind.Key)
        set.iterationCount++
    }

    companion object {
        fun create(realm: Realm, set: JSSetObject, kind: PropertyKind) = JSSetIterator(realm, set, kind).initialize()
    }
}
