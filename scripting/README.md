# QuickJs4J JSR-223 Scripting Engine

A [JSR-223](https://www.jcp.org/en/jsr/detail?id=223) (`javax.script`) compliant JavaScript engine backed by QuickJS running on WebAssembly.
It supports the standard `ScriptEngine` API plus the `Compilable` and `Invocable` interfaces.

## Dependency

```xml
<dependency>
  <groupId>io.roastedroot</groupId>
  <artifactId>quickjs4j-scripting-experimental</artifactId>
  <version>${quickjs4j.version}</version>
</dependency>
```

## Obtaining the Engine

The engine registers itself via SPI, so `ScriptEngineManager` can discover it by name, file extension, or MIME type:

```java
import javax.script.*;

ScriptEngineManager manager = new ScriptEngineManager();

ScriptEngine engine = manager.getEngineByName("quickjs4j");
// or: manager.getEngineByExtension("js");
// or: manager.getEngineByMimeType("application/x-javascript");
```

You can also create an engine directly:

```java
import io.roastedroot.quickjs4j.scripting.JsScriptEngine;

JsScriptEngine engine = new JsScriptEngine();
```

## Evaluating Scripts

`eval()` returns the value of the last expression — no `return` keyword needed:

```java
engine.eval("1 + 2");          // 3
engine.eval("'hello'");        // "hello"
engine.eval("true");           // true
engine.eval("null");           // null
engine.eval("var x = 3");      // null (declarations don't produce values)
```

## Bindings

Bindings pass Java values into the JavaScript scope with correct types preserved (numbers stay numbers, booleans stay booleans):

```java
engine.put("x", 42);
engine.put("pi", 3.14);
engine.put("flag", true);
engine.put("name", "world");

engine.eval("x + 10");             // 52  (not "4210")
engine.eval("'hello ' + name");    // "hello world"
engine.eval("flag === true");      // true
```

## State Persistence

The JavaScript runtime persists between `eval()` calls. Variables declared with `var` and function declarations are added to the global scope:

```java
engine.eval("var counter = 0");
engine.eval("function increment() { return ++counter; }");

engine.eval("increment()");  // 1
engine.eval("increment()");  // 2
engine.eval("counter");      // 2
```

> **Note:** `let` and `const` declarations are block-scoped to each `eval()` call and do not persist. Use `var` or assign to `globalThis` for cross-eval state.

## Compilable — Parse Once, Eval Many Times

The engine implements `javax.script.Compilable`. Compile a script once and evaluate it repeatedly, potentially with different bindings:

```java
Compilable compilable = (Compilable) engine;
CompiledScript compiled = compilable.compile("x * 2");

engine.put("x", 5);
compiled.eval();  // 10

engine.put("x", 7);
compiled.eval();  // 14
```

## Invocable — Calling JavaScript Functions

The engine implements `javax.script.Invocable`.

### invokeFunction

Call a top-level JavaScript function by name:

```java
engine.eval("function add(a, b) { return a + b; }");

Invocable invocable = (Invocable) engine;
Object result = invocable.invokeFunction("add", 3, 4);  // 7
```

If the function does not exist, a `NoSuchMethodException` is thrown:

```java
try {
    invocable.invokeFunction("nonExistent");
} catch (NoSuchMethodException e) {
    // "nonExistent"
}
```

### invokeMethod

Call a method on a JavaScript object. The first argument (`thiz`) is a `String` identifying the variable name in JavaScript's global scope:

```java
engine.eval("var calc = {"
          + "  add: function(a, b) { return a + b; },"
          + "  multiply: function(a, b) { return a * b; }"
          + "}");

Invocable invocable = (Invocable) engine;
invocable.invokeMethod("calc", "add", 2, 3);       // 5
invocable.invokeMethod("calc", "multiply", 2, 3);   // 6
```

> **Limitation:** Because values cross a WASM boundary via JSON serialization, live JavaScript object references cannot be held in Java.
> Unlike Nashorn or JRuby, the `thiz` parameter must be a `String` naming a variable in JavaScript's global scope — not a Java object returned from a previous `eval()`. Passing a non-String object throws `IllegalArgumentException`.

### getInterface

Create a Java interface proxy backed by JavaScript functions.
Each interface method call is delegated to a JavaScript function of the same name.

Define a Java interface:

```java
public interface Calculator {
    Object add(int a, int b);
    Object multiply(int a, int b);
}
```

Bind it to top-level functions:

```java
engine.eval("function add(a, b) { return a + b; }");
engine.eval("function multiply(a, b) { return a * b; }");

Calculator calc = ((Invocable) engine).getInterface(Calculator.class);
calc.add(2, 3);       // 5
calc.multiply(2, 3);  // 6
```

Or bind it to methods on a JavaScript object using the two-argument form. As with `invokeMethod`, `thiz` must be a `String` variable name:

```java
engine.eval("var math = {"
          + "  add: function(a, b) { return a + b; },"
          + "  multiply: function(a, b) { return a * b; }"
          + "}");

Calculator calc = ((Invocable) engine).getInterface("math", Calculator.class);
calc.add(3, 4);       // 7
calc.multiply(3, 4);  // 12
```

## Output Redirection

By default, `console.log` and `console.error` output is written to the `ScriptContext` writers (which default to `System.out` and `System.err`).
You can redirect output by providing a custom `ScriptContext`:

```java
ScriptContext ctx = new SimpleScriptContext();
StringWriter out = new StringWriter();
StringWriter err = new StringWriter();
ctx.setWriter(out);
ctx.setErrorWriter(err);
ctx.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

engine.eval("console.log('hello')", ctx);
out.toString();  // "hello\n"

engine.eval("console.error('oops')", ctx);
err.toString();  // "oops\n"
```

## Resource Management

`JsScriptEngine` implements `AutoCloseable`. Use try-with-resources to ensure the underlying WASM runtime is released:

```java
try (JsScriptEngine engine = new JsScriptEngine()) {
    engine.eval("1 + 1");  // 2
}
```

## Error Handling

JavaScript syntax errors and runtime errors are thrown as `javax.script.ScriptException`:

```java
try {
    engine.eval("function {{{");
} catch (ScriptException e) {
    // syntax error
}

try {
    engine.eval("undeclaredVar");
} catch (ScriptException e) {
    // ReferenceError: 'undeclaredVar' is not defined
}
```

## Full Example

```java
import javax.script.*;
import io.roastedroot.quickjs4j.scripting.JsScriptEngine;

public interface Greeter {
    Object greet(String name);
    Object greetAll(String names);
}

public class Example {
    public static void main(String[] args) throws Exception {
        try (JsScriptEngine engine = new JsScriptEngine()) {
            // Define functions
            engine.eval(
                "function greet(name) { return 'Hello, ' + name + '!'; }\n" +
                "function greetAll(names) {\n" +
                "  return names.split(',').map(n => greet(n.trim())).join(' ');\n" +
                "}"
            );

            // Use via invokeFunction
            Invocable inv = (Invocable) engine;
            System.out.println(inv.invokeFunction("greet", "World"));
            // Hello, World!

            // Use via getInterface
            Greeter greeter = inv.getInterface(Greeter.class);
            System.out.println(greeter.greetAll("Alice, Bob"));
            // Hello, Alice! Hello, Bob!
        }
    }
}
```
