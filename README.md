# QuickJs4J

QuickJS sandboxed for execution in Java.

QuickJs4J runs JavaScript via [QuickJS](https://bellard.org/quickjs/) compiled to WebAssembly, executed in Java through [Chicory](https://github.com/nicklaus-dev/chicory) (a pure-Java WASM runtime). This provides a secure, sandboxed JavaScript environment with no native dependencies.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| **core** | `quickjs4j` | Core engine — compile and execute JavaScript, define host/guest functions, pass typed values across the Java/JS boundary |
| **annotations** | `quickjs4j-annotations` | Annotations for declaring guest/host function bindings |
| **processor** | `quickjs4j-processor` | Annotation processor that generates boilerplate for annotated bindings |
| **scripting** | `quickjs4j-scripting-experimental` | [JSR-223 (`javax.script`) scripting engine](scripting/README.md) — standard `ScriptEngine`, `Compilable`, and `Invocable` support |

## Quick Start (JSR-223)

```java
import javax.script.*;

ScriptEngine engine = new ScriptEngineManager().getEngineByName("quickjs4j");

// Evaluate expressions
engine.eval("1 + 2");  // 3

// Bindings
engine.put("name", "World");
engine.eval("'Hello, ' + name");  // "Hello, World"

// Define and call functions
engine.eval("function add(a, b) { return a + b; }");
((Invocable) engine).invokeFunction("add", 3, 4);  // 7
```

See the [JSR-223 scripting documentation](scripting/README.md) for the full API including `Compilable`, `Invocable`, `getInterface`, output redirection, and more.

## License

[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt)
