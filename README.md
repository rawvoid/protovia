# Protovia

Lightweight compile-time Protobuf for Java 21. **The Java entity is the schema** — no `.proto` files.

Annotate a POJO or record, compile, and get a generated zero-reflection `ProtoCodec` that speaks official proto3 wire format.

```java
@ProtoMessage
public class User {
    @ProtoField(number = 1) private String name;
    @ProtoField(number = 2) private int age;
    @ProtoField(number = 3) private Address address;   // nested @ProtoMessage
    @ProtoField(number = 4) private List<String> tags; // repeated
    @ProtoField(number = 5) private Map<String, Integer> scores;
    @ProtoField(number = 6) private Status status;     // @ProtoEnum
    @ProtoField(number = 7, optional = true) private Integer level;
    // getters / setters
}

@ProtoMessage
public record Address(
        @ProtoField(number = 1) String city,
        @ProtoField(number = 2) String street) {}

@ProtoEnum
public enum Status {
    @ProtoEnumValue(0) UNKNOWN,
    @ProtoEnumValue(1) ACTIVE
}

byte[] bytes = ProtoVia.toBytes(user);
User back = ProtoVia.fromBytes(User.class, bytes);
```

The bytes are readable by official Protocol Buffers implementations (Go, Python, `protoc` Java, …).

## Modules

| Artifact | Role |
|----------|------|
| `protovia-api` | Annotations, `ProtoType`, `ProtoCodec`, wire types |
| `protovia-runtime` | `ProtoVia` facade and codec lookup |
| `protovia-processor` | Annotation processor that generates `XxxProtoCodec` |
| `protovia-itest` | End-to-end + official protobuf interop tests |

Runtime has **no third-party dependencies**.

## Maven

```xml
<dependency>
  <groupId>io.github.rawvoid</groupId>
  <artifactId>protovia-runtime</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.rawvoid</groupId>
  <artifactId>protovia-processor</artifactId>
  <version>1.0-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

`protovia-runtime` brings `protovia-api` transitively. The processor is discovered via `META-INF/services` once it is on the compiler classpath (Maven `provided` is enough for a published jar).

Gradle:

```kotlin
implementation("io.github.rawvoid:protovia-runtime:1.0-SNAPSHOT")
annotationProcessor("io.github.rawvoid:protovia-processor:1.0-SNAPSHOT")
```

## How it works

1. You mark types with `@ProtoMessage` / `@ProtoEnum` and members with `@ProtoField(number = N)`.
2. At compile time the processor writes `UserProtoCodec` in the **same package** (nested types become `Outer$InnerProtoCodec`).
3. `ProtoVia.codec(User.class)` loads `UserProtoCodec.INSTANCE` by convention. No reflection on entity fields.

Generated codecs:

- precompute serialized size, then write into an exact-size buffer
- skip proto3 default values (`0`, `false`, `""`, empty bytes / lists / maps)
- write proto3 `optional` (and `Optional<T>`) even when the value is default
- read both packed and unpacked repeated scalars
- skip unknown fields

## Type mapping

| Java | Default proto | Override with `ProtoType` |
|------|---------------|---------------------------|
| `int` / `Integer` | int32 | `UINT32`, `SINT32`, `FIXED32`, `SFIXED32` |
| `long` / `Long` | int64 | `UINT64`, `SINT64`, `FIXED64`, `SFIXED64` |
| `float` / `Float` | float | |
| `double` / `Double` | double | |
| `boolean` / `Boolean` | bool | |
| `String` | string | `BYTES` |
| `byte[]`, `ByteBuffer` | bytes | |
| `@ProtoEnum` enum | enum | |
| `@ProtoMessage` type | message | |
| `@ProtoOneof` sealed interface | oneof (cases flatten onto the parent) | |
| `java.time.Instant` | `google.protobuf.Timestamp` | |
| `java.time.Duration` | `google.protobuf.Duration` | |
| `ProtoAny` | `google.protobuf.Any` | |
| `wkt.Int32Value` and the other 8 wrappers | wrapper messages | |
| `List` / `Set` / array (not `byte[]`) | repeated | `packed` (default `true` for scalars) |
| `Map<K,V>` | map | `keyType` / `valueType` |
| `Optional<T>` | proto3 optional T | |

Map keys must be integral, `bool`, or `string`. Field numbers are **required** and must stay stable.

## Entity rules

**POJO**

- Non-abstract class, non-private no-arg constructor
- Each `@ProtoField` needs a JavaBean getter+setter, or a non-private field
- Unannotated members are ignored

**Record**

- Annotate components (or accessors)
- Decode uses the canonical constructor

**Enum**

- `@ProtoEnum` on the type, `@ProtoEnumValue(n)` on every constant
- A `0` value is required (proto3)
- Unknown numbers on the wire are skipped unless the enum has `@ProtoUnrecognized`
- `@ProtoUnrecognized` is a Java-only sentinel and is never written as a number

**oneof**

- Mark the field `@ProtoOneof` (no field number). The type must be `sealed`.
- Each permitted type is `@ProtoOneofCase(n)` — that number is the parent field.
- A one-component scalar record encodes as that scalar, not a nested message.

**Unknown fields**

- Opt in with `@ProtoUnknown UnknownFields` to capture and write back unknown tags.

**Presence**

- Primitive: always “set”; `0` / `false` are omitted
- Reference: `null` is omitted; empty string / empty bytes / boxed `0` are omitted unless `optional = true`
- `optional = true` requires a boxed type or `Optional<T>`, not a primitive

## API

```java
ProtoVia.toBytes(message);
ProtoVia.fromBytes(User.class, bytes);
ProtoVia.write(outputStream, message);
ProtoVia.read(User.class, inputStream);
ProtoVia.sizeOf(message);
ProtoVia.codec(User.class);
ProtoVia.register(User.class, handWrittenCodec); // tests / override
ProtoAny packed = ProtoVia.pack(user);           // type.googleapis.com/<protoFullName>
User back = ProtoVia.unpack(packed, User.class);
```

`pack` uses `@ProtoMessage(packageName, name)`, not the Java FQCN. `Integer` stays int32; use `Int32Value` when you need the wrapper message.

Default safety limits: 64 MiB per message, nesting depth 100. Override with `ProtoVia.setMaxMessageSize` / `setMaxDepth`.

## Not in this release

`.proto` import/export, proto2 required/default, `Struct` / `Value`, inheritance, Lombok-specific integration.

## Build

```bash
mvn test
```

Java 21. Interop tests compare Protovia bytes with `protobuf-java` `DynamicMessage` in both directions.

## Benchmarks

`protovia-bench` compares Protovia with `protobuf-java` **4.35.1 generated** messages (not `DynamicMessage`):

```bash
mvn -pl protovia-bench -am package
java -jar protovia-bench/target/benchmarks.jar
```

## Documentation

Planning and architecture for maintainers live under [`docs/`](docs/README.md). The overall roadmap (completed phase 1 + later stages) is [`docs/plan.md`](docs/plan.md).
