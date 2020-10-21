package me.mattco.renva.runtime.values.nonprimitives.arrays

import me.mattco.renva.runtime.Realm
import me.mattco.renva.runtime.values.nonprimitives.objects.JSObject

class JSArray(private val realm: Realm) : JSObject(realm, realm.arrayProto) {

}
