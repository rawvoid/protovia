# Protovia Roadmap

Protovia's core proto3 feature set, wire hot-path optimizations, `.proto` export, type adapters, MappedSuperclass inheritance, Lombok interoperability, `ProtoConfig`, and wide-DTO splitting are complete and production-ready.

This document tracks the remaining high-value work planned for Protovia.

---

## 1. Locked Architectural Principles

Any future work must preserve these invariants:

1. **Java Entity is the Schema** — no `.proto` authoring required; compiling emits valid proto3.
2. **Explicit, stable field numbers** — never auto-assign numbers.
3. **Zero reflection on entity fields & zero `Unsafe`** — pure Java 21 static codegen + `VarHandle`.
4. **Zero third-party dependencies in `protovia-core`** — optional integrations live in separate modules.
5. **Isolating incremental APT** — per-type codecs and `.proto` outputs with no global aggregated registry.
6. **Official proto3 wire interoperability** — verified bidirectionally against `protobuf-java`.

---

## 2. Prioritized Backlog

### High Priority (Production Polish & Core Completeness)

#### 1. True Streaming I/O (`InputStream` refill & buffered `OutputStream` writing)
- **Problem**: `ProtoConfig.read(InputStream)`, `readDelimited(InputStream)`, `write(OutputStream)`, and `writeDelimited(OutputStream)` currently buffer the entire message into a single `byte[]` (`readBounded` / `readNBytes` / `toBytes`). For multi-megabyte payloads or high-throughput framed streams (sockets, Kafka batches, delimited log files), this doubles peak memory usage and adds GC pressure compared to `protobuf-java`'s streaming `CodedInputStream`/`CodedOutputStream`.
- **Direction**:
  - Add a streaming refill path (or dedicated streaming reader/writer mode) that parses directly from a reusable fixed-size buffer (e.g., 4–8 KiB) while still strictly enforcing `maxMessageSize` and `maxDepth`.
  - Keep the zero-overhead `byte[]` fast path completely untouched for small/in-memory messages.

#### 2. `google.protobuf.Struct` / `Value` / `ListValue`
- **Problem**: Protovia already ships built-in codecs for `Timestamp`, `Duration`, `Any`, and all 9 wrapper types, leaving `Struct` / `Value` / `ListValue` as the only missing core proto3 well-known types. These are frequently needed when interoperating with Go, gRPC, or Google APIs carrying dynamic JSON-like metadata.
- **Direction**:
  - Add immutable value types (`ProtoStruct`, `ProtoValue`, `ProtoListValue`) and their zero-dependency `ProtoCodec` implementations under `io.github.rawvoid.protovia.wkt`.
  - Register `google/protobuf/struct.proto` in `WellKnownProtos` for `.proto` export and verify bidirectional interoperability against `com.google.protobuf.Struct` / `Value` / `ListValue`.

---

### Medium Priority (Ecosystem Expansion — Build When User Demand Justifies)

#### 3. Proto3 JSON Mapping (Separate Module: `protovia-json`)
- **Use case**: gRPC-Gateway, Envoy JSON transcoding, and cross-language diagnostic logging where canonical proto3 JSON rules matter (64-bit integers as decimal strings, RFC 3339 timestamps, enum names, `Any` `@type`, `Struct`/`Value`).
- **Constraints**:
  - Must live in an optional separate module (`protovia-json`) so `protovia-core` remains zero-dependency and lightweight.
  - Must reuse existing schema metadata and proto export names (`@ProtoField(name)` / `protoFullName()`), with no second annotation vocabulary.
  - Adapted fields serialize according to their **proto wire scalar type** (e.g., an `int32` epoch-day `LocalDate` writes as a JSON number, not an ISO string); do not add `toJson`/`fromJson` hooks to `ProtoAdapter`.

#### 4. Kotlin `data class` Interoperability
- **Use case**: JVM backend services written in Kotlin (Spring Boot Kotlin, Ktor) adopting Protovia.
- **Direction**: Start with a lightweight compatibility matrix and integration tests under KAPT (default-parameter constructors, Kotlin non-null semantics, `copy()` / immutable construction binding, `@ProtoField` site targets `@field:` / `@get:`) before considering a dedicated KSP processor.

#### 5. GraalVM Native Image / AOT Support
- **Use case**: Native-compiled cloud-native services where Protovia's zero-`Unsafe`, compile-time codec architecture is already a natural fit.
- **Direction**: Document and verify Native Image reachability for `CodecLookup`'s `Class.forName` convention (or provide an optional APT-generated `META-INF/native-image/.../reflect-config.json` registering only `*.internal.*ProtoCodec` classes and their `INSTANCE` field).
