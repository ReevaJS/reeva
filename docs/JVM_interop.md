# JVM Interop

Being a JVM library, JVM interop is an import part of Reeva. Users can leverage ES6 features such as class declarations 
and import statements to interact with the JVM in a much more ergonomic way than in other engines such as Nashorn or 
Rhino. This document will serve to do two things:

- Demontrate the JVM interop capabilities
- Explain how the implementation of some of the more complicated features (i.e. inheriting from JVM classes) works in 
  depth.

## Importing JVM Classes

In order to use a JVM class, you first need a reference to it. There are a few ways to do this:

1. Import statements
2. Using the global `Packages` object
3. (TODO) If the root package of the path is a common one, there may be a global for it. Packages that are available in 
   the global namespaces are: `java`, `sun`, `com`, `net`, `org` (TODO: Any others? What does Rhino do here?)

For example, here are all the different ways to import `StringBuilder`:

```js
// Default import from class
import StringBuilder from 'jvm:java.lang.StringBuilder';

// Named import from package
import { StringBuilder } from 'jvm:java.lang';

// Using the Packages builtin
const StringBuilder = Packages.java.lang.StringBuilder;

// Using the java global object
const StringBuilder = java.lang.StringBuilder;
```

Note that when using `import` syntax, the module specifier must be prefaced with `jvm:`. This informes the module system
that it isn't looking for a file named `java.lang.StringBuilder.js` in the current directory. 

## Using JVM Classes

Using JVM classes isn't too different than using JS classes:

```js
import { StringBuilder, System } from 'jvm:java.lang';

const builder = new StringBuilder();     // Instantiate with 'new' keyword
builder.append("hello world!");          // Pass arguments like normal, they are converted to
                                         // JVM objects automatically
builder.append([1, 2, 3]);               // Even complex objects are converted. This will turn into a 
                                         // List<Object> (containing Integers)
System.out.println(builder.toString());  // Access static properties like normal as well
```

So how does this work? Internally, JVM classes (i.e. the objects given from a JVM import statement) hold a reference to 
their associated `Class<*>` objects. When you use the `new` operator on them, they instantiate the class using 
reflection -- specifically, the `Class<*>.newInstance` method. The object that is returned is wrapped in another object 
and given to the user.

An important difference here from other engines is that these objects _have their own prototypes_. This means that, much
like other JS objects, you can assign or remove properties from them as normal. This is key, since it means that many 
standard library APIs will work just fine with these objects (TODO: verify this claim). 

## Extending JVM classes

As mentioned previously, inherited classes can derive directly from Java classes and interfaces. This is done using the ES6 syntax that everyone is so familiar with. For the same of this example, let's work with the follow completely useless Java class and interface:

```java
package com.example;

public abstract class MyClass {
    public static int staticProperty = 10;

    protected int a;
    protected int b;

    public MyClass(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public abstract int getValue();
}
```

```java
package com.example;

public interface MyInterface {
    void doThing();
}
```

And now lets see how to extend these from the JS side:

```js
import { MyClass, MyInterface } from 'jvm:com.example';

// When extends classes and interfaces, you must pass them to the global "jvm" function first
class MyWeirdJSClass extends jvm(MyClass, MyInterface) {
    constructor(a, b) {
        super(a, b);

        // Create a new property
        this.multiple = a * b;
    }

    // Implement method from MyClass
    getValue() {
        // Reference properties derived from the super class
        return this.a + this.b;
    }

    // Implement method from MyInterface
    doThing() {
        console.log(`multiple: ${this.multiple}`);
    }
    
    // Custom method
    doThing2() {
        console.log('hello world! ' + this.a);
    }
}

const o = new MyWeirdJSClass(10, 20);
console.log(o.getValue());       // prints "30"
o.doThing();                     // prints "multiple: 200"
console.log(o.nonExistantField); // prints "undefined" instead of throwing an error,
                                 // just like a normal JS object would do
```

## Conversion Rules

TODO

## Implementation for Extending Classes

So how does class extension work? When you extend a JVM class or interface, Reeva generates two JVM classes at runtime, 
one of which inherits from the extended class. Let's take a look at what Reeva does for the code example above. You will
want to read the [ARCHITECTURE.md](#) file first (TODO). 

_Note that for the following generated class example, I'm using nice identifiers and writing relatively sane Java code. 
The decompiled bytecode would look much worse, and the code may not be exactly the same._

The first step is generating the implementation class. This is the class that extends JVM classes or implements JVM 
interfaces and overriding any fields or methods necessary. This class would look something like the following:

```java
package com.reevajs.reeva.generated;

class MyWeirdClassJvmImpl extends MyClass implements MyInterface {
    // The Realm reference is necessary to access global variables
    private final Realm realm;

    // Discussed below
    private final JSObject wrapper;

    // Accept parameters for the super constructor. If there were multiple super constructor,
    // then multiple constructors would be generated here, each accepting the same parameters
    // in addition to the realm and wrapper.
    MyWeirdClassJvmImpl(Realm realm, JSObject wrapper, int a, int b) {
        super(a, b);

        this.realm = realm;
        this.wrapper = wrapper;
    }

    // Abstract method override
    // The implementation is centralized because there may be multiple override of the JVM method,
    // but there is of course only every one implementation on the JS side.
    @Override
    public int getValue() {
        return (int) JVMValueMapper.jsToJVM(getValueImpl(new JSArguments()), int.class);
    }

    private JSValue getValueImpl(JSArguments arguments) {
        JSValue aValue = JVMValueMapper.jvmToJS(this.a);
        JSValue bValue = JVMValueMapper.jvmToJS(this.b);
        return AOs.applyStringOrNumericBinaryOperator(aValue, bValue, "+");
    }

    // Interface method override
    @Override
    public void doThing() {
        doThingImpl(new JSArguments());
    }

    private JSValue doThingImpl(JSArguments argumentsUnused) {
        JSObject console = AOs.toObject(realm.globalEnv.get("console"));
        JSArguments arguments = new JSArguments();
        arguments.add(new JSString("multiple: "));
        arguments.add(AOs.toString(wrapper.get("multiple")));
        AOs.invoke(console, "log", arguments);
        return JSUndefined.INSTANCE;
    }
}
```

This class will be able to passed to JVM methods, but what about interacting with in on the JS side? All Reeva values must inherit from `JSValue`, so this class cannot be used by itself. Therefore, we create an instance of `JSJVMImplWrapper`, which wraps our class above. The prototype for the class is generated at runtime using reflection, however this will probably be a compiled class in the future.

Lastly, we need to generate a constructor for a class, which is the object that will be bound to the class identifier.

```java
package com.reevajs.reeva.generated;

class MyWeirdClassCtor extends JSNativeFunction {
    private JSObject associatedPrototype;

    // Store property keys which are available when compiling the class. This is important to
    // keep Symbol identities. Not strictly necessary for non-symbol property keys, but using
    // this for all keys simplifies the code generation a bit
    private List<PropertyKey> staticFieldKeys;
    private List<PropertyKey> staticMethodKeys;

    public MyWeirdClassCtor(Realm realm, JSObject associatedPrototype) {
        super(realm, "MyWeirdClass", 2);
        this.associatedPrototype = associatedPrototype;
    }

    // These methods get called by the compiled when instantiating the various classes
    public void setStaticFieldKeys(List<PropertyKey> fieldKeys) {
        this.staticFieldKeys = fieldKeys;
    }

    public void setStaticMethodKeys(List<PropertyKey> methodKeys) {
        this.staticMethodKeys = methodKeys;
    }

    @Override 
    public void init() {
        super.init();

        defineOwnProperty(this.staticFieldKeys.get(0), new JSNumber(10.0), 0);
    }

    @Override
    public JSValue evaluate(JSArguments arguments) {
        if (arguments.getThisValue() == JSUndefined.INSTANCE) {
            new Errors.CtorCallWithoutNew("MyWeirdClass").throwTypeError();
        }

        JSObject wrapper = JSObject.create(realm, proto = associatedPrototype);

        // If there are multiple constructors, this logic gets significantly more complex
        MyWeirdClassJvmImpl impl = new MyWeirdClassJvmImpl(
            realm, 
            wrapper, 
            (int) JVMValueMapper.jsToJVM(arguments.argument(0), int.class),
            (int) JVMValueMapper.jsToJVM(arguments.argument(1), int.class)
        );

        wrapper.setSlot(Slot.Impl, impl);

        return wrapper;
    }
}
```

TODO: Hash out the details of the prototype

```java
package com.example;

class MyWeirdClassProto extends JSObject {
    private List<PropertyKey> methodKeys;

    public MyWeirdClassProto(Realm realm) {
        super(realm, realm.getObjectProto());
    }

    public void setMethodKeys(List<PropertyKey> methodKeys) {
        this.methodKeys = methodKeys;
    }

    @Override
    public void init() {
        super.init();

        Function<JSArguments, JSValue> doThing2Function = new Function<JSArguments, JSValue>() {
            @Override
            public JSValue apply(JSArguments arguments) {
                if (!(arguments.thisValue instanceof JSJvmValueWrapper))
                    new Errors.IncompatibleMethodCall("MyWeirdClass", "doThing2").throwTypeError();

                Object impl = ((JSJvmValueWrapper) arguments.thisValue).impl;
                if (!(impl instanceof MyWeirdClassJvmImpl))
                    new Errors.IncompatibleMethodCall("MyWeirdClass", "doThing2").throwTypeError();
            }
        };

        defineBuiltin(this.methodKeys.get(0), 0, doThing2Function);
    }
}
```
