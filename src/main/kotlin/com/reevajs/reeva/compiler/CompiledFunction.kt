package com.reevajs.reeva.compiler

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.functions.JSUserFunction

abstract class CompiledFunction @JvmOverloads constructor(
    realm: Realm,
    name: String,
    prototype: JSValue = realm.functionProto,
) : JSUserFunction(
    realm,
    name,
    ThisMode.Strict,
    true,
    prototype,
) 
