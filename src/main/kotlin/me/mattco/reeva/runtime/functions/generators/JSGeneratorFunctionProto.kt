package me.mattco.reeva.runtime.functions.generators

import me.mattco.reeva.core.Realm
import me.mattco.reeva.runtime.objects.Descriptor
import me.mattco.reeva.runtime.objects.JSObject
import me.mattco.reeva.utils.toValue

class JSGeneratorFunctionProto(realm: Realm) : JSObject(realm, realm.functionProto) {
    override fun init() {
        super.init()

        defineOwnProperty("prototype", realm.generatorObjectProto, Descriptor.CONFIGURABLE)
        defineOwnProperty("constructor", realm.generatorFunctionCtor, Descriptor.CONFIGURABLE or Descriptor.WRITABLE)
        defineOwnProperty(Realm.`@@toStringTag`, "GeneratorFunction".toValue(), Descriptor.CONFIGURABLE)
    }

    companion object {
        fun create(realm: Realm) = JSGeneratorFunctionProto(realm).initialize()
    }
}
