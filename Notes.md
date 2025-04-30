
Things TODO:

- have Strings going forth and back Java <-> JS - DONE
- have a basic mechanism for dynamically invoking from JavaScript on the Java host - DONE
- test the mechanism with various types - DONE
- verify if it's useful to pass around object references and how - implementing top level functions plus JavaRefs - considering DONe (eventually add the option to invoke methods on objects - probably it can be already implemented)
- verify how to use JavaScript libraries - also TS - DONE (cowsay and zod)
- make it possible to cache compiled JavaScript - DONE
- write an executor that can cache already compiled scripts - DONE
- wrap things up with an annotation processor - DONE
- improve JavaScript invocation from Java - On GOING
- verify complex types with the GuestFunction interface
- cleanup Invokable implementation
- finish annotation processor generation for GuestFunction
- emit the "java_api.mjs" stub to be used in JS

- more tests for primitive types in annotation processor
- test more closer to reality (e.g. with Apicurio like interface)
