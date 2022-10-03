package com.reevajs.reeva.runtime.temporal

import com.reevajs.reeva.core.Agent
import com.reevajs.reeva.core.Realm
import com.reevajs.reeva.runtime.annotations.ECMAImpl
import com.reevajs.reeva.runtime.collections.JSArguments
import com.reevajs.reeva.runtime.functions.JSNativeFunction
import com.reevajs.reeva.runtime.objects.JSObject
import com.reevajs.reeva.runtime.primitives.JSUndefined
import com.reevajs.reeva.runtime.temporal.TemporalAOs
import com.reevajs.reeva.runtime.JSValue
import com.reevajs.reeva.runtime.toJSString
import com.reevajs.reeva.utils.*

class JSCalendarCtor private constructor(realm: Realm) : JSNativeFunction(realm, "Calendar", 1) {
    override fun init() {
        super.init()

        defineBuiltin("from", 1, ::from)
    }

    override fun evaluate(arguments: JSArguments): JSValue {
        // 1. If NewTarget is undefined, then
        if (arguments.newTarget == JSUndefined) {
            // a. Throw a TypeError exception.
            Errors.CtorCallWithoutNew("Calendar").throwTypeError(realm)
        }

        // 2. Set id to ? ToString(id).
        val id = arguments.argument(0).toJSString().string

        // 3. If IsBuiltinCalendar(id) is false, then
        if (!TemporalAOs.isBuiltinCalendar(id)) {
            // a. Throw a RangeError exception.
            Errors.Temporal.InvalidBuiltinCalendar(id).throwRangeError() 
        }

        // 4. Return ? CreateTemporalCalendar(id, NewTarget).
        return TemporalAOs.createTemporalCalendar(id, arguments.newTarget as? JSObject)
    }

    companion object {
        fun create(realm: Realm = Agent.activeAgent.getActiveRealm()) = JSCalendarCtor(realm).initialize()

        @JvmStatic
        @ECMAImpl("12.4.2")
        fun from(arguments: JSArguments): JSValue {
            // 1. Return ? ToTemporalCalendar(calendarLike).
            return TemporalAOs.toTemporalCalendar(arguments.argument(0))
        }
    }
}