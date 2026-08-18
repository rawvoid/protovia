# Protovia

Lightweight compile-time Protobuf for Java 21. **The Java entity is the schema** — no `.proto` files.

Annotate a POJO or record, compile, and get a generated zero-reflection `ProtoCodec` that speaks official proto3 wire format.

```java

@ProtoMessage
public class User {
  @ProtoField(number = 1)
  private String name;
  @ProtoField(number = 2)
  private int age;
  @ProtoField(number = 3)
  private Address address;   // nested @ProtoMessage
  @ProtoField(number = 4)
  private List<String> tags; // repeated
  @ProtoField(number = 5)
  private Map<String, Integer> scores;
  @ProtoField(number = 6)
  private Status status;     // @ProtoEnum
  @ProtoField(number = 7, optional = true)
  private Integer level;
  // getters / setters
}

@ProtoMessage
public record Address(
  @ProtoField(number = 1) String city,
  @ProtoField(number = 2) String street) {
}

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

| Artifact             | Role                                                |
|----------------------|-----------------------------------------------------|
| `protovia-api`       | Annotations, `ProtoType`, `ProtoCodec`, wire types  |
| `protovia-runtime`   | `ProtoVia` facade and codec lookup                  |
| `protovia-processor` | Annotation processor that generates `XxxProtoCodec` |
| `protovia-itest`     | End-to-end + official protobuf interop tests        |

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

| Java                                                      | Default proto                         | Override with `ProtoType`                 |
|-----------------------------------------------------------|---------------------------------------|-------------------------------------------|
| `int` / `Integer`                                         | int32                                 | `UINT32`, `SINT32`, `FIXED32`, `SFIXED32` |
| `long` / `Long`                                           | int64                                 | `UINT64`, `SINT64`, `FIXED64`, `SFIXED64` |
| `float` / `Float`                                         | float                                 |                                           |
| `double` / `Double`                                       | double                                |                                           |
| `boolean` / `Boolean`                                     | bool                                  |                                           |
| `String`                                                  | string                                | `BYTES`                                   |
| `byte[]`, `ByteBuffer`                                    | bytes                                 |                                           |
| `@ProtoEnum` enum                                         | enum                                  |                                           |
| `@ProtoMessage` type                                      | message                               |                                           |
| `@ProtoOneof` field (cases listed on the field)           | oneof (cases flatten onto the parent) |                                           |
| `java.time.Instant`                                       | `google.protobuf.Timestamp`           |                                           |
| `java.time.Duration`                                      | `google.protobuf.Duration`            |                                           |
| `ProtoAny`                                                | `google.protobuf.Any`                 |                                           |
| `wkt.Int32Value` and the other 8 wrappers                 | wrapper messages                      |                                           |
| adapted `J` via `@ProtoField(adapter)` / `@ProtoAdapters` | proto scalar of the adapter           |                                           |
| `List` / `Set` / array (not `byte[]`)                     | repeated                              | `packed` (default `true` for scalars)     |
| `Map<K,V>`                                                | map                                   | `keyType` / `valueType`                   |
| `Optional<T>`                                             | proto3 optional T                     |                                           |

Map keys must be integral, `bool`, or `string`. Field numbers are **required** and must stay stable.

## Custom adapters

Map a Java reference type onto an existing proto3 scalar with a `ProtoAdapter`. Sample adapters live in `io.github.rawvoid.protovia.adapter` (`LocalDateEpochDay`, `UuidString`, `InstantEpochMilli`, `DurationMilli`). They are unused unless named in `@ProtoField(adapter)` / `@ProtoAdapters` — **not** well-known types, and they are not registered in `CodecLookup`.

`Instant` and `Duration` still default to `google.protobuf.Timestamp` / `Duration`. An adapter only changes fields that mention it.

**Field-level override** — only this member becomes `int64`:

```java

@ProtoMessage
public class Audit {
  @ProtoField(number = 1)
  String id;
  @ProtoField(number = 2, adapter = InstantEpochMilli.class)
  Instant created;                         // int64 created = 2
  @ProtoField(number = 3)
  Instant published;                       // still Timestamp
}
```

**Class-level override** — every matching field on that message:

```java

@ProtoMessage
@ProtoAdapters({InstantEpochMilli.class, DurationMilli.class})
public class Event {
  @ProtoField(number = 1)
  Instant created;     // int64
  @ProtoField(number = 2)
  Instant updated;     // int64
  @ProtoField(number = 3)
  Duration ttl;        // int64 millis
}
```

A sibling message with no `@ProtoAdapters` still encodes `Instant` as Timestamp.

**Presence.** Adapted singular fields use Java reference presence: `null` is omitted; a non-null value is **always written**, including proto3 defaults such as epoch day `0` (`1970-01-01`). Missing on the wire decodes as `null` — do not call `fromWire(0)`. A Go / proto3-Java client using implicit-presence `int32` will omit `0`; Protovia then reads `null`. Use `optional = true` on both sides if unset vs epoch must survive the wire.

```java

@ProtoField(number = 3, adapter = LocalDateEpochDay.class)
LocalDate birthDate;   // 1970-01-01 writes tag + 0x00
```

## oneof

Declare a proto3 `oneof` by placing `@ProtoOneof` on a field, JavaBean getter, or record component. The oneof group itself has no field number; each `@ProtoOneof.Case(number, of)` assigns a field number on the parent message.

**Polymorphic record wrappers (recommended):**

```java
@ProtoMessage
public class Contact {
  @ProtoField(number = 1)
  private String name;

  @ProtoOneof({
    @ProtoOneof.Case(number = 10, of = Email.class),
    @ProtoOneof.Case(number = 11, of = Home.class)
  })
  private Target target;
  // getters / setters
}

public interface Target {} // sealed is optional

public record Email(String value) implements Target {}
public record Home(Address address) implements Target {}
```

Wire equivalent:

```protobuf
message Contact {
  string name = 1;
  oneof target {
    string email = 10;
    Address home = 11;
  }
}
```

- A 1-component record without `@ProtoMessage` (like `Email(String value)`) encodes directly as that scalar wire type (`string`), without an extra nested message envelope.
- A case holding a `@ProtoMessage` type (like `Address`) encodes as a nested sub-message.

**Naked scalar / message cases:**

You can also use raw types directly without custom wrapper records by declaring a common supertype such as `Object`:

```java
@ProtoMessage
public class Bag {
  @ProtoOneof({
    @ProtoOneof.Case(number = 10, of = String.class),
    @ProtoOneof.Case(number = 11, of = Address.class)
  })
  private Object data;
}
```

**Case-level type & adapter overrides:**

Individual oneof cases can customize their scalar wire type or adapter:

```java
@ProtoOneof({
  @ProtoOneof.Case(number = 10, of = Count.class, type = ProtoType.SINT32),
  @ProtoOneof.Case(number = 11, of = Born.class, adapter = LocalDateEpochDay.class)
})
private Event event;
```

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

- Mark the field `@ProtoOneof({ @ProtoOneof.Case(number, of), ... })`. The group has no field number; each `Case.number` belongs to the parent message.
- `of` is a 0- or 1-component record, a `@ProtoMessage` (class or record, including multi-component), or a naked scalar / enum / `byte[]`. `sealed` is optional and is not consulted.
- A one-component scalar record without `@ProtoMessage` encodes as that scalar, not a nested message. A `@ProtoMessage` case is `readMessage` / `writeTo` on that instance. A naked `String` case stores the string directly.

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
