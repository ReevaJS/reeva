package com.reevajs.reeva.utils

import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments

typealias NativeGetterSignature = (realm: Realm, thisValue: JSValue) -> JSValue
typealias NativeSetterSignature = (realm: Realm, thisValue: JSValue, value: JSValue) -> Unit
typealias NativeFunctionSignature = (realm: Realm, arguments: JSArguments) -> JSValue
