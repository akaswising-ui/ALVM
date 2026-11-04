# AL Language Specification: Stage 9

## Identity

AL uses Python-influenced vocabulary, Java-like source structure, and strict C++-influenced rules. It owns its compiler and bytecode VM.

## Static member syntax

```al
room Main {
    public static void main() {
        Counter.value = 10;
        Counter.increment(5);
        AL.print(Counter.value);
    }

    class Counter {
        static int value;

        static void increment(int amount) {
            Counter.value = Counter.value + amount;
        }
    }
}
```

## Static member rules

- A class field may be declared with the `static` modifier.
- A class method may be declared with the `static` modifier.
- Static fields and methods are accessed using the class name: `ClassName.field` and `ClassName.method(args)`.
- Static fields currently support scalar `int`, `string` and `boolean` only. Defaults are `0`, empty string and `false`.
- Static values are stored once per declaring class in VM class storage, not in individual object instances.
- Static field values are shared across function calls and object instances for the lifetime of one program execution.
- A static method has no hidden receiver and no implicit `this`. Using `this` in a static method is a compile-time error.
- Static methods support typed parameters, return values, normal calls and recursion using the existing call-frame runtime.
- Static fields and methods are inherited. An inherited static field access resolves to the declaring class's storage, so reading or writing it through a subclass does not create a second copy.
- Static fields cannot be accessed through an object instance. Instance fields cannot be accessed through a class name.
- Static methods cannot be invoked through an object instance. Instance methods cannot be invoked through a class name.
- A class cannot declare a static and instance field with the same name. A static and instance method with the same name are also rejected.
- Inherited member-name collisions are rejected. Method overriding is not implemented.
- Access modifiers are not enforced yet. The `static` modifier is implemented for fields and methods; access modifiers remain syntax-only.
- Static field initializers and static initializer blocks are not implemented.

## Existing language support

Stage 9 retains typed variables, arrays, functions, recursion, classes, objects, constructors, single inheritance, switch/case/default, while/do-while/for loops, break/continue, arithmetic, strings and boolean operations.

## Compiler and .room v8

`.al -> lexer -> parser -> AST -> semantic/type analysis -> IR -> bytecode lowerer -> .room v8 -> verifier -> ALVM`

The v8 class table stores instance and static field type arrays. Function records store a static flag. New bytecode instructions read and write class storage using a class-id and static-field-slot operand. The verifier checks static field references and stack effects before the VM runs.

Stage 9 rejects .room v1-v7. Recompile source files. The runtime executes compiled bytecode only.

## Not implemented

Enforced `public/private/protected` access control, static initializers, constructors with explicit `super(args)`, overloading, virtual methods, interfaces, abstract classes, object arrays, object-valued fields, generics, enums, exceptions, modules, full standard libraries, file/network/database APIs, graphics/game/audio APIs, GUI IDE, `.AL` packaging and Android APK support remain future work.
