# QuickJs4J

**QuickJs4J** lets you safely and easily run JavaScript from Java using a sandboxed environment.

## Why Use QuickJs4J?

QuickJs4J provides a secure and efficient way to execute JavaScript within Java. By running code in a sandbox, it ensures:

* **Memory safety** – JavaScript runs in isolation, protecting your application from crashes or memory leaks.
* **No system access by default** – JavaScript cannot access the filesystem, network, or other sensitive resources unless explicitly allowed.
* **Portability** – Being pure Java bytecode, it runs wherever the JVM does.
* **Native-image friendly** – Compatible with GraalVM's native-image for fast, lightweight deployments.
* **Forward compatible** - regular Java bytecode is generated, so there is no concern version of quickjs4j you are using now will break when you upgrade to newer Java runtime

Whether you're embedding scripting capabilities or isolating untrusted code, QuickJs4J is designed for safe and seamless integration.

## How it works

There are a few steps to achieve the result:

- compile QuickJS to [WebAssembly](https://webassembly.org/)
- translate the QuickJS  payload to pure Java bytecode using [Chicory Compiler](https://chicory.dev/docs/usage/build-time-compiler)
- run QuickJS directly from Java without using JNI
- ship an extremely small and self contained `jar` that can run wherever the JVM can go!



## Quick Start

Add QuickJs4J as a standard Maven dependency:

```xml
<dependency>
    <groupId>io.roastedroot</groupId>
    <artifactId>quickjs4j</artifactId>
</dependency>
```

Then run a simple "Hello World" example:

```java
import io.roastedroot.quickjs4j.core.Runner;

try (var runner = Runner.builder().build()) {
    runner.compileAndExec("console.log(\"Hello QuickJs4J!\");");
    System.out.println(runner.stdout());
}
```

Note: You must explicitly print your JavaScript program’s output using `System.out`.

QuickJs4J runs JavaScript in a secure, sandboxed environment. To simplify communication, it allows you to bind Java methods so they can be called directly from JavaScript.

```java
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.Builtins;

@Builtins("from_java")
class JavaApi {
    @HostFunction("my_java_func")
    public String add(int x, int y) {
        return "hello " + (x + y);
    }

    @HostFunction("my_java_check")
    public void check(String value) {
        assert("hello 42".equals(value));
    }
}

var engine =
    Engine.builder()
          .addBuiltins(JavaApi_Builtins.toBuiltins(new JavaApi()))
          .build();

try (var runner = Runner.builder().withEngine(engine).build()) {
    runner.exec("from_java.my_java_check(from_java.my_java_func(40, 2));");
}
```

### Calling JavaScript from Java

To invoke functions defined in a JavaScript or TypeScript library, define an interface like this:

```java
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.Invokables;

@Invokables("from_js")
interface JsApi {
    @GuestFunction
    String sub(int x, int y);
}

var engine =
    Engine.builder()
          .addInvokables(JsApi_Invokables.toInvokables())
          .build();

// Inlined for demo; normally loaded from a packaged distribution file
String jsLibrary = "function sub(x, y) { return \"hello js \" + (x - y); };";

try (var runner = Runner.builder().withEngine(engine).build()) {
    var jsApi = JsApi_Invokables.create(jsLibrary, runner);
    System.out.println(jsApi.sub(3, 1));
}
```

### Enabling Annotation Processing

Configure the annotation processor in your Maven `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>io.roastedroot</groupId>
    <artifactId>quickjs4j-annotations</artifactId>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>io.roastedroot</groupId>
            <artifactId>quickjs4j-processor</artifactId>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### Passing Java Object References

Sometimes, you may want to pass a Java object reference to JavaScript without serializing it (i.e., keeping it only in Java memory). Use `HostRef`s as shown below:

```java
import io.roastedroot.quickjs4j.annotations.HostRefParam;
import io.roastedroot.quickjs4j.annotations.ReturnsHostRef;

@ReturnsHostRef
@HostFunction("my_java_ref")
public String myRef() {
    return "a Java string not visible in JS";
}

@HostFunction("my_java_ref_check")
public void myRefCheck(@HostRefParam String value) {
    ...
}

try (var myTestModule = new MyJsTestModule()) {
    myTestModule.exec("my_java_ref_check(my_java_ref());");
}
```

## High Level API

An higher level API is exposed for convenience to wrap everything up for the most common use cases.
You can use the `ScriptInterface` annotation:

```java
import io.roastedroot.quickjs4j.annotations.ScriptInterface;

public class CalculatorContext {
    public void log(String message) {
        System.out.println("LOG>> " + message);
    }
}

@ScriptInterface(context = CalculatorContext.class)
public interface Calculator {
    int add(int term1, int term2);

    int subtract(int term1, int term2);
}

try (var calculator = new Calculator_Proxy(jsLibrary, new CalculatorContext()) {
    calculator.add(1, 2);
    calculator.subtract(3, 1);
}
```

> ***_NOTE:_*** currently only basic use is supported.

## Building a JS/TS Library

To build your JavaScript/TypeScript library, refer to [this example](it/src/it/apicurio-example/src/main/resources/library).

Key points:

* Output an **ECMAScript module** using tools like `esbuild`:

  ```bash
  esbuild your_file.js --format=esm
  ```
* The library must export the expected functions.
* The annotation processor will generate a `.mjs` file at:

  ```
  target/classes/META-INF/quickjs4j/builtin_name.mjs
  ```

  This file bridges your Java and JS code.

## Building the Project

To build this project, you'll need:

* A Rust toolchain
* JDK 11 or newer
* Maven

Steps:

```bash
rustup target add wasm32-wasip1  # Only needed once

cd javy-plugin
make build
cd ..

mvn clean install
```

## Acknowledgements

This project stands on the shoulders of giants:

* [QuickJS](https://bellard.org/quickjs/) – a small, embeddable JavaScript engine
* [Javy](https://github.com/bytecodealliance/javy) – a toolchain for compiling JavaScript to WebAssembly
* [Chicory](https://chicory.dev/) – a native JVM WebAssembly runtime
