package me.mattco.jsthing.runtime

import me.mattco.jsthing.runtime.values.nonprimitives.objects.JSObject

open class JSGlobalObject(realm: Realm) : JSObject(realm, realm.objectProto) {

}
