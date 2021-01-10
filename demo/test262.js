// Copyright (C) 2017 Ecma International.  All rights reserved.
// This code is governed by the BSD license found in the LICENSE file.
/*---
description: |
    Collection of assertion functions used throughout test262
defines: [assert]
---*/


function assert(mustBeTrue, message) {
    if (mustBeTrue === true) {
        return;
    }

    if (message === undefined) {
        message = 'Expected true but got ' + assert._toString(mustBeTrue);
    }
    $ERROR(message);
}

assert._isSameValue = function (a, b) {
    if (a === b) {
        // Handle +/-0 vs. -/+0
        return a !== 0 || 1 / a === 1 / b;
    }

    // Handle NaN vs. NaN
    return a !== a && b !== b;
};

assert.sameValue = function (actual, expected, message) {
    try {
        if (assert._isSameValue(actual, expected)) {
            return;
        }
    } catch (error) {
        $ERROR(message + ' (_isSameValue operation threw) ' + error);
        return;
    }

    if (message === undefined) {
        message = '';
    } else {
        message += ' ';
    }

    message += 'Expected SameValue(«' + assert._toString(actual) + '», «' + assert._toString(expected) + '») to be true';

    $ERROR(message);
};

assert.notSameValue = function (actual, unexpected, message) {
    if (!assert._isSameValue(actual, unexpected)) {
        return;
    }

    if (message === undefined) {
        message = '';
    } else {
        message += ' ';
    }

    message += 'Expected SameValue(«' + assert._toString(actual) + '», «' + assert._toString(unexpected) + '») to be false';

    $ERROR(message);
};

assert.throws = function (expectedErrorConstructor, func, message) {
    if (typeof func !== "function") {
        $ERROR('assert.throws requires two arguments: the error constructor ' +
            'and a function to run');
        return;
    }
    if (message === undefined) {
        message = '';
    } else {
        message += ' ';
    }

    try {
        func();
    } catch (thrown) {
        if (typeof thrown !== 'object' || thrown === null) {
            message += 'Thrown value was not an object!';
            $ERROR(message);
        } else if (thrown.constructor !== expectedErrorConstructor) {
            message += 'Expected a ' + expectedErrorConstructor.name + ' but got a ' + thrown.constructor.name;
            $ERROR(message);
        }
        return;
    }

    message += 'Expected a ' + expectedErrorConstructor.name + ' to be thrown but no exception was thrown at all';
    $ERROR(message);
};

assert._toString = function (value) {
    try {
        if (value === 0 && 1 / value === -Infinity) {
            return '-0';
        }

        return String(value);
    } catch (err) {
        if (err.name === 'TypeError') {
            return Object.prototype.toString.call(value);
        }

        throw err;
    }
};

// Copyright (C) 2017 André Bargull. All rights reserved.
// This code is governed by the BSD license found in the LICENSE file.

/*---
description: |
    Test if a given function is a constructor function.
defines: [isConstructor]
---*/

function isConstructor(f) {
    try {
        Reflect.construct(function(){}, [], f);
    } catch (e) {
        return false;
    }
    return true;
}


// Copyright (c) 2012 Ecma International.  All rights reserved.
// This code is governed by the BSD license found in the LICENSE file.
/*---
description: |
    Provides both:

    - An error class to avoid false positives when testing for thrown exceptions
    - A function to explicitly throw an exception using the Test262Error class
defines: [Test262Error, $ERROR, $DONOTEVALUATE]
---*/


function Test262Error(message) {
    this.message = message || "";
}

Test262Error.prototype.toString = function () {
    return "Test262Error: " + this.message;
};

Test262Error.thrower = (...args) => {
    throw new Test262Error(...args);
};

var $ERROR = Test262Error.thrower;

function $DONOTEVALUATE() {
    throw "Test262: This statement should not be evaluated.";
}

// Copyright (C) 2017 Ecma International.  All rights reserved.
// This code is governed by the BSD license found in the LICENSE file.
/*---
description: |
    Collection of functions used to safely verify the correctness of
    property descriptors.
defines:
  - verifyProperty
  - verifyEqualTo
  - verifyWritable
  - verifyNotWritable
  - verifyEnumerable
  - verifyNotEnumerable
  - verifyConfigurable
  - verifyNotConfigurable
---*/

// @ts-check

/**
 * @param {object} obj
 * @param {string|symbol} name
 * @param {PropertyDescriptor|undefined} desc
 * @param {object} [options]
 * @param {boolean} [options.restore]
 */
function verifyProperty(obj, name, desc, options) {
    assert(
        arguments.length > 2,
        'verifyProperty should receive at least 3 arguments: obj, name, and descriptor'
    );


    var originalDesc = Object.getOwnPropertyDescriptor(obj, name);
    var nameStr = String(name);

    // Allows checking for undefined descriptor if it's explicitly given.
    if (desc === undefined) {
        assert.sameValue(
            originalDesc,
            undefined,
            "obj['" + nameStr + "'] descriptor should be undefined"
        );

        // desc and originalDesc are both undefined, problem solved;
        return true;
    }

    assert(
        Object.prototype.hasOwnProperty.call(obj, name),
        "obj should have an own property " + nameStr
    );

    assert.notSameValue(
        desc,
        null,
        "The desc argument should be an object or undefined, null"
    );

    assert.sameValue(
        typeof desc,
        "object",
        "The desc argument should be an object or undefined, " + String(desc)
    );

    var failures = [];

    if (Object.prototype.hasOwnProperty.call(desc, 'value')) {
        if (!isSameValue(desc.value, originalDesc.value)) {
            failures.push("descriptor value should be " + desc.value);
        }
    }

    if (Object.prototype.hasOwnProperty.call(desc, 'enumerable')) {
        if (desc.enumerable !== originalDesc.enumerable ||
            desc.enumerable !== isEnumerable(obj, name)) {
            failures.push('descriptor should ' + (desc.enumerable ? '' : 'not ') + 'be enumerable');
        }
    }

    if (Object.prototype.hasOwnProperty.call(desc, 'writable')) {
        if (desc.writable !== originalDesc.writable ||
            desc.writable !== isWritable(obj, name)) {
            failures.push('descriptor should ' + (desc.writable ? '' : 'not ') + 'be writable');
        }
    }

    if (Object.prototype.hasOwnProperty.call(desc, 'configurable')) {
        if (desc.configurable !== originalDesc.configurable ||
            desc.configurable !== isConfigurable(obj, name)) {
            failures.push('descriptor should ' + (desc.configurable ? '' : 'not ') + 'be configurable');
        }
    }

    assert(!failures.length, failures.join('; '));

    if (options && options.restore) {
        Object.defineProperty(obj, name, originalDesc);
    }

    return true;
}

function isConfigurable(obj, name) {
    var hasOwnProperty = Object.prototype.hasOwnProperty;
    try {
        delete obj[name];
    } catch (e) {
        if (!(e instanceof TypeError)) {
            $ERROR("Expected TypeError, got " + e);
        }
    }
    return !hasOwnProperty.call(obj, name);
}

function isEnumerable(obj, name) {
    var stringCheck = false;

    if (typeof name === "string") {
        for (var x in obj) {
            if (x === name) {
                stringCheck = true;
                break;
            }
        }
    } else {
        // skip it if name is not string, works for Symbol names.
        stringCheck = true;
    }

    return stringCheck &&
        Object.prototype.hasOwnProperty.call(obj, name) &&
        Object.prototype.propertyIsEnumerable.call(obj, name);
}

function isSameValue(a, b) {
    if (a === 0 && b === 0) return 1 / a === 1 / b;
    if (a !== a && b !== b) return true;

    return a === b;
}

var __isArray = Array.isArray;
function isWritable(obj, name, verifyProp, value) {
    var unlikelyValue = __isArray(obj) && name === "length" ?
        Math.pow(2, 32) - 1 :
        "unlikelyValue";
    var newValue = value || unlikelyValue;
    var hadValue = Object.prototype.hasOwnProperty.call(obj, name);
    var oldValue = obj[name];
    var writeSucceeded;

    try {
        obj[name] = newValue;
    } catch (e) {
        if (!(e instanceof TypeError)) {
            $ERROR("Expected TypeError, got " + e);
        }
    }

    writeSucceeded = isSameValue(obj[verifyProp || name], newValue);

    // Revert the change only if it was successful (in other cases, reverting
    // is unnecessary and may trigger exceptions for certain property
    // configurations)
    if (writeSucceeded) {
        if (hadValue) {
            obj[name] = oldValue;
        } else {
            delete obj[name];
        }
    }

    return writeSucceeded;
}

function verifyEqualTo(obj, name, value) {
    if (!isSameValue(obj[name], value)) {
        $ERROR("Expected obj[" + String(name) + "] to equal " + value +
            ", actually " + obj[name]);
    }
}

function verifyWritable(obj, name, verifyProp, value) {
    if (!verifyProp) {
        assert(Object.getOwnPropertyDescriptor(obj, name).writable,
            "Expected obj[" + String(name) + "] to have writable:true.");
    }
    if (!isWritable(obj, name, verifyProp, value)) {
        $ERROR("Expected obj[" + String(name) + "] to be writable, but was not.");
    }
}

function verifyNotWritable(obj, name, verifyProp, value) {
    if (!verifyProp) {
        assert(!Object.getOwnPropertyDescriptor(obj, name).writable,
            "Expected obj[" + String(name) + "] to have writable:false.");
    }
    if (isWritable(obj, name, verifyProp)) {
        $ERROR("Expected obj[" + String(name) + "] NOT to be writable, but was.");
    }
}

function verifyEnumerable(obj, name) {
    assert(Object.getOwnPropertyDescriptor(obj, name).enumerable,
        "Expected obj[" + String(name) + "] to have enumerable:true.");
    if (!isEnumerable(obj, name)) {
        $ERROR("Expected obj[" + String(name) + "] to be enumerable, but was not.");
    }
}

function verifyNotEnumerable(obj, name) {
    assert(!Object.getOwnPropertyDescriptor(obj, name).enumerable,
        "Expected obj[" + String(name) + "] to have enumerable:false.");
    if (isEnumerable(obj, name)) {
        $ERROR("Expected obj[" + String(name) + "] NOT to be enumerable, but was.");
    }
}

function verifyConfigurable(obj, name) {
    assert(Object.getOwnPropertyDescriptor(obj, name).configurable,
        "Expected obj[" + String(name) + "] to have configurable:true.");
    if (!isConfigurable(obj, name)) {
        $ERROR("Expected obj[" + String(name) + "] to be configurable, but was not.");
    }
}

function verifyNotConfigurable(obj, name) {
    assert(!Object.getOwnPropertyDescriptor(obj, name).configurable,
        "Expected obj[" + String(name) + "] to have configurable:false.");
    if (isConfigurable(obj, name)) {
        $ERROR("Expected obj[" + String(name) + "] NOT to be configurable, but was.");
    }
}


function compareArray(a, b) {
    if (b.length !== a.length) {
        return false;
    }

    for (var i = 0; i < a.length; i++) {
        if (!compareArray.isSameValue(b[i], a[i])) {
            return false;
        }
    }
    return true;
}

compareArray.isSameValue = function(a, b) {
    if (a === 0 && b === 0) return 1 / a === 1 / b;
    if (a !== a && b !== b) return true;

    return a === b;
};

compareArray.format = function(array) {
    return `[${array.map(String).join(', ')}]`;
};

assert.compareArray = function(actual, expected, message = '') {
    assert(actual != null, `First argument shouldn't be nullish. ${message}`);
    assert(expected != null, `Second argument shouldn't be nullish. ${message}`);
    var format = compareArray.format;
    assert(
        compareArray(actual, expected),
        `Expected ${format(actual)} and ${format(expected)} to have the same contents. ${message}`
    );
};


var date_1899_end = -2208988800001;
var date_1900_start = -2208988800000;
var date_1969_end = -1;
var date_1970_start = 0;
var date_1999_end = 946684799999;
var date_2000_start = 946684800000;
var date_2099_end = 4102444799999;
var date_2100_start = 4102444800000;

var start_of_time = -8.64e15;
var end_of_time = 8.64e15;

function  assertRelativeDateMs(date, expectedMs) {
    var actualMs = date.valueOf();
    var localOffset = date.getTimezoneOffset() * 60000;

    if (actualMs - localOffset !== expectedMs) {
        $ERROR(
            'Expected ' + date + ' to be ' + expectedMs +
            ' milliseconds from the Unix epoch, got ' + (actualMs - localOffset)
        );
    }
}


function buildString({ loneCodePoints, ranges }) {
    const CHUNK_SIZE = 10000;
    let result = Reflect.apply(String.fromCodePoint, null, loneCodePoints);
    for (let i = 0; i < ranges.length; i++) {
        const range = ranges[i];
        const start = range[0];
        const end = range[1];
        const codePoints = [];
        for (let length = 0, codePoint = start; codePoint <= end; codePoint++) {
            codePoints[length++] = codePoint;
            if (length === CHUNK_SIZE) {
                result += Reflect.apply(String.fromCodePoint, null, codePoints);
                codePoints.length = length = 0;
            }
        }
        result += Reflect.apply(String.fromCodePoint, null, codePoints);
    }
    return result;
}

function testPropertyEscapes(regex, string, expression) {
    if (!regex.test(string)) {
        for (const symbol of string) {
            const hex = symbol
                .codePointAt(0)
                .toString(16)
                .toUpperCase()
                .padStart(6, "0");
            assert(
                regex.test(symbol),
                `\`${ expression }\` should match U+${ hex } (\`${ symbol }\`)`
            );
        }
    }
}

// Returns a function that will validate RegExp match result
//
// Example:
//
//    var validate = matchValidator(['b'], 1, 'abc');
//    validate(/b/.exec('abc'));
//
function matchValidator(expectedEntries, expectedIndex, expectedInput) {
    return function(match) {
        assert.compareArray(match, expectedEntries, 'Match entries');
        assert.sameValue(match.index, expectedIndex, 'Match index');
        assert.sameValue(match.input, expectedInput, 'Match input');
    }
}


var byteConversionValues = {
    values: [
        127,         // 2 ** 7 - 1
        128,         // 2 ** 7
        32767,       // 2 ** 15 - 1
        32768,       // 2 ** 15
        2147483647,  // 2 ** 31 - 1
        2147483648,  // 2 ** 31
        255,         // 2 ** 8 - 1
        256,         // 2 ** 8
        65535,       // 2 ** 16 - 1
        65536,       // 2 ** 16
        4294967295,  // 2 ** 32 - 1
        4294967296,  // 2 ** 32
        9007199254740991, // 2 ** 53 - 1
        9007199254740992, // 2 ** 53
        1.1,
        0.1,
        0.5,
        0.50000001,
        0.6,
        0.7,
        undefined,
        -1,
        -0,
        -0.1,
        -1.1,
        NaN,
        -127,        // - ( 2 ** 7 - 1 )
        -128,        // - ( 2 ** 7 )
        -32767,      // - ( 2 ** 15 - 1 )
        -32768,      // - ( 2 ** 15 )
        -2147483647, // - ( 2 ** 31 - 1 )
        -2147483648, // - ( 2 ** 31 )
        -255,        // - ( 2 ** 8 - 1 )
        -256,        // - ( 2 ** 8 )
        -65535,      // - ( 2 ** 16 - 1 )
        -65536,      // - ( 2 ** 16 )
        -4294967295, // - ( 2 ** 32 - 1 )
        -4294967296, // - ( 2 ** 32 )
        Infinity,
        -Infinity,
        0
    ],

    expected: {
        Int8: [
            127,  // 127
            -128, // 128
            -1,   // 32767
            0,    // 32768
            -1,   // 2147483647
            0,    // 2147483648
            -1,   // 255
            0,    // 256
            -1,   // 65535
            0,    // 65536
            -1,   // 4294967295
            0,    // 4294967296
            -1,   // 9007199254740991
            0,    // 9007199254740992
            1,    // 1.1
            0,    // 0.1
            0,    // 0.5
            0,    // 0.50000001,
            0,    // 0.6
            0,    // 0.7
            0,    // undefined
            -1,   // -1
            0,    // -0
            0,    // -0.1
            -1,   // -1.1
            0,    // NaN
            -127, // -127
            -128, // -128
            1,    // -32767
            0,    // -32768
            1,    // -2147483647
            0,    // -2147483648
            1,    // -255
            0,    // -256
            1,    // -65535
            0,    // -65536
            1,    // -4294967295
            0,    // -4294967296
            0,    // Infinity
            0,    // -Infinity
            0
        ],
        Uint8: [
            127, // 127
            128, // 128
            255, // 32767
            0,   // 32768
            255, // 2147483647
            0,   // 2147483648
            255, // 255
            0,   // 256
            255, // 65535
            0,   // 65536
            255, // 4294967295
            0,   // 4294967296
            255, // 9007199254740991
            0,   // 9007199254740992
            1,   // 1.1
            0,   // 0.1
            0,   // 0.5
            0,   // 0.50000001,
            0,   // 0.6
            0,   // 0.7
            0,   // undefined
            255, // -1
            0,   // -0
            0,   // -0.1
            255, // -1.1
            0,   // NaN
            129, // -127
            128, // -128
            1,   // -32767
            0,   // -32768
            1,   // -2147483647
            0,   // -2147483648
            1,   // -255
            0,   // -256
            1,   // -65535
            0,   // -65536
            1,   // -4294967295
            0,   // -4294967296
            0,   // Infinity
            0,   // -Infinity
            0
        ],
        Uint8Clamped: [
            127, // 127
            128, // 128
            255, // 32767
            255, // 32768
            255, // 2147483647
            255, // 2147483648
            255, // 255
            255, // 256
            255, // 65535
            255, // 65536
            255, // 4294967295
            255, // 4294967296
            255, // 9007199254740991
            255, // 9007199254740992
            1,   // 1.1,
            0,   // 0.1
            0,   // 0.5
            1,   // 0.50000001,
            1,   // 0.6
            1,   // 0.7
            0,   // undefined
            0,   // -1
            0,   // -0
            0,   // -0.1
            0,   // -1.1
            0,   // NaN
            0,   // -127
            0,   // -128
            0,   // -32767
            0,   // -32768
            0,   // -2147483647
            0,   // -2147483648
            0,   // -255
            0,   // -256
            0,   // -65535
            0,   // -65536
            0,   // -4294967295
            0,   // -4294967296
            255, // Infinity
            0,   // -Infinity
            0
        ],
        Int16: [
            127,    // 127
            128,    // 128
            32767,  // 32767
            -32768, // 32768
            -1,     // 2147483647
            0,      // 2147483648
            255,    // 255
            256,    // 256
            -1,     // 65535
            0,      // 65536
            -1,     // 4294967295
            0,      // 4294967296
            -1,     // 9007199254740991
            0,      // 9007199254740992
            1,      // 1.1
            0,      // 0.1
            0,      // 0.5
            0,      // 0.50000001,
            0,      // 0.6
            0,      // 0.7
            0,      // undefined
            -1,     // -1
            0,      // -0
            0,      // -0.1
            -1,     // -1.1
            0,      // NaN
            -127,   // -127
            -128,   // -128
            -32767, // -32767
            -32768, // -32768
            1,      // -2147483647
            0,      // -2147483648
            -255,   // -255
            -256,   // -256
            1,      // -65535
            0,      // -65536
            1,      // -4294967295
            0,      // -4294967296
            0,      // Infinity
            0,      // -Infinity
            0
        ],
        Uint16: [
            127,   // 127
            128,   // 128
            32767, // 32767
            32768, // 32768
            65535, // 2147483647
            0,     // 2147483648
            255,   // 255
            256,   // 256
            65535, // 65535
            0,     // 65536
            65535, // 4294967295
            0,     // 4294967296
            65535, // 9007199254740991
            0,     // 9007199254740992
            1,     // 1.1
            0,     // 0.1
            0,     // 0.5
            0,     // 0.50000001,
            0,     // 0.6
            0,     // 0.7
            0,     // undefined
            65535, // -1
            0,     // -0
            0,     // -0.1
            65535, // -1.1
            0,     // NaN
            65409, // -127
            65408, // -128
            32769, // -32767
            32768, // -32768
            1,     // -2147483647
            0,     // -2147483648
            65281, // -255
            65280, // -256
            1,     // -65535
            0,     // -65536
            1,     // -4294967295
            0,     // -4294967296
            0,     // Infinity
            0,     // -Infinity
            0
        ],
        Int32: [
            127,         // 127
            128,         // 128
            32767,       // 32767
            32768,       // 32768
            2147483647,  // 2147483647
            -2147483648, // 2147483648
            255,         // 255
            256,         // 256
            65535,       // 65535
            65536,       // 65536
            -1,          // 4294967295
            0,           // 4294967296
            -1,          // 9007199254740991
            0,           // 9007199254740992
            1,           // 1.1
            0,           // 0.1
            0,           // 0.5
            0,           // 0.50000001,
            0,           // 0.6
            0,           // 0.7
            0,           // undefined
            -1,          // -1
            0,           // -0
            0,           // -0.1
            -1,          // -1.1
            0,           // NaN
            -127,        // -127
            -128,        // -128
            -32767,      // -32767
            -32768,      // -32768
            -2147483647, // -2147483647
            -2147483648, // -2147483648
            -255,        // -255
            -256,        // -256
            -65535,      // -65535
            -65536,      // -65536
            1,           // -4294967295
            0,           // -4294967296
            0,           // Infinity
            0,           // -Infinity
            0
        ],
        Uint32: [
            127,        // 127
            128,        // 128
            32767,      // 32767
            32768,      // 32768
            2147483647, // 2147483647
            2147483648, // 2147483648
            255,        // 255
            256,        // 256
            65535,      // 65535
            65536,      // 65536
            4294967295, // 4294967295
            0,          // 4294967296
            4294967295, // 9007199254740991
            0,          // 9007199254740992
            1,          // 1.1
            0,          // 0.1
            0,          // 0.5
            0,          // 0.50000001,
            0,          // 0.6
            0,          // 0.7
            0,          // undefined
            4294967295, // -1
            0,          // -0
            0,          // -0.1
            4294967295, // -1.1
            0,          // NaN
            4294967169, // -127
            4294967168, // -128
            4294934529, // -32767
            4294934528, // -32768
            2147483649, // -2147483647
            2147483648, // -2147483648
            4294967041, // -255
            4294967040, // -256
            4294901761, // -65535
            4294901760, // -65536
            1,          // -4294967295
            0,          // -4294967296
            0,          // Infinity
            0,          // -Infinity
            0
        ],
        Float32: [
            127,                  // 127
            128,                  // 128
            32767,                // 32767
            32768,                // 32768
            2147483648,           // 2147483647
            2147483648,           // 2147483648
            255,                  // 255
            256,                  // 256
            65535,                // 65535
            65536,                // 65536
            4294967296,           // 4294967295
            4294967296,           // 4294967296
            9007199254740992,     // 9007199254740991
            9007199254740992,     // 9007199254740992
            1.100000023841858,    // 1.1
            0.10000000149011612,  // 0.1
            0.5,                  // 0.5
            0.5,                  // 0.50000001,
            0.6000000238418579,   // 0.6
            0.699999988079071,    // 0.7
            NaN,                  // undefined
            -1,                   // -1
            -0,                   // -0
            -0.10000000149011612, // -0.1
            -1.100000023841858,   // -1.1
            NaN,                  // NaN
            -127,                 // -127
            -128,                 // -128
            -32767,               // -32767
            -32768,               // -32768
            -2147483648,          // -2147483647
            -2147483648,          // -2147483648
            -255,                 // -255
            -256,                 // -256
            -65535,               // -65535
            -65536,               // -65536
            -4294967296,          // -4294967295
            -4294967296,          // -4294967296
            Infinity,             // Infinity
            -Infinity,            // -Infinity
            0
        ],
        Float64: [
            127,         // 127
            128,         // 128
            32767,       // 32767
            32768,       // 32768
            2147483647,  // 2147483647
            2147483648,  // 2147483648
            255,         // 255
            256,         // 256
            65535,       // 65535
            65536,       // 65536
            4294967295,  // 4294967295
            4294967296,  // 4294967296
            9007199254740991, // 9007199254740991
            9007199254740992, // 9007199254740992
            1.1,         // 1.1
            0.1,         // 0.1
            0.5,         // 0.5
            0.50000001,  // 0.50000001,
            0.6,         // 0.6
            0.7,         // 0.7
            NaN,         // undefined
            -1,          // -1
            -0,          // -0
            -0.1,        // -0.1
            -1.1,        // -1.1
            NaN,         // NaN
            -127,        // -127
            -128,        // -128
            -32767,      // -32767
            -32768,      // -32768
            -2147483647, // -2147483647
            -2147483648, // -2147483648
            -255,        // -255
            -256,        // -256
            -65535,      // -65535
            -65536,      // -65536
            -4294967295, // -4294967295
            -4294967296, // -4294967296
            Infinity,    // Infinity
            -Infinity,   // -Infinity
            0
        ]
    }
};

