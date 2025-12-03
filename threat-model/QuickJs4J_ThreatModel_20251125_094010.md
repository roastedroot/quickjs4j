---
title: "Threat Model: QuickJs4J"
date: 2025-11-25 09:40:10
geometry:
  - margin=1in
---


**Model:** gemini-2.5-pro

## Executive Summary

The threat model for QuickJs4J identifies critical vulnerabilities at the boundary between the host (JVM) and the guest (JS/Wasm) environments. The most severe threat is Remote Code Execution (RCE) through deserialization attacks targeting host functions exposed to the guest, rated as critical. Other high-impact threats include Denial of Service through resource exhaustion, information disclosure via unhandled exceptions, and cache poisoning of compiled scripts. The security of the system relies heavily on the integrator implementing secure configurations, as default settings for JSON parsing and script caching are insecure. Supply chain risks associated with the Wasm binary and build-time annotation processors also present a significant threat vector.

## Assumptions

- The Wasm runtime provides a secure memory sandbox, isolating guest (JS) from host (JVM) memory and execution.
- The system's security critically depends on the integrator defining a minimal and secure ABI using the provided annotations.
- The default JSON deserializer is not secure; integrators must configure it to prevent deserialization gadget chain exploits.
- Uncaught exceptions in Java host functions may leak stack traces and other sensitive details into guest-accessible logs.
- Integrators are assumed to wrap host function calls in try-catch blocks to prevent information leakage.
- A single Engine instance is not thread-safe; serialized access must be enforced by the integrator (e.g., via a single-thread executor).
- Configurable memory and execution time limits are the primary defenses against guest-induced resource exhaustion (DoS) attacks.
- ~~The default script cache uses MD5 and is vulnerable to cache poisoning attacks if a stronger implementation is not provided.~~ Fixed in: #108
- The impact of a cache poisoning attack is assumed to be limited to returning manipulated data, not escaping the sandbox.
- All data crosses the host-guest boundary via explicit serialization (JSON) or as opaque host references, not via shared memory.
- Only Java methods explicitly annotated with `@HostFunction` are exposed, preventing accidental exposure of unintended functionality.

## Assets

| Asset ID  | Classification | Description                                                               | Owner             |
| --------- | -------------- | ------------------------------------------------------------------------- | ----------------- |
| asset-001 | Confidential   | The QuickJS `Engine` instance managing the Wasm runtime.                  | QuickJs4J Library |
| asset-002 | Confidential   | The `Runner`, a high-level API for executing JS code with timeouts.       | QuickJs4J Library |
| asset-003 | Confidential   | The Wasm runtime (Chicory) that executes the QuickJS binary.              | QuickJs4J Library |
| asset-004 | Restricted     | The compiled QuickJS engine in WebAssembly format.                        | QuickJs4J Library |
| asset-005 | Confidential   | Untrusted JavaScript code provided by the end-user/integrator.            | Integrator        |
| asset-006 | Restricted     | The host Java application that integrates and uses the QuickJs4J library. | Integrator        |
| asset-007 | Confidential   | Compile-time annotation processor for generating bridge code.             | QuickJs4J Library |
| asset-008 | Confidential   | Generated Java code that bridges the host and guest environments (ABI).   | QuickJs4J Library |
| asset-009 | Internal       | Default cache for compiled JS bytecode using MD5.                         | QuickJs4J Library |
| asset-010 | Restricted     | Internal list holding `HostRef` objects passed from Java to JS.           | QuickJs4J Library |
| asset-011 | Confidential   | Compiled JavaScript bytecode, the output of the Javy compiler.            | QuickJs4J Library |
| asset-012 | Internal       | The JSON serializer/deserializer (`Jackson`) for data marshalling.        | QuickJs4J Library |
| asset-013 | Internal       | `ExecutorService` used by the Runner for managing execution threads.      | QuickJs4J Library |

## Trust Boundaries

| Boundary ID       | Type      | Description                                                              |
| ----------------- | --------- | ------------------------------------------------------------------------ |
| trustboundary-001 | process   | Separates the trusted JVM host from the untrusted JS/Wasm guest sandbox. |
| trustboundary-002 | process   | Boundary between the runtime engine and the script cache.                |
| trustboundary-003 | privilege | Logical boundary between the library and the integrator's application.   |
| trustboundary-004 | physical  | Separates the build environment from the runtime environment.            |

## Data Flows

| Source    | Destination | Protocol          | Description                                                         | Encryption | Trust Boundary   |
| --------- | ----------- | ----------------- | ------------------------------------------------------------------- | ---------- | ---------------- |
| asset-006 | asset-005   | Internal Wasm ABI | Host Java code calls a guest JS function.                           | Encrypted  | Crosses Boundary |
| asset-005 | asset-006   | Internal Wasm ABI | Guest JS code calls a host Java function.                           | Encrypted  | Crosses Boundary |
| asset-006 | asset-005   | Internal Wasm ABI | Host function returns a value (or `HostRef`) to the guest.          | Encrypted  | Crosses Boundary |
| asset-005 | asset-006   | Internal Wasm ABI | Guest function returns a value to the host.                         | Encrypted  | Crosses Boundary |
| asset-001 | asset-009   | In-Memory         | Engine writes compiled JS bytecode to the script cache.             | Encrypted  | Crosses Boundary |
| asset-009 | asset-001   | In-Memory         | Engine reads compiled JS bytecode from the script cache.            | Encrypted  | Crosses Boundary |
| asset-010 | asset-005   | Internal Wasm ABI | Host Java object is converted to a `HostRef` pointer for the guest. | Encrypted  | Crosses Boundary |

## Identified Threats

| # | Threat                                    | Component         | STRIDE                 | Severity | Risk Score |
| - | ----------------------------------------- | ----------------- | ---------------------- | -------- | ---------- |
| 1 | RCE via Deserialization of Untrusted Data | HostFunction ABI  | Elevation of Privilege | critical | 80/100     |
| 2 | Stack Trace Information Disclosure        | HostFunction ABI  | Information Disclosure | medium   | 35/100     |
| 3 | Resource Exhaustion by Guest Code         | Runner/Engine     | Denial of Service      | medium   | 54/100     |
| 4 | Script Cache Poisoning                    | BasicScriptCache  | Tampering              | medium   | 28/100     |
| 5 | HostRef Type Confusion                    | HostRef Mechanism | Elevation of Privilege | medium   | 42/100     |
| 6 | Wasm Supply Chain Compromise              | Wasm Binary       | Tampering              | medium   | 30/100     |
| 7 | Race Condition from Concurrent Access     | Engine            | Tampering              | medium   | 30/100     |

### Threat Details

#### 1. RCE via Deserialization of Untrusted Data

Guest JS code can pass a malicious JSON string to a host function. If the host uses Jackson's default typing, it can lead to a deserialization gadget chain attack, resulting in arbitrary code execution on the host.

**Component:** HostFunction ABI | **STRIDE Category:** StrideCategory.ELEVATION_OF_PRIVILEGE | **Risk Severity:** RiskSeverity.CRITICAL

**Risk Score:** 8/10 Likelihood * 10/10 Impact = 80/100 Overall

**Likelihood Rationale:** The vulnerability is in a core, exposed data flow path from the untrusted guest to the trusted host. The documentation explicitly warns about this, but insecure defaults are often overlooked.

**Impact Rationale:** A successful exploit leads to a full sandbox escape and RCE on the host JVM, compromising the entire application. This is the highest possible impact.

**Current Mitigation:** The documentation states the default `ObjectMapper` is insecure. The integrator is responsible for providing a securely configured one.

**Recommended Mitigation:** The integrator MUST configure the `ObjectMapper` to disable default typing (`ObjectMapper.deactivateDefaultTyping()`) or use a secure, allow-list-based PolymorphicTypeValidator. The library should consider shipping a secure-by-default `ObjectMapper`.

#### 2. Stack Trace Information Disclosure

If a host function called from JS throws an unhandled exception, the stack trace could be written to stderr, which is captured and potentially exposed to the guest environment.

**Component:** HostFunction ABI | **STRIDE Category:** StrideCategory.INFORMATION_DISCLOSURE | **Risk Severity:** RiskSeverity.MEDIUM

**Risk Score:** 7/10 Likelihood * 5/10 Impact = 35/100 Overall

**Likelihood Rationale:** Developer error in forgetting to catch all exceptions in exposed host functions is common. The path for the leak exists by default.

**Impact Rationale:** Leaking stack traces and exception messages reveals internal class names, code paths, and library versions, which is valuable intelligence for an attacker to craft further exploits.

**Current Mitigation:** The documentation assumes the integrator will wrap host function logic in try-catch blocks to prevent exceptions from propagating.

**Recommended Mitigation:** The `Engine`'s invoke function handler should globally catch all Throwables from host code, log a generic error message, and prevent the stack trace from leaking into the guest's accessible streams.

#### 3. Resource Exhaustion by Guest Code

Untrusted guest JS code can perform computationally expensive operations (e.g., infinite loops) or allocate large amounts of memory within the Wasm sandbox, consuming host CPU and memory.

**Component:** Runner/Engine | **STRIDE Category:** StrideCategory.DENIAL_OF_SERVICE | **Risk Severity:** RiskSeverity.MEDIUM

**Risk Score:** 9/10 Likelihood * 6/10 Impact = 54/100 Overall

**Likelihood Rationale:** This is a classic and simple attack for any system running untrusted code. Malicious or poorly written code can easily trigger this.

**Impact Rationale:** A successful DoS can render the application or thread unresponsive, impacting availability for all users.

**Current Mitigation:** The `Runner` component provides a configurable timeout for execution. The Wasm runtime has finite memory, but its limit may be high by default.

**Recommended Mitigation:** Integrators must configure a reasonably short timeout on the `Runner`. The Wasm memory limits in the Engine should also be configured to the lowest required value. Monitor CPU and memory usage.

#### 4. Script Cache Poisoning

The default script cache (`BasicScriptCache`) uses MD5 for keying. MD5 is cryptographically broken and vulnerable to collision attacks, allowing an attacker to cause the wrong compiled script to be executed.

**Component:** BasicScriptCache | **STRIDE Category:** StrideCategory.TAMPERING | **Risk Severity:** RiskSeverity.MEDIUM

**Risk Score:** 4/10 Likelihood * 7/10 Impact = 28/100 Overall

**Likelihood Rationale:** While generating MD5 collisions requires expertise, it is feasible. A system that caches untrusted, user-provided scripts is a direct target for this.

**Impact Rationale:** Executing a malicious script instead of a benign one can lead to data theft, corruption, or further attacks, though contained within the sandbox.

**Current Mitigation:** The system ships with this vulnerable component. No mitigation is in place by default.

**Recommended Mitigation:** Replace MD5 with a collision-resistant hashing algorithm like SHA-256 in the `BasicScriptCache`. Document that the default cache is not production-safe.

#### 5. `HostRef` Type Confusion

HostRefs are passed to the guest as simple integer indices. The guest can pass any integer back to a host function. If the host does not validate the type of the object at the index, it can lead to type confusion and misuse of objects.

**Component:** HostRef Mechanism | **STRIDE Category:** StrideCategory.ELEVATION_OF_PRIVILEGE | **Risk Severity:** RiskSeverity.MEDIUM

**Risk Score:** 6/10 Likelihood * 7/10 Impact = 42/100 Overall

**Likelihood Rationale:** Guest code has full control over the integer it passes back. Guessing or reusing a valid pointer from another operation is a feasible attack vector.

**Impact Rationale:** An attacker could potentially access or manipulate a Java object they were not granted access to, leading to privilege escalation or information disclosure.

**Current Mitigation:** No explicit mitigation is described. The system relies on the integer being an opaque reference that isn't tampered with.

**Recommended Mitigation:** When a host function receives a `HostRef`, the `Engine` must validate that the object at `javaRefs.get(ptr)` is of the expected type declared in the `@HostRefParam` annotation before invoking the method.

#### 6. Wasm Supply Chain Compromise

The `javy_quickjs4j_plugin.wasm` binary is a critical, pre-compiled asset. If this file is compromised in the supply chain (e.g., in the repository or build artifact), it could contain vulnerabilities allowing a sandbox escape.

**Component:** Wasm Binary | **STRIDE Category:** StrideCategory.TAMPERING | **Risk Severity:** RiskSeverity.MEDIUM

**Risk Score:** 3/10 Likelihood * 10/10 Impact = 30/100 Overall

**Likelihood Rationale:** Supply chain attacks are increasingly common. Compromising a core binary that underpins the security model is a high-value target.

**Impact Rationale:** ~~A compromised Wasm binary could completely nullify all sandbox guarantees, leading to full host compromise.~~ A compromised Wasm binary will be anyway executed in a safe sandboxed environment.

**Current Mitigation:** The Wasm file is included in the source repository, providing some level of source control.

**Recommended Mitigation:** Implement Subresource Integrity. The build process should verify the SHA-256 hash of the `.wasm` file against a known-good value. The Java code should also verify this hash at runtime before loading the module.

#### 7. Race Condition from Concurrent Access

The documentation states the Engine is not thread-safe. If an integrator uses it concurrently without external locking, race conditions can occur, corrupting invocation state (e.g., arguments, return values).

**Component:** Engine | **STRIDE Category:** StrideCategory.TAMPERING | **Risk Severity:** RiskSeverity.MEDIUM

**Risk Score:** 5/10 Likelihood * 6/10 Impact = 30/100 Overall

**Likelihood Rationale:** Integrators may misuse the API by creating their own runner or using the Engine directly in a multi-threaded context, especially if the warning is not prominent.

**Impact Rationale:** Data corruption or returning incorrect results can lead to unpredictable application behavior, data integrity issues, and potential information disclosure.

**Current Mitigation:** The default `Runner` correctly serializes access by using a `newSingleThreadExecutor`.

**Recommended Mitigation:** Make the non-thread-safe nature of the `Engine` a prominent warning in the documentation. Alternatively, refactor the `Engine` to use `ThreadLocal` storage for per-invocation state to make it inherently thread-safe.

## Quality Assessment

- Input Data Quality: 8/10 - High-quality input from source code and explicit security assumptions.
- Model Confidence: 9/10 - High confidence due to clear architecture and documented security boundaries.
