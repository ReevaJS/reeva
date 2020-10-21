package me.mattco.reeva.runtime

import me.mattco.reeva.runtime.values.objects.JSObject

open class JSGlobalObject(realm: Realm) : JSObject(realm, realm.objectProto) {

}
