package me.mattco.reeva.utils

import me.mattco.reeva.core.Agent
import me.mattco.reeva.core.ThrowException
import me.mattco.reeva.runtime.JSValue
import me.mattco.reeva.runtime.errors.*
import me.mattco.reeva.runtime.objects.PropertyKey

open class Error(private val formatString: String) {
    fun throwEvalError(): Nothing {
        throw ThrowException(JSEvalErrorObject.create(Agent.runningContext.realm, formatString))
    }

    fun throwTypeError(): Nothing {
        throw ThrowException(JSTypeErrorObject.create(Agent.runningContext.realm, formatString))
    }

    fun throwRangeError(): Nothing {
        throw ThrowException(JSRangeErrorObject.create(Agent.runningContext.realm, formatString))
    }

    fun throwReferenceError(): Nothing {
        throw ThrowException(JSReferenceErrorObject.create(Agent.runningContext.realm, formatString))
    }

    fun throwSyntaxError(): Nothing {
        throw ThrowException(JSSyntaxErrorObject.create(Agent.runningContext.realm, formatString))
    }

    fun throwURIError(): Nothing {
        throw ThrowException(JSURIErrorObject.create(Agent.runningContext.realm, formatString))
    }
}

object Errors {
    class UnknownReference(propertyName: PropertyKey) : Error("unknown reference \"$propertyName\"")
    class InvalidLHSAssignment(lhs: String) : Error("cannot assign value to non-left-hand-side expression $lhs")
    class UnresolvableReference(propertyName: PropertyKey) : Error("cannot resolve reference \"$propertyName\"")
    class StrictModeFailedSet(propertyName: PropertyKey, base: String) : Error("unable to set property \"$propertyName\" " +
        "of object $base")
    class StrictModeFailedDelete(propertyName: PropertyKey, base: String) : Error("unable to delete property \"$propertyName\" " +
        "of object $base")
    class StrictModeMutableSet(propertyName: String) : Error("cannot set value of undeclared variable \"$propertyName\"")
    class AssignmentBeforeInitialization(propertyName: String) : Error("cannot assign to variable \"$propertyName\" " +
        "before it is initialized")
    class AssignmentToConstant(propertyName: String) : Error("cannot assign to constant variable \"$propertyName\"")
    object NoThisBinding : Error("current context has no 'this' property binding")

    class FailedToPrimitive(value: String) : Error("cannot convert $value to primitive value")
    class FailedToNumber(type: JSValue.Type) : Error("cannot convert $type to Number")
    object FailedSymbolToString : Error("cannot convert Symbol to String")
    class FailedToObject(type: JSValue.Type) : Error("cannot convert $type to Object")
    class FailedCall(value: String) : Error("cannot call value $value")

    object InstanceOfBadRHS : Error("right-hand side of \"instanceof\" operator must be a function")
    object InBadRHS : Error("right-hand side of \"in\" operator must be an object")
    class BadCtor(value: String) : Error("constructor of $value must be undefined or an object")
    object SpeciesNotCtor : Error("non-nullish value returned from Symbol.species method is not a constructor")
    class NotIterable(value: String) : Error("$value is not iterable")
    object NonObjectIterator : Error("iterator must be an object")
    object NonObjectIteratorReturn : Error("result of <iterator>.next() must be an object")
    class InvalidArrayLength(length: Any) : Error("array length $length is too large")
    object CalleePropertyAccess : Error("\"callee\" property may not be accessed on unmapped arguments object")
    class NotACtor(value: String) : Error("$value is not a constructor")
    class NotCallable(value: String) : Error("$value is not callable")
    class BadOperator(op: String, lhs: JSValue.Type, rhs: JSValue.Type) : Error("cannot apply operator $op to types $lhs and $rhs")
    object NewTargetOutsideFunc : Error("new.target accessed outside of a function")
    object SuperOutsideMethod : Error("super property accessed outside of a method")
    object SuperCallOutsideCtor : Error("super call outside of a constructor")
    class IncompatibleMethodCall(method: String) : Error("$method called on incompatible object")
    class InvalidToPrimitiveHint(hint: String) : Error("invalid @@toPrimitive hint \"$hint\". Valid values are \"string\", " +
        "\"number\", and \"default\"")
    class CtorCallWithoutNew(name: String) : Error("$name constructor cannot be called without \"new\" keyword")

    object DescriptorGetType : Error("descriptor's \"get\" property must be undefined or callable")
    object DescriptorSetType : Error("descriptor's \"set\" property must be undefined or callable")
    object DescriptorPropType : Error("descriptor cannot specify a \"get\" or \"set\" property with a \"value\" or \"writable\" property")

    class TODO(message: String) : Error("TODO: message ($message)")

    object Array {
        class CallableFirstArg(methodName: String) : Error("the first argument to Array.prototype.$methodName must be callable")
        class CopyWithinFailedSet(fromIndex: Int, toIndex: Int) : Error("unable to copy index $fromIndex to index $toIndex")
        object GrowToInvalidLength : Error("cannot increase array length beyond 2 ** 53 - 1")
        object ReduceEmptyArray : Error("cannot reduce empty array with no initial value")
    }

    object Class {
        object DerivedSuper : Error("derived class must call super() before using 'this'")
        object BadExtends : Error("class extends target must be a constructor or null")
        object BadExtendsProto : Error("class extends target must be a prototype which is an object or null")
        object BadSuperFunc : Error("super call on non-constructable super")
        object DuplicateSuperCall : Error("duplicate super() call in derived class constructor")
        object CtorRequiresNew : Error("cannot call class constructor without \"new\" keyword")
        object ReturnObjectFromDerivedCtor : Error("derived class cannot return a non-object from its constructor")
    }

    object Function {
        object CallerArgumentsAccess : Error("cannot access \"caller\" or \"arguments\" properties on functions")
        object BindNonFunction : Error("cannot bind a non-callable object")
        class NonCallable(methodName: String) : Error("Function.prototype.$methodName cannot be called with a non-callable \"this\" value")
    }

    object JSON {
        object StringifyBigInt : Error("JSON.stringify cannot serialize BigInt objects")
        object StringifyCircular : Error("JSON.stringify cannot serialize circular objects")
    }

    object JVMClass {
        object InvalidCall : Error("JVM class object must be called with \"new\"")
        class NoPublicCtors(fullName: String) : Error("JVM class $fullName has no public constructors")
        class NoValidCtor(className: String, providedTypes: List<String>) : Error("no constructor found for " +
            "class $className with types: ${providedTypes.joinToString()}")
        class AmbiguousCtors(className: String, providedTypes: List<String>) : Error("more than one applicable constructor " +
            "found for class $className with types: ${providedTypes.joinToString()}")
        class NoValidMethod(className: String, providedTypes: List<String>) : Error("no constructor found for " +
            "class $className with types: ${providedTypes.joinToString()}")
        class AmbiguousMethods(className: String, providedTypes: List<String>) : Error("more than one applicable constructor " +
            "found for class $className with types: ${providedTypes.joinToString()}")
        class IncompatibleFieldGet(className: String, fieldName: String) : Error("invalid access of JVM field $fieldName " +
            "on object which is not an instance of the JVM class $className")
        class IncompatibleStaticFieldGet(className: String, fieldName: String) : Error("invalid access of static JVM field $fieldName " +
            "on object which is not the JVM class $className")
        class IncompatibleFieldSet(className: String, fieldName: String) : Error("invalid set of JVM field $fieldName " +
            "on object which is not an instance of the JVM class $className")
        class IncompatibleStaticFieldSet(className: String, fieldName: String) : Error("invalid set of static JVM field $fieldName " +
            "on object which is not the JVM class $className")
        class IncompatibleMethodCall(className: String, methodName: String) : Error("invalid call of JVM method $methodName " +
            "on object which is not an instance of the JVM class $className")
        class IncompatibleStaticMethodCall(className: String, methodName: String) : Error("invalid call of static JVM method $methodName " +
            "on object which is not the JVM class $className")
        class InvalidFieldSet(className: String, fieldName: String, expectedType: String, receivedType: String) :
            Error("invalid set of JVM field $fieldName on class $className. Expected type: $expectedType, received type: $receivedType")
    }

    object JVMCompat {
        class InconvertibleType(value: JSValue, type: java.lang.Class<*>) : Error("$value isn't convertible to type $type")
    }

    object JVMPackage {
        object InvalidSymbolAccess : Error("Package object properties cannot be symbols")
        object InvalidNumberAccess : Error("Package object properties cannot be numbers")
        object InvalidDelete : Error("cannot delete properties on Package objects")
        object InvalidSet : Error("cannot set properties on Package objects")
        object InvalidPreventExtensions : Error("cannot prevent extensions on the Package object")
    }

    object Map {
        class CallableFirstArg(methodName: String) : Error("the first argument to Map.prototype.$methodName must be callable")
    }

    object Number {
        class InvalidRadix(radix: Int) : Error("invalid radix: $radix")
    }

    object Object {
        class AssignFailedSet(propertyName: PropertyKey) : Error("Object.assign was unable to set property \"$propertyName\" of object")
        object CreateBadArgType : Error("the first argument to Object.create must be an object or null")
        object DefinePropertiesBadArgType : Error("the first argument to Object.defineProperties must be an object")
        object DefinePropertyBadArgType : Error("the first argument to Object.defineProperty must be an object")
        object SetPrototypeOfBadArgType : Error("the second argument to Object.setPrototypeOf must be an object or null")
        object ProtoValue : Error("value of __proto__ must be an object or null")
        object DefineGetterBadArgType : Error("getter supplied to __defineGetter__ must be callable")
        object DefineSetterBadArgType : Error("getter supplied to __defineSetter__ must be callable")
    }

    object Promise {
        object CtorFirstArgCallable : Error("the first argument to the Promise constructor must be callable")
    }

    object Proxy {
        class Revoked(trapName: String) : Error("attempt to use revoked Proxy's [[$trapName]] trap")
        object CtorFirstArgType : Error("the first argument to the Proxy constructor must be an object")
        object CtorSecondArgType : Error("the second argument to the Proxy constructor must be an object")

        object GetPrototypeOf {
            object ReturnObjectOrNull : Error("proxy's [[GetPrototypeOf]] trap did not return an object or null")
            object NonExtensibleReturn : Error("proxy's [[GetPrototypeOf]] trap did not return its non-extensible target's prototype")
        }

        object SetPrototypeOf {
            object NonExtensibleReturn : Error("proxy's [[SetPrototypeOf]] trap successfully changed its non-extensible target's prototype")
        }

        object IsExtensible {
            object DifferentReturn : Error("proxy's [[IsExtensible]] trap did not return the same value as its target's [[IsExtensible]] method")
        }

        object PreventExtensions {
            object ExtensibleReturn : Error("proxy's [[PreventExtensions]] trap return true, but the target is still extensible")
        }

        object GetOwnPropertyDesc {
            object ReturnObjectOrUndefined : Error("proxy's [[GetOwnProperty]] trap did not return an object or undefined")
            class ExistingNonConf(propertyName: PropertyKey) : Error("proxy's [[GetOwnProperty]] trap reported an existing non-configurable " +
                "property \"$propertyName\" as non-existent")
            class NonExtensibleOwnProp(propertyName: PropertyKey) : Error("proxy's [[GetOwnProperty]] trap reported non-extensible target's " +
                "own-property \"$propertyName\" as non-existent")
            class NonExistentNonExtensible(propertyName: PropertyKey) : Error("proxy's [[GetOwnProperty]] trap reported non-existent " +
                "property \"$propertyName\" as existent on its non-extensible target")
            class NonExistentNonConf(propertyName: PropertyKey) : Error("proxy's [[GetOwnProperty]] trap reported non-existent property " +
                "\"$propertyName\" as non-configurable")
            class ConfAsNonConf(propertyName: PropertyKey) : Error("proxy's [[GetOwnProperty]] trap reported configurable property " +
                "\"$propertyName\" as non-configurable")
            class WritableAsNonWritable(propertyName: PropertyKey) : Error("proxy's [[GetOwnProperty]] trap reported writable property " +
                "\"$propertyName\" as non-configurable and non-writable")
        }

        object DefineOwnProperty {
            class AddToNonExtensible(propertyName: PropertyKey) : Error("proxy's [[DefineProperty]] trap added property " +
                "\"$propertyName\" to non-extensible target")
            class AddNonConf(propertyName: PropertyKey) : Error("proxy's [[DefineProperty]] trap added previously non-existent " +
                "property \"$propertyName\" to target as non-configurable")
            class IncompatibleDesc(propertyName: PropertyKey) : Error("proxy's [[DefineProperty]] trap added property " +
                "\"$propertyName\" to the target with an incompatible descriptor to the existing property")
            class ChangeConf(propertyName: PropertyKey) : Error("proxy's [[DefineProperty]] trap overwrote existing configurable " +
                "property \"$propertyName\" to be non-configurable")
            class ChangeWritable(propertyName: PropertyKey) : Error("proxy's [[DefineProperty]] trap added a non-writable property " +
                "\"$propertyName\" in place of an existing non-configurable, writable property")
        }

        object HasProperty {
            class ExistingNonConf(propertyName: PropertyKey) : Error("proxy's [[Has]] trap reported existing non-configurable " +
                "property \"$propertyName\" as non-existent")
            class ExistingNonExtensible(propertyName: PropertyKey) : Error("proxy's [[Has]] trap reported existing property " +
                "\"$propertyName\" of non-extensible target as non-existent")
        }

        object Get {
            class DifferentValue(propertyName: PropertyKey) : Error("proxy's [[Get]] trap reported a different value from the " +
                "existing non-configurable, non-writable own property \"$propertyName\"")
            class NonConfAccessor(propertyName: PropertyKey) : Error("proxy's [[Get]] trap reported a non-undefined value for " +
                "the existing non-configurable accessor property \"$propertyName\" with an undefined getter")
        }

        object Set {
            class NonConfNonWritable(propertyName: PropertyKey) : Error("proxy's [[Set]] trap changed the value of the " +
                "non-configurable, non-writable own property \"$propertyName\"")
            class NonConfAccessor(propertyName: PropertyKey) : Error("proxy's [[Set]] trap changed the value of the non-configurable " +
                "accessor property \"$propertyName\" with an undefined setter")
        }

        object Delete {
            class NonConf(propertyName: PropertyKey) : Error("proxy's [[Delete]] trap deleted non-configurable property " +
                "\"$propertyName\" from its target")
            class NonExtensible(propertyName: PropertyKey) : Error("proxy's [[Delete]] trap deleted existing property " +
                "\"$propertyName\" from its non-extensible target")
        }

        object OwnPropertyKeys {
            object DuplicateKeys : Error("proxy's [[OwnKeys]] trap reported duplicate keys")
            class NonConf(propertyName: PropertyKey) : Error("proxy's [[OwnKeys]] trap failed to report non-configurable " +
                "property \"$propertyName\"")
            class NonExtensibleMissingKey(propertyName: PropertyKey) : Error("proxy's [[OwnKeys]] trap failed to report property " +
                "\"$propertyName\" of its non-extensible target")
            object NonExtensibleExtraProp : Error("proxy's [[OwnKeys]] trap reported extra property keys for its non-extensible target")
        }

        object Construct {
            object NonObject : Error("proxy's [[Construct]] trap returned a non-object value")
        }
    }

    object Reflect {
        class FirstArgNotCallable(methodName: String) : Error("the first argument to Reflect.$methodName must be an object")
        object BadProto : Error("the prototype give to Reflect.setPrototypeOf must be an object or null")
    }

    object Set {
        class FirstArgNotCallable(methodName: String) : Error("the first argument to Set.prototype.$methodName must be an object")
        object ThisMissingAdd : Error("Set constructor expected this value to have a callable \"add\" property")
    }

    object Symbol {
        object KeyForBadArg : Error("the first argument to Symbol.keyFor must be a symbol")
    }
}
