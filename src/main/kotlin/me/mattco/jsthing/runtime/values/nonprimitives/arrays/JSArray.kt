package me.mattco.jsthing.runtime.values.nonprimitives.arrays

import me.mattco.jsthing.runtime.Realm
import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

class JSArray(private val realm: Realm) : JSObject(realm, realm.arrayProto) {

}
