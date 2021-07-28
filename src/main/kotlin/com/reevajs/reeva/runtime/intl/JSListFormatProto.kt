package com.reevajs.reeva.runtime.intl

import com.ibm.icu.text.ConstrainedFieldPosition
import com.ibm.icu.text.ListFormatter
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.runtime.*
import com.reevajs.reeva.runtime.arrays.JSArrayObject
import com.reevajs.reeva.runtime.builtins.ReevaBuiltin
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.objects.SlotName
import com.reevajs.reeva.runtime.primitives.*
import com.reevajs.reeva.utils.Errors
import com.reevajs.reeva.utils.toValue

class JSListFormatProto private constructor(realm: Realm) : JSObject(realm, realm.objectProto) {
    override fun init() {
        super.init()

        defineBuiltin("format", 1, ReevaBuiltin.ListFormatProtoFormat)
        defineBuiltin("formatToParts", 1, ReevaBuiltin.ListFormatProtoFormatToParts)
        defineBuiltin("resolvedOptions", 0, ReevaBuiltin.ListFormatProtoResolvedOptions)
    }

    companion object {
        fun create(realm: Realm) = JSListFormatProto(realm).initialize()

        private fun thisListFormatter(realm: Realm, thisValue: JSValue, method: String): ListFormatter {
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedListFormat))
                Errors.IncompatibleMethodCall(method).throwTypeError(realm)
            return thisValue.getSlotAs(SlotName.ListFormatter)
        }

        @JvmStatic
        fun format(realm: Realm, arguments: JSArguments): JSValue {
            val listFormatter = thisListFormatter(realm, arguments.thisValue, "Intl.ListFormat.prototype.format")
            val stringList = stringListFromIterable(realm, arguments.argument(0), "format")
            return listFormatter.format(stringList).toValue()
        }

        @JvmStatic
        fun formatToParts(realm: Realm, arguments: JSArguments): JSValue {
            val listFormatter = thisListFormatter(realm, arguments.thisValue, "Intl.ListFormat.prototype.format")
            val stringList = stringListFromIterable(realm, arguments.argument(0), "formatToParts")
            val formatted = listFormatter.formatToValue(stringList)

            val array = JSArrayObject.create(realm)
            val cfp = ConstrainedFieldPosition().also { it.constrainClass(ListFormatter.Field::class.java) }
            var index = 0
            val string = formatted.toString()
            while (formatted.nextPosition(cfp)) {
                val substring = string.substring(cfp.start, cfp.limit)
                array.set(index++, substring.toValue())
            }
            return array
        }

        @JvmStatic
        fun resolvedOptions(realm: Realm, arguments: JSArguments): JSValue {
            val thisValue = arguments.thisValue
            if (thisValue !is JSObject || !thisValue.hasSlot(SlotName.InitializedListFormat))
                Errors.IncompatibleMethodCall("Intl.ListFormat.prototype.resolvedOptions").throwTypeError(realm)
            val listFormatter = thisValue.getSlotAs<ListFormatter>(SlotName.ListFormatter)
            val type = thisValue.getSlotAs<ListFormatter>(SlotName.ListFormatterType)
            val style = thisValue.getSlotAs<ListFormatter>(SlotName.ListFormatterStyle)
            val obj = JSObject.create(realm)
            obj.set("locale", listFormatter.locale.toLanguageTag().toValue())
            obj.set("type", type.toValue())
            obj.set("style", style.toValue())
            return obj
        }

        private fun stringListFromIterable(realm: Realm, iterable: JSValue, methodName: String): List<String> {
            if (iterable == JSUndefined)
                return emptyList()

            val iterator = Operations.getIterator(realm, iterable)
            val list = mutableListOf<String>()
            while (true) {
                val next = Operations.iteratorStep(iterator)
                if (next == JSFalse)
                    break
                val nextValue = Operations.iteratorValue(next)
                if (nextValue !is JSString) {
                    Operations.iteratorClose(iterator, JSEmpty)
                    Errors.Intl.ListFormat.FormatNonString(methodName, Operations.typeofOperator(nextValue)).throwTypeError(realm)
                }
                list.add(nextValue.string)
            }
            return list
        }
    }
}