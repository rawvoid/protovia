# Protovia

Lightweight compile-time Protobuf for Java 21. **The Java entity is the schema** — you do not write `.proto` files. Compiling emits them.

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

byte[] bytes = Protovia.toBytes(user);
User back = Protovia.fromBytes(bytes, User.class);
```

The bytes are readable by official Protocol Buffers implementations (Go, Python, `protoc` Java, …). Compiling also writes a `.proto` per type onto the class output (and into the jar), using protobuf import paths and `lower_snake_case` file names (`example.v1.User` → `example/v1/user.proto`, `FlightOfferId` → `flight_offer_id.proto`). Optional: `-Aprotovia.protoOut=<dir>` copies that tree to a directory for buf / Go.

## Modules

| Artifact             | Role                                                |
|----------------------|-----------------------------------------------------|
| `protovia-core`      | Annotations, core engine, wire format, adapters, `Protovia` facade |
| `protovia-processor` | Annotation processor that generates `XxxProtoCodec` |
| `protovia-itest`     | End-to-end + official protobuf interop tests        |
| `protovia-bench`     | JMH benchmarks against official `protobuf-java`     |

Runtime has **no third-party dependencies**.

## Maven

```xml
<dependency>
  <groupId>io.github.rawvoid</groupId>
  <artifactId>protovia-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.rawvoid</groupId>
  <artifactId>protovia-processor</artifactId>
  <version>1.0-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

The processor is discovered via `META-INF/services` once it is on the compiler classpath (Maven `provided` is enough for a published jar).

Gradle:

```kotlin
implementation("io.github.rawvoid:protovia-core:1.0-SNAPSHOT")
annotationProcessor("io.github.rawvoid:protovia-processor:1.0-SNAPSHOT")
```

To copy generated `.proto` files into a directory for buf / Go, pass `-Aprotovia.protoOut`:

Maven:

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <compilerArgs>
      <arg>-Aprotovia.protoOut=${project.basedir}/src/main/proto</arg>
    </compilerArgs>
  </configuration>
</plugin>
```

Gradle:

```kotlin
tasks.compileJava {
    options.compilerArgs.add("-Aprotovia.protoOut=${layout.projectDirectory.dir("src/main/proto")}")
}
```

Without that option, the files still land on class output (and in the jar) as `example/v1/user.proto`.

## How it works

1. You mark types with `@ProtoMessage` / `@ProtoEnum` and members with `@ProtoField(number = N)`.
2. At compile time the processor writes `internal.UserProtoCodec` and a `.proto` resource (`example.v1.User` → `example/v1/user.proto`). Nested types become `Outer$InnerProtoCodec`.
3. `Protovia.codec(User.class)` loads `UserProtoCodec.INSTANCE` by convention. No reflection on entity fields. The `.proto` is for other languages and docs; the runtime codec does not read it.

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

**Export names.** Wire uses numbers only. `.proto` field names default to the Java member (or the oneof-case rule below). Override with `@ProtoField(name=…)`, `@ProtoOneof(name=…)`, or `@ProtoOneof.Case(name=…)`. There is no automatic snake_case for fields. Names must be proto identifiers and cannot be keywords (`string`, `message`, …).

**Enum constants.** Java stays `Status.ACTIVE`. The generated `.proto` prefixes every constant with the enum type name (`STATUS_ACTIVE`) so values stay unique under protobuf’s C++ scoping rules. `@ProtoEnum(name=…)` is the prefix source. `@ProtoReserved(names=…)` on an enum refers to those prefixed proto names.

**Reserved.** `@ProtoReserved` on a `@ProtoMessage` / `@ProtoEnum` (or a mixin superclass) occupies retired numbers and proto names so they cannot be reused. They are written into the generated `.proto` as `reserved`.

**oneof export.** A wrapper record without `@ProtoMessage` flattens to its payload (`Email(String)` → `string email`). A case type that is itself `@ProtoMessage` exports as that message, even if it has a single string field. Empty `record Ping() {}` becomes a nested `message Ping {}` in the parent file.

## Custom adapters

Map a Java reference type onto an existing proto3 scalar with a `ProtoAdapter`. Built-in opt-in adapters live in `io.github.rawvoid.protovia.adapter`. They are unused unless named in `@ProtoField(adapter)` / `@ProtoAdapters` — **not** well-known types, and they are not registered in `CodecLookup`.

### Built-in Adapters

| Category | Java Type | Adapter Class | Proto Wire Type | Description |
|---|---|---|---|---|
| **Time** | `Instant` | `InstantEpochMilliAdapter` | `int64` | Epoch milliseconds |
| | `Instant` | `InstantEpochSecondAdapter` | `int64` | Epoch seconds (drops sub-second) |
| | `Instant` | `InstantEpochNanoAdapter` | `int64` | Epoch nanoseconds (~1678 to ~2262) |
| | `Duration` | `DurationMilliAdapter` | `int64` | Milliseconds duration |
| | `Duration` | `DurationSecondAdapter` | `int64` | Seconds duration |
| | `Duration` | `DurationNanoAdapter` | `int64` | Nanoseconds duration |
| | `LocalDate` | `LocalDateEpochDayAdapter` | `int32` | Epoch day count (days since 1970-01-01) |
| | `LocalTime` | `LocalTimeMilliOfDayAdapter` | `int32` | Millisecond of day (0..86,399,999) |
| | `LocalTime` | `LocalTimeSecondOfDayAdapter` | `int32` | Second of day (0..86,399) |
| | `LocalTime` | `LocalTimeNanoOfDayAdapter` | `int64` | Nanosecond of day (0..86,399,999,999,999) |
| | `LocalDateTime` | `LocalDateTimeEpochMilliAdapter` | `int64` | Epoch milli assuming UTC |
| | `ZonedDateTime` | `ZonedDateTimeEpochMilliAdapter` | `int64` | Epoch milli (restores as UTC) |
| | `ZonedDateTime` | `ZonedDateTimeIsoStringAdapter` | `string` | Lossless ISO-8601 with timezone ID |
| | `OffsetDateTime` | `OffsetDateTimeEpochMilliAdapter` | `int64` | Epoch milli (restores as UTC) |
| | `OffsetDateTime` | `OffsetDateTimeIsoStringAdapter` | `string` | Lossless ISO-8601 / RFC 3339 |
| | `YearMonth` | `YearMonthEpochMonthAdapter` | `int32` | 0-based epoch month (`year * 12 + month - 1`) |
| | `Year` | `YearInt32Adapter` | `int32` | Integer year (e.g. 2026) |
| | `Period` | `PeriodIsoStringAdapter` | `string` | ISO-8601 period format (`"P1Y2M3D"`) |
| | `ZoneId` | `ZoneIdStringAdapter` | `string` | IANA zone ID (`"Asia/Shanghai"`) |
| | `ZoneOffset` | `ZoneOffsetSecondsAdapter` | `int32` | Total seconds offset from UTC |
| | `Date` | `DateEpochMilliAdapter` | `int64` | Epoch milliseconds |
| **Math** | `BigDecimal` | `BigDecimalStringAdapter` | `string` | Lossless canonical plain string (`toPlainString()`) |
| | `BigInteger` | `BigIntegerStringAdapter` | `string` | Base-10 string |
| **Network & ID** | `UUID` | `UuidStringAdapter` | `string` | 36-char canonical RFC 4122 string |
| | `UUID` | `UuidBytesAdapter` | `bytes` | 16 bytes in big-endian network byte order |
| | `InetAddress` | `InetAddressBytesAdapter` | `bytes` | 4 bytes (IPv4) / 16 bytes (IPv6) |
| | `InetAddress` | `InetAddressStringAdapter` | `string` | Host IP string (`"192.168.1.1"` / `"::1"`) |
| | `URI` | `UriStringAdapter` | `string` | RFC 3986 URI string |

`Instant` and `Duration` still default to `google.protobuf.Timestamp` / `Duration`. An adapter only changes fields that mention it.

**Field-level override** — only this member becomes `int64`:

```java

@ProtoMessage
public class Audit {
  @ProtoField(number = 1)
  String id;
  @ProtoField(number = 2, adapter = InstantEpochMilliAdapter.class)
  Instant created;                         // int64 created = 2
  @ProtoField(number = 3)
  Instant published;                       // still Timestamp
}
```

**Class-level override** — every matching field on that message:

```java

@ProtoMessage
@ProtoAdapters({InstantEpochMilliAdapter.class, DurationMilliAdapter.class})
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

@ProtoField(number = 3, adapter = LocalDateEpochDayAdapter.class)
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
    Email email = 10;
    Home home = 11;
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
    @ProtoOneof.Case(number = 10, of = String.class, name = "text"),
    @ProtoOneof.Case(number = 11, of = Address.class)
  })
  private Object data;
}
```

Naked `String` and `byte[]` default to proto names `string` / `bytes`, which are keywords — set `name` to override.

**Case-level type & adapter overrides:**

Individual oneof cases can customize their scalar wire type or adapter:

```java
@ProtoOneof({
  @ProtoOneof.Case(number = 10, of = Count.class, type = ProtoType.SINT32),
  @ProtoOneof.Case(number = 11, of = Born.class, adapter = LocalDateEpochDayAdapter.class)
})
private Event event;
```

## Entity rules

**POJO (mutable)**

- Public no-arg constructor
- Each `@ProtoField` needs a JavaBean getter+setter, or a non-final public field
- Unannotated members are ignored
- Preferred when both a JavaBean path and a builder / all-args constructor exist

**Immutable class**

- Public getters (or public fields) for encode
- Decode uses one public construction path, chosen in this order:
  1. `@ProtoCreator` constructor or static factory
  2. `@ProtoBuilder` (non-standard `builder()` / setter names)
  3. `builder()` + `build()` + a setter per proto member (Lombok `@Builder` / `@SuperBuilder`, handwritten)
  4. Public all-args constructor or public static factory whose parameters match proto members
- Generated codecs live in `*.internal`, so the constructor / factory / `builder()` must be **public**. Lombok `@Value` defaults to a private constructor — use `@Builder`, `@Value(staticConstructor = "of")`, or `@AllArgsConstructor(access = PUBLIC)`
- `mergeFrom` returns a new instance

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

**Inheritance**

- A concrete `@ProtoMessage` class may extend a non-message class or abstract class. Superclass `@ProtoField` / `@ProtoOneof` / `@ProtoUnknown` members are merged into the leaf codec as one flat proto3 message.
- The superclass must **not** be `@ProtoMessage`. No codec is generated for it; this is not wire polymorphism.
- Duplicate field numbers, proto export names, or Java property names across the hierarchy fail compilation. There is no override and no same-name coexistence.
- Inherited members need a public getter (and a public setter when the leaf is a mutable JavaBean) or a public field (the generated codec lives in `.internal`). Field access casts to the superclass, so that type must be public; a package-private mixin must expose public getters (and setters if mutable). An immutable leaf must bind inherited properties on its constructor, factory, or builder.
- Generic bases work when the leaf binds the type arguments (`class UserPage extends PageResult<User> {}`). Raw / wildcard superclasses fail.
- Prefer numbers 1–15 on shared bases, 16+ on leaves. If you change only annotations on a superclass and the leaf codec does not rebuild, clean rebuild (Isolating APT).

**Unknown fields**

- Opt in with `@ProtoUnknown UnknownFields` to capture and write back unknown tags.

**Presence**

- Primitive: always “set”; `0` / `false` are omitted
- Reference: `null` is omitted; empty string / empty bytes / boxed `0` are omitted unless `optional = true`
- `optional = true` requires a boxed type or `Optional<T>`, not a primitive

## API

```java
Protovia.toBytes(message);
Protovia.fromBytes(bytes, User.class);
Protovia.write(outputStream, message);
Protovia.read(inputStream, User.class);
Protovia.sizeOf(message);
Protovia.codec(User.class);
Protovia.register(User.class, handWrittenCodec); // tests / override

ProtoAny packed = Protovia.pack(user);           // type.googleapis.com/<protoFullName>
User back = Protovia.unpack(packed, User.class);
```

`pack` uses `@ProtoMessage(packageName, name)`, not the Java FQCN. `Integer` stays int32; use `Int32Value` when you need the wrapper message.

Default safety limits: 64 MiB per message, nesting depth 100. Override with `Protovia.setMaxMessageSize` / `setMaxDepth`.

## Not in this release

`.proto` import (Java stays the schema), proto2 required/default, `Struct` / `Value`.

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
