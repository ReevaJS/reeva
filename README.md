# ReevaJS

Reeva is a JavaScript engine written from scratch using Kotlin. 

### Motivation

The motivation for this project is two-fold. 

Firstly, the options for JS engines on the JVM are limited. Nashorn is officially 
deprecated, and was only ES5. Rhino, while somewhat actively maintained, is a very old
project, and thus contribution is hard. As someone who has contributed quite a bit to
a Rhino fork, it is very hard to read. Additionally, some newer features (classes,
nullish coalescence, optional chaining, etc) have yet to see support.

Second, I really enjoy JS. Not necessarily from a programming standpoint (I'd much
rather use TypeScript), however as an engine implementer I think it is fantastic. The
language specification is the best I've seen from a dynamic language. Being the language
that runs the web, the language is extremely active. TC39 is always advancing new proposals,
and the major engines do a great job of keeping up. 

Speaking of engines, judging from 
[this great JS engine writeup](https://notes.eatonphil.com/javascript-implementations.html),
there are at least 20+ serious JS implementations out there, and as an engine implementor, 
that really excites me! Clearly, interest in JS implementations is very high. Aside from
the Rhino fork I mentioned earlier, I have also contributed significantly to
[SerenityOS's ](https://github.com/SerenityOS/serenity) implementation of JS, 
[LibJS](https://github.com/SerenityOS/serenity/tree/master/Userland/Libraries/LibJS). 
This contribution is what really got me excited for the language, so much so that I decided
to write my own engine.

### Goals

These are the goals of the project, in order from most important to least important*.

- Code Quality
  - Being written in Kotlin, the code quality of the project is important. Contributing
    should be simple, and it should be obvious what any particular piece of code does.
    To that end, there are some practices implemented within the code base to make
    following various aspects easier. See the contributing guidelines (TODO) for more info.
- ECMAScript Compliance
  - Like every JS engine, Reeva aims to be 100% ECMAScript compliant. However, certain
    legacy features will not be a priority. For example, `eval`, `with () {}`, etc.,
    will most likely not get implemented until the engine is much more stable.
- Speed 
  - Reeva aims to have performs comparable with Rhino. Of course, this is not to say that
    more performance won't be pursued, but it may be put aside for more important endeavours. 

*_Note: "least important" != "not important"_

### Design

Reeva is an interpreter-based engine. It first parses JS source text into an AST tree 
with a custom Parser, and then transforms it to an intermediary IR representation using
opcodes. These opcodes are then interpreted when the code in executed. The plan is to
eventually compile the IR from hot functions down to JVM bytecode for a bit more performance,
however that will not happen until the engine becomes more stable.

For more information on the way Reeva works, see the [Design document](README.md) (TODO)


