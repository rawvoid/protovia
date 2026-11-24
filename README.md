# Protovia

Lightweight compile-time Protobuf for Java 21. **The Java entity is the schema** — you do not write `.proto` files; compiling emits them.

Annotate a POJO, immutable class, or record, compile, and get a generated zero-reflection, zero-`Unsafe` `ProtoCodec` that speaks official proto3 wire format.

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

The bytes are readable by official Protocol Buffers implementations (Go, Python, `protoc` Java, …). Compiling also writes a `.proto` per type under `SOURCE_OUTPUT/proto/` (`target/generated-sources/annotations/proto/…`), using protobuf import paths and `lower_snake_case` file names (`example.v1.User` → `example/v1/user.proto`, `com.acme.FlightOfferId` → `com/acme/flight_offer_id.proto`). Two types that snake-case to the same path fail compilation (`FooBar` and `Foo_Bar` → `foo_bar.proto`). The proto `package` (and Any `type_url`) default to the Java package; override with `@ProtoMessage(packageName = …)`. Optional: `-Aprotovia.protoOut=<dir>` copies that tree to a directory for `buf` / Go. Files are not written to class output, so they do not enter the jar.

## Modules

| Artifact             | Role                                                             |
|----------------------|------------------------------------------------------------------|
| `protovia-core`      | Annotations, wire engine, codecs, built-in adapters, `Protovia` / `ProtoConfig` facade |
| `protovia-processor` | Isolating annotation processor generating `XxxProtoCodec` and `.proto` files |
| `protovia-itest`     | End-to-end + official `protobuf-java` interop tests              |
| `protovia-bench`     | JMH benchmarks against official `protobuf-java`                  |

Runtime has **no third-party dependencies** and uses **no `sun.misc.Unsafe`**.

## Installation

Maven:

```xml
<dependency>
  <groupId>io.github.rawvoid</groupId>
  <artifactId>protovia-core</artifactId>
  <version>1.3.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.rawvoid</groupId>
  <artifactId>protovia-processor</artifactId>
  <version>1.3.0-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

The processor is discovered via `META-INF/services` once it is on the compiler classpath (Maven `provided` is enough for a published jar).

Gradle:

```kotlin
implementation("io.github.rawvoid:protovia-core:1.3.0-SNAPSHOT")
annotationProcessor("io.github.rawvoid:protovia-processor:1.3.0-SNAPSHOT")
```

To mirror generated `.proto` files into a directory for `buf` or other languages, pass `-Aprotovia.protoOut`:

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

Without that option, the files still land in generated sources as `proto/example/v1/user.proto` (Maven: `target/generated-sources/annotations/proto/…`). They are not copied into the jar.

## How it works

1. You mark types with `@ProtoMessage` / `@ProtoEnum` and members with `@ProtoField(number = N)`.
2. At compile time the processor writes `internal.UserProtoCodec` and a `.proto` file under `SOURCE_OUTPUT/proto/` (`example.v1.User` → `proto/example/v1/user.proto`). Nested types become `Outer$InnerProtoCodec`.
3. `Protovia.codec(User.class)` loads `UserProtoCodec.INSTANCE` by convention and caches it via `ClassValue`. No reflection on entity fields. The `.proto` is for other languages and docs; the runtime codec does not read it.

Generated codecs:

- precompute serialized size, then write into an exact-size buffer
- skip proto3 default values (`0`, `false`, `""`, empty bytes / lists / maps)
- write proto3 `optional` (and `Optional<T>`) even when the value is default
- read both packed and unpacked repeated scalars
- decode a repeated / map field absent from the wire as an empty collection, never `null` (array-typed repeated fields stay `null`)
- skip unknown fields (or capture and write them back via `@ProtoUnknown UnknownFields`)

**Decoded collections are mutable.** A repeated / map field is decoded into a mutable implementation chosen for the declared type — `ArrayList` (or `protovia-core`'s specialized primitive `*ArrayList` for repeated primitive scalars) for `List`/`Collection`, `LinkedHashSet` for `Set`, `TreeSet` for `SortedSet`/`NavigableSet`, `LinkedHashMap` for `Map`, `TreeMap` for `SortedMap`/`NavigableMap`, `ConcurrentHashMap`/`ConcurrentSkipListMap` for `ConcurrentMap`/`ConcurrentNavigableMap`, or the declared concrete mutable type directly (`ArrayList`, `LinkedList`, `HashSet`, `TreeSet`, `HashMap`, `LinkedHashMap`, `TreeMap`, `*ArrayList`, …) when it exposes a public no-arg constructor — and the entity owns it. This differs from `protobuf-java`, where `Message` getters return read-only collections and all mutation goes through a `Builder`. Protovia decodes into user-owned entities, so modifying a decoded collection in place is valid. If you need a frozen collection, copy or wrap it in the model itself, for example in a record compact constructor (`tags = List.copyOf(tags)`), a setter, or with `Collections.unmodifiableList(...)`.

## Type mapping

| Java                                                      | Default proto                         | Override with `ProtoType`                 |
|-----------------------------------------------------------|---------------------------------------|-------------------------------------------|
| `int` / `Integer`                                         | `int32`                               | `UINT32`, `SINT32`, `FIXED32`, `SFIXED32` |
| `long` / `Long`                                           | `int64`                               | `UINT64`, `SINT64`, `FIXED64`, `SFIXED64` |
| `float` / `Float`                                         | `float`                               |                                           |
| `double` / `Double`                                       | `double`                              |                                           |
| `boolean` / `Boolean`                                     | `bool`                                |                                           |
| `String`                                                  | `string`                              |                                           |
| `byte[]`, `ByteBuffer`                                    | `bytes`                               |                                           |
| `@ProtoEnum` enum                                         | `enum`                                |                                           |
| `@ProtoMessage` type                                      | `message`                             |                                           |
| `@ProtoOneof` field (cases listed on the field)           | `oneof` (cases flatten onto parent)   |                                           |
| `java.time.Instant`                                       | `google.protobuf.Timestamp`           |                                           |
| `java.time.Duration`                                      | `google.protobuf.Duration`            |                                           |
| `ProtoAny`                                                | `google.protobuf.Any`                 |                                           |
| `wkt.Int32Value` and the other 8 wrappers                 | wrapper messages                      |                                           |
| adapted `J` via `@ProtoField(adapter)` / `@ProtoAdapters` | proto scalar of the adapter           |                                           |
| `List` / `Set` / `Collection` / array (not `byte[]`)      | `repeated`                            | `packed` (default `true` for scalars)     |
| `Map<K, V>`                                               | `map`                                 | `keyType` / `valueType`                   |
| `Optional<T>`                                             | proto3 `optional T`                   |                                           |

Map keys must be integral, `bool`, or `string`. Field numbers are **required** and must stay stable. Concrete mutable collections/maps with a public no-arg constructor (`ArrayList`, `LinkedList`, `HashSet`, `TreeSet`, `HashMap`, `LinkedHashMap`, `TreeMap`, `*ArrayList`, …), sorted/concurrent interfaces (`SortedSet`, `NavigableSet`, `SortedMap`, `NavigableMap`, `ConcurrentMap`, `ConcurrentNavigableMap`), `byte[][]` (`repeated bytes`), `Map<K, ByteBuffer>`, and `Optional<byte[]>` / `@ProtoField(optional = true) byte[]` are all supported.

**Export names.** Wire uses numbers only. `.proto` field names default to the Java member (or the oneof-case rule below). Override with `@ProtoField(name = …)`, `@ProtoOneof(name = …)`, or `@ProtoOneof.Case(name = …)`. There is no automatic snake_case for fields. Names must be proto identifiers and cannot be keywords (`string`, `message`, …).

**Enum constants.** Java stays `Status.ACTIVE` (or `Active` / `unknown`). The generated `.proto` prefixes every constant with the enum type name and snake-cases the suffix to `UPPER_SNAKE_CASE` (`STATUS_ACTIVE`, `Status.ActiveUser` → `STATUS_ACTIVE_USER`) so values stay unique under protobuf’s C++ scoping rules. Two Java constants that normalize to the same proto name fail compilation. `@ProtoEnum(name = …)` is the prefix source. `@ProtoReserved(names = …)` on an enum refers to those prefixed proto names.

**Reserved.** `@ProtoReserved` on a `@ProtoMessage` / `@ProtoEnum` (or a mixin superclass) occupies retired numbers and proto names so they cannot be reused. They are written into the generated `.proto` as `reserved`.

**Deterministic maps.** Map fields are written in `Map` iteration order (`LinkedHashMap` = insertion order). Annotate with `@ProtoDeterministic` to sort entries by **wire key** so the same logical map yields the same bytes (signing, diffs). Resolution, first match wins: field / getter / record component → `@ProtoMessage` type → mixin superclasses (near to far) → the leaf type's `package-info` → off.

```java
@ProtoField(number = 1)
@ProtoDeterministic
Map<String, Integer> scores;          // this field only

@ProtoMessage
@ProtoDeterministic
public class SignedEnvelope { ... }   // every map on this message

@ProtoDeterministic                   // default for every @ProtoMessage in the package
package example.v1;
```

A mixin superclass annotated `@ProtoDeterministic` applies to **every map** on each `@ProtoMessage` leaf that extends it, including maps the subclass declares. `@ProtoDeterministic(false)` on a field or on the leaf opts out. Nested messages are independent: annotating `User` does not sort maps inside `Address`. Non-map members and `@ProtoEnum` types cannot carry the annotation.

Sort order is Java natural order for `string` / `bool` / signed integers and unsigned order for `uint32` / `fixed32` / `uint64` / `fixed64`. String map keys are ordered by Java `String::compareTo` (UTF-16 code units), which matches UTF-8 byte order for all BMP characters (`U+0000..U+FFFF`) and differs only when comparing supplementary characters (`U+10000+`) against `U+E000..U+FFFF`. Adapted keys are sorted after `toWire`. Deterministic serialization sorts once per map during `computeSize` and caches the sorted snapshot (including single-pass Schwartzian key adaptation) via `SizeCache`, so `writeTo` reuses it without re-sorting or re-adapting. This is not byte-identical to C++ `deterministic_serialization`. Unannotated maps keep the previous write path with no extra allocation. The generated `.proto` is unchanged (`map<k, v>`).

## Custom adapters

Map a Java reference type onto an existing proto3 scalar with a `ProtoAdapter`. Built-in opt-in adapters live in `io.github.rawvoid.protovia.adapter`. They are unused unless named in `@ProtoField(adapter)` / `@ProtoAdapters` — **not** well-known types, and they are not registered in `CodecLookup`.

### Built-in adapters

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
| | `ZonedDateTime` | `ZonedDateTimeIsoStringAdapter` | `string` | Lossless Java ISO-8601 + `[Zone]` (`ISO_ZONED_DATE_TIME` / RFC 9557) |
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
| | `InetAddress` | `InetAddressStringAdapter` | `string` | Strict numeric IPv4/IPv6 literal, no DNS (`"192.168.1.1"` / `"::1"`) |
| | `URI` | `UriStringAdapter` | `string` | RFC 3986 URI string |

`Instant` and `Duration` still default to `google.protobuf.Timestamp` / `Duration`. An adapter only changes fields that mention it.

- `ZonedDateTimeIsoStringAdapter` emits Java's `ISO_ZONED_DATE_TIME` / RFC 9557 extension format (`2026-08-18T15:47:05.123+08:00[Asia/Shanghai]`). Non-Java strict RFC 3339 parsers will not accept the `[Zone]` suffix verbatim without stripping or supporting it; use `OffsetDateTimeIsoStringAdapter` or `Instant` for strict RFC 3339 interoperability.
- `InetAddressStringAdapter` parses strict IPv4/IPv6 numeric literals purely in memory via `InetAddress.getByAddress` with zero DNS lookups, rejecting empty strings and hostnames.

### Writing & applying adapters

Implement `ProtoAdapter<J, W>` with `@ProtoScalar(...)` and a `public static final INSTANCE` singleton:

```java
@ProtoScalar(ProtoType.INT64)
public final class MoneyCentsAdapter implements ProtoAdapter<Money, Long> {
  public static final MoneyCentsAdapter INSTANCE = new MoneyCentsAdapter();
  private MoneyCentsAdapter() {}

  @Override public Long toWire(Money value) { return value.cents(); }
  @Override public Money fromWire(Long wire) { return Money.ofCents(wire); }
}
```

Adapter discovery precedence (first match wins): field / oneof case → `@ProtoAdapters` on the `@ProtoMessage` → `@ProtoAdapters` on mixin superclasses (near to far) → `@ProtoAdapters` on the leaf type's `package-info` → `@ProtoAdapted(MoneyCentsAdapter.class)` on user-owned type `J` → built-in scalar / WKT mapping.

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

A sibling message with no `@ProtoAdapters` still encodes `Instant` as `Timestamp`.

**Presence.** Adapted singular fields use Java reference presence: `null` is omitted; a non-null value is **always written**, including proto3 defaults such as epoch day `0` (`1970-01-01`). Missing on the wire decodes as `null` — `fromWire(0)` is never called. A Go / proto3-Java client using implicit-presence `int32` will omit `0`; Protovia then reads `null`. Use `optional = true` on both sides if unset vs epoch must survive the wire.

```java
@ProtoField(number = 3, adapter = LocalDateEpochDayAdapter.class)
LocalDate birthDate;   // 1970-01-01 writes tag + 0x00
```

## oneof

Declare a proto3 `oneof` by placing `@ProtoOneof` on a field, JavaBean getter, or record component. The oneof group itself has no field number; each `@ProtoOneof.Case(number, of)` assigns a field number on the parent message. `sealed` is optional and is not consulted by the processor.

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

Wire & `.proto` equivalent:

```protobuf
message Contact {
  string name = 1;
  oneof target {
    string email = 10;
    Address address = 11;
  }
}
```

- A 1-component record without `@ProtoMessage` (like `Email(String value)`) flattens directly to its payload wire type (`string`) with no extra message envelope.
- A 0-component record (`record Ping() {}`) encodes as an empty message and exports as a nested `message Ping {}` in the parent `.proto`.
- A case type that is itself `@ProtoMessage` (class or record, including multi-component) encodes as a nested sub-message directly on that instance (`readMessage` / `writeTo`).
- An unmatched non-null runtime value throws `ProtoException` during `computeSize` / `writeTo`.

**Naked scalar / message cases:**

Use raw types directly without wrapper records via a common supertype such as `Object`:

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

```java
@ProtoOneof({
  @ProtoOneof.Case(number = 10, of = Count.class, type = ProtoType.SINT32),
  @ProtoOneof.Case(number = 11, of = Born.class, adapter = LocalDateEpochDayAdapter.class)
})
private Event event;
```

## Entity rules

`@ProtoMessage` types must belong to a named Java package (the default/unnamed package is rejected).

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
  4. Public all-args constructor or public static factory whose parameters match proto members (without `-parameters`, positional binding requires unambiguous parameter types)
- Generated codecs live in `*.internal`, so the constructor / factory / `builder()` must be **public**. Lombok `@Value` defaults to a private constructor — use `@Builder`, `@Value(staticConstructor = "of")`, or `@AllArgsConstructor(access = PUBLIC)`
- `mergeFrom` returns a new instance

**Record**

- Annotate components (or accessors)
- Decode uses the canonical constructor

**Enum**

- `@ProtoEnum` on the type, `@ProtoEnumValue(n)` on every constant
- A `0` value is required (proto3)
- Unknown enum numbers on the wire are captured in `@ProtoUnknown UnknownFields` when declared (including raw full map entries for unknown enum map values). On the entity fields themselves, singular/oneof fields decode to `null` (or `@ProtoUnrecognized` if declared; `Optional.empty()` / `Optional.of(UNRECOGNIZED)` for `Optional`), repeated unknown values are skipped, and map entries with unknown enum values are omitted without coercing to `0`
- `@ProtoUnrecognized` is a Java-only sentinel and is never written as a number

**Inheritance**

- A concrete `@ProtoMessage` class may extend a non-message class or abstract class. Superclass `@ProtoField` / `@ProtoOneof` / `@ProtoUnknown` members are merged into the leaf codec as one flat proto3 message.
- The superclass must **not** be `@ProtoMessage`. No codec is generated for it; this is not wire polymorphism.
- Duplicate field numbers, proto export names, or Java property names across the hierarchy fail compilation. There is no override and no same-name coexistence.
- Inherited members need a public getter (and a public setter when the leaf is a mutable JavaBean) or a public field (the generated codec lives in `.internal`). Field access casts to the superclass, so that type must be public; a package-private mixin must expose public getters (and setters if mutable). An immutable leaf must bind inherited properties on its constructor, factory, or builder.
- Generic bases work when the leaf binds the type arguments (`class UserPage extends PageResult<User> {}`). Raw / wildcard superclasses fail.
- Prefer numbers 1–15 on shared bases, 16+ on leaves. If you change only annotations on a superclass and the leaf codec does not rebuild, clean rebuild (Isolating APT).

**Unknown fields**

- Opt in with `@ProtoUnknown UnknownFields` to capture and write back unknown tags (including unknown enum values and raw map entries whose enum value is unknown).
- Singular and oneof unknown enum entries captured in `UnknownFields` are automatically stripped when overwritten by wire data (decode / `mergeFrom`) or mutated to a known value in Java before re-serialization.

**Presence**

- Primitive: always “set”; `0` / `false` are omitted
- Reference: `null` is omitted; empty string / empty bytes / boxed `0` are omitted unless `optional = true`
- `optional = true` requires a reference type (boxed primitive, `String`, `byte[]`, `ByteBuffer`, enum, message, adapted type) or `Optional<T>`, not a primitive (`Optional<byte[]>` and `@ProtoField(optional = true) byte[]` are both supported)

## API

```java
Protovia.toBytes(message);
Protovia.fromBytes(bytes, User.class);
Protovia.fromBytes(bytes, offset, length, User.class);
Protovia.toByteBuffer(message);
Protovia.fromByteBuffer(byteBuffer, User.class);      // zero-copy for heap buffers; position/limit unchanged
Protovia.mergeFrom(bytes, existingUser);              // proto3 merge (mutates POJO / returns new record)
Protovia.write(outputStream, message);
Protovia.read(inputStream, User.class);
Protovia.writeDelimited(outputStream, message);       // varint32 length-prefixed framing
Protovia.readDelimited(inputStream, User.class);      // returns null on clean EOF
Protovia.sizeOf(message);
Protovia.codec(User.class);
Protovia.register(User.class, handWrittenCodec);      // top-level lookup override

ProtoConfig limits = Protovia.withLimits(1024 * 1024, 32); // isolated limits without mutating global defaults
ProtoConfig snapshot = Protovia.config();
User bounded = limits.fromBytes(bytes, User.class);

ProtoAny packed = Protovia.pack(user);                // type.googleapis.com/<protoFullName>
User back = Protovia.unpack(packed, User.class);
User boundedAny = packed.unpack(Protovia.codec(User.class), limits);
```

`pack` uses `@ProtoMessage(packageName, name)`, not the Java FQCN. Blank `packageName` defaults to the Java package (`demo.User` → `type.googleapis.com/demo.User`). `Integer` stays `int32`; use `Int32Value` when you need the wrapper message.

### Merge semantics (`mergeFrom`)

`Protovia.mergeFrom` and `ProtoConfig.mergeFrom` follow official proto3 merge rules. Mutable POJOs are updated in place and returned; records and immutable classes return a new merged instance.

| Field kind | Merge behavior |
|---|---|
| Scalar / enum / `optional` (`Optional<T>`) | Incoming wire value overwrites existing value (absent wire fields keep existing value) |
| Repeated (`List` / `Set` / `Collection` / array) | Incoming elements append to existing elements |
| `Map<K, V>` | Separate wire entries replace per key (`put`); duplicate value tags within a single wire entry merge message values |
| Nested `@ProtoMessage` | Recursive field-level merge into the existing sub-message |
| `oneof` | Incoming message case matching the current branch merges recursively; any other branch replaces the whole oneof |
| `@ProtoUnknown UnknownFields` | Appends newly captured fields; strips overwritten singular/oneof unknown enum entries |
| Well-known types (`Timestamp`, `Duration`, `ProtoAny`, wrappers) | Field-level merge (an empty wire payload does not wipe existing fields) |
| Adapted fields | Follow the corresponding scalar, repeated, map, or oneof merge rule |

### `Protovia.register(...)` scope

`Protovia.register(type, codec)` overrides top-level `Protovia.*` / `ProtoConfig.*` lookups (including `Protovia.unpack` and `ProtoConfig.unpack`). Generated codecs invoke child `XxxProtoCodec.INSTANCE` directly for static dispatch, so registering an override for a nested child type does not intercept generated parent codecs — to customize a nested type, replace the parent codec, invoke a custom codec at the call site, or use a `ProtoAdapter`.

### Safety limits

Default limits are 64 MiB per message and nesting depth 100. Prefer immutable `ProtoConfig` (`Protovia.withLimits(maxMessageSize, maxDepth)` / `Protovia.config()`) for isolated per-call or multi-tenant limits without mutating global defaults (`Protovia.setMaxMessageSize` / `setMaxDepth`). `ProtoConfig` exposes the same encoding, decoding, merging, `ByteBuffer`, delimited stream, and `Any` operations as `Protovia`; `ProtoAny.unpack(codec, config)` also accepts a `ProtoConfig`.

## Not in this release

`Struct` / `Value` / `ListValue`, proto3 JSON mapping, streaming `InputStream` refill, `.proto` import (Java stays the schema), proto2 required/default.

## Build

```bash
mvn test
```

Java 21. Interop tests compare Protovia bytes with `protobuf-java` `DynamicMessage` in both directions.

## Benchmarks

`protovia-bench` compares Protovia against `protobuf-java` **4.36.2 generated** messages (not `DynamicMessage`):

```bash
mvn -pl protovia-bench -am package
java -jar protovia-bench/target/benchmarks.jar
```
