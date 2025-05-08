# QuickJs4J

QuickJs4J lets you easily and safely run JavaScript from Java using a sandbox.

## Why

QuickJs4J provides a secure way to run JavaScript from Java. By executing code in a sandboxed environment, it ensures:

- **Memory safety** – isolated execution protects your Java application from crashes or leaks.
- **No system access by default** – JavaScript code can’t touch files, network, or other sensitive resources unless you explicitly allow it.
- **Portability** - being pure Java bytecode it can run wherever the JVM can go.
- **Native-image friendly** – works out of the box with GraalVM’s native-image for fast, lightweight deployments.

Whether you're embedding scripting support or isolating untrusted code, QuickJs4J is built for safe, efficient integration.

## Quick Start

Import QuickJs4J as a standard dependency:

```xml
<dependency>
    <groupId>io.roastedroot</groupId>
    <artifactId>quickjs4j</artifactId>
</dependency>
```

and get started with a "hello world" program:

```java
import io.roastedroot.quickjs4j.core.Runner;

try (var runner = Runner.builder().build()) {
    runner.compileAndExec("console.log(\"Hello QuickJs4J!\");");
    System.out.println(runner.stdout());
}
```

Note that you need to explicitly print to `System.out` your program's stdout output.

QuickJs4J runs JavaScript in a secure, sandboxed environment, requiring explicit access to resources.
It simplifies this by letting you bind top-level Java functions, making them easily callable from JavaScript.

```java
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.Builtins;

@Builtins("from_java")
class JavaApi {
    @HostFunction("my_java_func")
    public String add(int x, int y) {
        var sum = x + y;
        return "hello " + sum;
    }

    @HostFunction("my_java_check")
    public void check(String value) {
        assert("hello 42".equals(value));
    }
}

var engine =
    Engine.builder()
            .addBuiltins(JavaApi_Builtins.toBuiltins(new TestBuiltins()))
            .build();

try (var runner = Runner.builder().withEngine(engine).build()) {
    runner.exec("from_java.my_java_check(from_java.my_java_func(40, 2));");
}
```

When you need to, instead, invoke functions on a provided JavaScript/TypeScript library you can define the interface in this way:

```java
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.Invokables;

@Invokables("from_js")
interface JsApi {
    @GuestFunction
    public String sub(int x, int y);
}

var engine =
    Engine.builder()
            .addInvokables(JsApi_Invokables.toInvokables())
            .build();

// inlined for demo purposes, this is usually loaded from packager distribution file
String jsLibrary = "function sub(x, y) { return \"hello js \" + (x - y); };";

try (var runner = Runner.builder().withEngine(engine).build()) {
    var jsApi = JsApi_Invokables.create(jsLibrary, runner);
    System.out.println(jsApi.sub(3, 1));
}
```

You need to configure the annotation processor as usual, for example in Maven `pom.xml`:

```xml
  <dependencies>
    <dependency>
        <groupId>io.roastedroot</groupId>
        <artifactId>quickjs4j-annotations</artifactId>
    </dependency>
  </dependencies>
    ...
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

At times is useful to pass to the JavaScript environment a reference to a Java object without actually serializing it (e.g. keeping it only in the memory of the Java Host).
You can do it by using `HostRef`s, when using annotations it looks like the following:

```java
import io.roastedroot.quickjs4j.annotations.HostRefParam;
import io.roastedroot.quickjs4j.annotations.ReturnsHostRef;

@ReturnsHostRef
@HostFunction("my_java_ref")
public String myRef() {
    return "a java String not visible in JS";
}

@HostFunction("my_java_ref_check")
public void myRefCheck(@HostRefParam String value) {
    ...
}


try (var myTestModule = new MyJsTestModule()) {
    myTestModule.exec("my_java_ref_check(my_java_ref());");
}
```

## Building a JS/TS library

To build your JS/TS library we invite you to take a look at [this example](it/src/it/apicurio-example/src/main/resources/library).
The key aspects of it are:

- output an "ECMAScript module", with `esbuild` specify: `--format=esm`
- the library should implements the expected functions and `export` them
- the annotation processor for `Builtins` is going to generate a `.mjs` file(`target/classes/META-INF/quickjs4j/builtin_name.mjs`) that represents your Java API

## Build

To build this project you need a Rust toolchain available, a JDK(11+) and Maven.

```bash
rustup target add wasm32-wasip1 # only once

cd javy-plugin
make build
cd ..

mvn clean install
```

## Thanks

This project is standing on giant's shoulders:

- [QuickJS](https://bellard.org/quickjs/) a small and embeddable Javascript engine
- [Javy](https://github.com/bytecodealliance/javy) a convenient JavaScript to Webassembly toolchain
- [Chicory](https://chicory.dev/) a native JVM WebAssembly runtime
