package me.mattco.reeva.utils

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.JSArguments
import me.mattco.reeva.runtime.JSValue

typealias NativeGetterSignature = (realm: Realm, thisValue: JSValue) -> JSValue
typealias NativeSetterSignature = (realm: Realm, thisValue: JSValue, value: JSValue) -> Unit
typealias NativeFunctionSignature = (realm: Realm, arguments: JSArguments) -> JSValue
