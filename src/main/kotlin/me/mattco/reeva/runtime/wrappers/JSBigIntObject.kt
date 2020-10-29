package me.mattco.reeva.runtime.wrappers

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.runtime.primitives.JSBigInt

// TODO
class JSBigIntObject(realm: Realm, val value: JSBigInt) : JSObject(realm)
