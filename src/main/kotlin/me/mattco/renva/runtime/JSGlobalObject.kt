package me.mattco.renva.runtime

import me.mattco.renva.runtime.values.objects.JSObject

open class JSGlobalObject(realm: Realm) : JSObject(realm, realm.objectProto) {

}
