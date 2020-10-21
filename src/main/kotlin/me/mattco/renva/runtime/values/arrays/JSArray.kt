package me.mattco.renva.runtime.values.arrays

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.objects.JSObject

class JSArray(private val realm: Realm) : JSObject(realm, realm.arrayProto) {

}
