package com.reevajs.reeva.utils

import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.collections.JSArguments

typealias NativeGetterSignature = (thisValue: JSValue) -> JSValue
typealias NativeSetterSignature = (thisValue: JSValue, value: JSValue) -> Unit
typealias NativeFunctionSignature = (arguments: JSArguments) -> JSValue
