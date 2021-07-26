package com.reevajs.reeva.utils

import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.JSArguments
import com.reevajs.reeva.runtime.JSValue

typealias NativeGetterSignature = (realm: Realm, thisValue: JSValue) -> JSValue
typealias NativeSetterSignature = (realm: Realm, thisValue: JSValue, value: JSValue) -> Unit
typealias NativeFunctionSignature = (realm: Realm, arguments: JSArguments) -> JSValue
