package me.mattco.reeva.runtime.values.wrappers

import me.mattco.reeva.runtime.Realm
import me.mattco.reeva.runtime.values.objects.JSObject
import me.mattco.reeva.runtime.values.primitives.JSBigInt

// TODO
class JSBigIntObject(realm: Realm, val value: JSBigInt) : JSObject(realm)
