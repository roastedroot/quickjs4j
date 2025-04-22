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

and get started with an "hello world" program:

```java
import io.roastedroot.quickjs4j.core.Runner;

try (var runner = Runner.builder().build()) {
    runner.compileAndExec("console.log(\"Hello QuickJs4J!\");");
}
```

QuickJs4J runs JavaScript in a secure, sandboxed environment, requiring explicit access to resources.
It simplifies this by letting you bind top-level Java functions, making them easily callable from JavaScript.

```java
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;

@JsModule()
class MyJsTestModule implements AutoCloseable {
    private final Runner runner;

    public MyJsTestModule() {
        var builtins = Builtins.builder().add(MyJsTestModule_Builtins.toBuiltins(this)).build();
        var engine = Engine.builder().withBuiltins(builtins).build();
        this.runner = Runner.builder().withEngine(engine).build();
    }

    @HostFunction("my_java_func")
    public String add(int x, int y) {
        var sum = x + y;
        return "hello " + sum;
    }

    @HostFunction("my_java_check")
    public void check(String value) {
        invoked = true;
        assertEquals("hello 42", value);
    }

    public void exec(String code) {
        runner.compileAndExec(code);
    }

    @Override
    public void close() {
        runner.close();
    }
}


try (var myTestModule = new MyJsTestModule()) {
    myTestModule.exec("my_java_check(my_java_func(40, 2));");
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

## Build

To build this project you need a Rust toolchain available, a JDK(11+) and Maven.

```bash
cd javy-plugin
make build
cd ..

mvn clean install
```

## Thanks

This project is standing on giant's shoulders:

- [QuickJS](https://bellard.org/quickjs/) is a small and embeddable Javascript engine
- [Javy](https://github.com/bytecodealliance/javy) is a convenient JavaScript to Webassembly toolchain
- [Chicory](https://chicory.dev/) is a ative JVM WebAssembly runtime
