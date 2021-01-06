package me.mattco.reeva.runtime.iterators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.builtins.JSSetObject
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.expect

class JSSetIterator private constructor(
    realm: Realm,
    data: JSSetObject.SetData,
    val iterationKind: PropertyKind
) : JSObject(realm, realm.setIteratorProto) {
    var iteratedSet: JSSetObject.SetData? = data
    var nextIndex = 0

    init {
        expect(iterationKind != PropertyKind.Key)
        data.iterationCount++
    }

    companion object {
        fun create(realm: Realm, data: JSSetObject.SetData, kind: PropertyKind) = JSSetIterator(realm, data, kind).initialize()
    }
}
