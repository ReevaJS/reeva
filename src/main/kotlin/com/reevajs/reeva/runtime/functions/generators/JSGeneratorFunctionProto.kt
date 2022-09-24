package com.reevajs.reeva.runtime.functions.generators

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.objects.Descriptor
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.utils.toValue

class JSGeneratorFunctionProto(realm: Realm) : JSObject(realm.functionProto) {
    override fun init(realm: Realm) {
        super.init(realm)

        defineOwnProperty("prototype", realm.generatorObjectProto, Descriptor.CONFIGURABLE)
        defineOwnProperty("constructor", realm.generatorFunctionCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.WellKnownSymbols.toStringTag, "GeneratorFunction".toValue(), Descriptor.CONFIGURABLE)
    }

    companion object {
        fun create(realm: Realm) = JSGeneratorFunctionProto(realm).initialize(realm)
    }
}
