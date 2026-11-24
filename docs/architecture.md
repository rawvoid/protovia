# Protovia Architecture

Protovia is a compile-time Protobuf library for Java 21 built around one core thesis: **the Java entity is the schema**. Users annotate POJOs, immutable classes, records, and enums directly; annotation processing generates zero-reflection `ProtoCodec` implementations speaking official proto3 wire format, alongside standard `.proto` files for cross-language consumers.

```text
@ProtoMessage / @ProtoEnum (Java entity)
        │
        │  javac + APT (protovia-processor, Isolating)
        ├───────────────────────────────────────►  SOURCE_OUTPUT/proto/<path>.proto (+ optional -Aprotovia.protoOut)
        ▼
  <pkg>.internal.<Type>ProtoCodec.INSTANCE  (zero entity reflection, zero Unsafe)
        │
        ▼
  ProtoWriter / ProtoReader / Utf8 / SizeCache  (hand-written proto3 wire engine)
        │
        ▼
  Official proto3 wire bytes (interoperable with protoc Java, Go, Python, C++, …)
```

---

## 1. Modules

| Module | Responsibility | Runtime Dependencies |
|---|---|---|
| `protovia-core` | Annotations, `ProtoType`, `ProtoCodec`, `ProtoAdapter`, wire engine (`ProtoReader`/`ProtoWriter`/`CodedSize`/`Utf8`/`SizeCache`/`ProtoMaps`), unboxed primitive lists, well-known types, 28 opt-in built-in adapters, `UnknownFields`, `ProtoConfig`, `Protovia`, `CodecLookup` | None (zero third-party deps) |
| `protovia-processor` | Isolating APT (`ProtoviaProcessor`): parses Java models, validates proto3 schema rules, emits `*ProtoCodec` sources (via JavaPoet) and per-type `.proto` files | `protovia-core`, `javapoet` (compile-time only for users) |
| `protovia-itest` | End-to-end tests + bidirectional interoperability against official `protobuf-java` `DynamicMessage` and WKT classes | Test-only (`protobuf-java`, JUnit, Lombok) |
| `protovia-bench` | JMH benchmarks comparing Protovia against `protoc`-generated `protobuf-java` classes | Benchmark only (not published) |

User projects depend on `protovia-core` at runtime and `protovia-processor` as an annotation processor (`provided` / `annotationProcessor`).

---

## 2. Runtime Entry Points & Codec Lookup

- **`Protovia` & `ProtoConfig`**:
  - `Protovia` holds process-wide defaults (64 MiB max message size, nesting depth 100) in an `AtomicReference<ProtoConfig>`, preventing torn concurrent updates.
  - `ProtoConfig` is immutable and thread-safe (`Protovia.withLimits(maxMessageSize, maxDepth)` / `Protovia.config()` / `ProtoConfig.of(...)`), enabling isolated per-call or multi-tenant limits without mutating global state.
  - Both expose the full API surface: `toBytes`, `fromBytes`, `toByteBuffer`, `fromByteBuffer` (zero-copy for heap buffers, non-mutating position/limit), `mergeFrom`, `write`, `read`, `writeDelimited`, `readDelimited`, `sizeOf`, `pack`, `unpack`, and `is`. `ProtoAny.unpack(codec, ProtoConfig)` also enforces custom limits.
- **`CodecLookup`**:
  - Resolves `com.example.User` → `com.example.internal.UserProtoCodec.INSTANCE` (nested types: `Outer$InnerProtoCodec`).
  - Built-in well-known types (`Instant`, `Duration`, `ProtoAny`, and the 9 `wkt.*Value` wrappers) live in an immutable `BUILTINS` map.
  - Resolved codecs are cached in a `ClassValue<ProtoCodec<?>>` for lock-free hot reads while still allowing dynamically loaded `ClassLoader`s to be garbage-collected.
  - Manual overrides registered via `Protovia.register(type, codec)` live in a separate `OVERRIDES` `ConcurrentHashMap` guarded by a `volatile boolean hasOverrides` fast path (zero map lookup when no override is registered).
- **Static dispatch & `Protovia.register` scope**:
  - `Protovia.register` overrides top-level `Protovia.*` and `ProtoConfig.*` lookups (including `Protovia.unpack` and `ProtoConfig.unpack`).
  - Generated parent codecs invoke child `XxxProtoCodec.INSTANCE` statically rather than going through `CodecLookup`.
  - No global aggregated registry class is generated, preserving Gradle Isolating incremental compilation.
- **`ProtoCodec.protoFullName()`**:
  - Used by `ProtoAny` `type_url` (`type.googleapis.com/<protoFullName>`) and `.proto` export.
  - Defaults `packageName` to the Java package when blank; well-known types return fixed `google.protobuf.*` names.

---

## 3. Wire Engine & Hot-Path Design

- **Exact two-pass encoding & `SizeCache`**:
  - `ProtoConfig.toBytes` runs `computeSize(msg, sizes)` first, allocates an exact-size `byte[]` (or returns a shared `EMPTY_BYTES` singleton when size is `0`), then runs `writeTo(writer, msg)`.
  - `SizeCache` records nested message lengths, packed repeated byte lengths, map entry lengths, and cached deterministic map snapshots in strict pre-order so `writeTo` never recomputes nested sizes.
  - Flat messages (no nested messages, maps, packed repeated fields, message repeated fields, or non-empty-message oneofs) override `ProtoCodec.needsSizeCache()` to return `false`, allowing `toBytes` to pass `SizeCache.NOOP` and avoid allocating a `SizeCache` completely.
  - All codecs are stateless and thread-safe; `ProtoReader`, `ProtoWriter`, and `SizeCache` are single-threaded per operation.
- **Primitive & varint hot paths**:
  - `CodedSize` uses branchless `Integer.numberOfLeadingZeros` / `Long.numberOfLeadingZeros` formulas matching `protobuf-java` 4.36.2.
  - `ProtoReader` uses unrolled varint32/varint64 fast paths with 10-byte varint continuation-bit validation (matching `protobuf-java` on non-canonical 10th-byte payloads while rejecting >10-byte varints), overflow-safe `require(int)` / `pushLimit`, and little-endian `VarHandle` views for `fixed32`/`fixed64`/`float`/`double`.
  - Generated codecs emit `writer.writeRawByte` directly for compile-time 1-byte tags (field numbers 1–15 and map entry inner tags 1 & 2); `ProtoWriter` writes `ByteBuffer` payloads zero-copy via `ByteBuffer.get(pos, buf, off, len)` and uses `VarHandle` for fixed-width writes.
- **UTF-8 (`Utf8`)**:
  - Encoding takes an ASCII fast path and writes directly into the target `ProtoWriter` buffer, replacing unpaired surrogates with `'?'` (`0x3F`), matching `String.getBytes(UTF_8)` and `protobuf-java`.
  - Decoding follows `protobuf-java` 4.36's `ServerProcessor` strategy: delegate directly to JDK `new String(buf, offset, length, UTF_8)`, and only if `U+FFFD` appears in the result, verify round-trip UTF-8 bytes via `Arrays.equals` to reject malformed byte sequences with `ProtoException`.
- **Unboxed collections & mutability**:
  - `protovia-core` provides 5 specialized `RandomAccess` primitive lists (`IntArrayList`, `LongArrayList`, `FloatArrayList`, `DoubleArrayList`, `BooleanArrayList`) with lazy empty backing arrays, O(1) `clear()`, O(N) `removeRange`, and zero-boxing `equals`/`hashCode`/`indexOf`/`lastIndexOf`/`contains`.
  - `ProtoLists` supplies PECS-bounded `ensureMutableList`/`Set`/`Collection`/`Map` and `ensureMutablePrimitiveList` across standard interfaces (`List`, `Set`, `SortedSet`, `NavigableSet`, `Collection`, `Map`, `SortedMap`, `NavigableMap`, `ConcurrentMap`, `ConcurrentNavigableMap`) and concrete mutable implementations with a public no-arg constructor (`ArrayList`, `LinkedList`, `HashSet`, `TreeSet`, `HashMap`, `LinkedHashMap`, `TreeMap`, `*ArrayList`, …).
  - Decoded collections are mutable and owned by the entity; absent repeated/map fields decode to empty collections rather than `null` (array fields remain `null`).
- **Packed validation & anti-amplification**:
  - Fixed-width packed repeated fields validate alignment before reading (`(_rem & 3) != 0` for 4-byte types, `(_rem & 7) != 0` for 8-byte types → `ProtoException.truncated`) and pre-allocate exact element counts (`_rem >>> 2`, `_rem >>> 3`; `bool` uses `remaining`).
  - Packed varint repeated fields (`int32`, `uint32`, `sint32`, `int64`, `uint64`, `sint64`, `enum`) intentionally avoid pre-allocating by byte count, letting lists grow geometrically to prevent 6×–8× memory amplification DoS.
  - Generated POJO decoders cache lazy mutable collection/map locals (`_col_<n>`) across loop iterations (at most one getter/ensure/setter per present field) and hoist `instanceof *ArrayList` checks outside packed loops (skipping `instanceof` completely for concrete `*ArrayList` fields to satisfy Java 21 JEP 441).
- **Deterministic maps (`@ProtoDeterministic`)**:
  - Resolution priority (first match wins): member → leaf `@ProtoMessage` → mixin superclasses (near to far) → leaf `package-info` → `false`.
  - `ProtoMaps.sortedEntriesArray` (unadapted keys) and `ProtoMaps.sortedAdaptedEntries` (single-pass Schwartzian transform into `KeyedEntry<W, V>[]`, invoking `adapter.toWire` once per entry) sort once during `computeSize` and push the snapshot into `SizeCache` for reuse in `writeTo` (with automatic fallback when `writeTo` is called without a cache).
  - String keys use Java `String::compareTo` (UTF-16 code units); unsigned integer keys (`uint32`/`fixed32`/`uint64`/`fixed64`) use unsigned comparison.
- **Unknown fields & unknown enums**:
  - Opt-in `@ProtoUnknown UnknownFields` captures raw tags via `UnknownFields.Builder` (lazily allocated growing `ProtoWriter`, amortized O(N) capture, 64-bit `presentMask` for O(1) miss checks on `removeField`/`removeFields`).
  - Singular and oneof unknown enum values captured in `UnknownFields` are stripped both on wire overwrite (`_ub.removeField`/`removeFields` during decode/`mergeFrom`) and on application-layer mutation (`serializedSizeExcluding`/`writeToExcluding` during `computeSize`/`writeTo`), preventing stale unknown enums from resurrecting.
  - Closed enums receiving an unknown value clear the Java field to `null` / `Optional.empty()` so untouched round-trips preserve the captured varint.
  - Map entries whose enum value is unknown are never coerced to `0`: they are captured raw via `_ub.mergeRange(...)` when `@ProtoUnknown` is present, or dropped cleanly otherwise.

---

## 4. Annotation Processor & Code Generation

### Parse Pipeline (`protovia-processor/.../parse`)

1. **`ProtoviaProcessor`**: Isolating processor (`SupportedAnnotationTypes`: `@ProtoMessage`, `@ProtoEnum`), supporting multi-round Lombok deferral when `@Builder` / `@SuperBuilder` / `@Value` / `@Data` / constructor annotations have not yet materialized methods.
2. **`SchemaParser` / `MemberScanner` / `Roles` / `MessageScope`**:
   - Rejects `@ProtoMessage` in the default (unnamed) package.
   - Uses `LinkedHashMap` and declaration-order method lists so member ordering is deterministic across JVMs.
   - Flattens non-`@ProtoMessage` superclasses (MappedSuperclass) via `Types.asMemberOf` (specializing generic bases; rejecting raw/wildcard bases, `@ProtoMessage` superclasses, interface mixins, and any number / proto-name / Java-property collision).
   - Enforces explicit field numbers in `[1, 536870911]` excluding `[19000, 19999]`, plus `@ProtoReserved` number/range/name checks.
3. **`FieldResolver` / `OneofParser` / `AdapterResolver` / `TypeEnv` / `ConstructionResolver`**:
   - Resolves adapters in strict precedence (highest to lowest): field / oneof case → leaf `@ProtoMessage` `@ProtoAdapters` → mixin superclasses (near to far) → leaf `package-info` `@ProtoAdapters` → `@ProtoAdapted` on `J` → built-in WKT/scalar mapping.
   - Resolves `@ProtoOneof.Case` solely from member annotations (`sealed`/`permits` is optional syntax sugar only), enforcing non-overlapping case types and throwing `ProtoException` on unmatched runtime values.
   - Binds immutable construction paths in order: `@ProtoCreator` → `@ProtoBuilder` → `builder()`/`build()` → public all-args constructor/factory. Without `-parameters`, positional binding is allowed only when parameter-to-field type assignment is unambiguous (rejecting silent cross-position swaps).

### Code Generation (`protovia-processor/.../gen`)

- **`CodecGenerator`** orchestrates `SizeEmitter`, `WriteEmitter`, `ReadEmitter` (delegating to `PojoReadEmitter`, `ImmutableReadEmitter`, `FieldReadEmitter`, `ReadMode`), `HelperEmitter`, and `WireCodegen`.
- **Wide-DTO method splitting**:
  - To keep every generated method below HotSpot's `HugeMethodLimit` (8,000 bytecode bytes so C2 JIT always compiles it), `WireCodegen` computes a bytecode-weighted unit count per message (`SPLIT_THRESHOLD = 80`, `CHUNK_SIZE = 40`):
    - Weight `1`: scalars, enums, messages, plain maps, non-packable non-message lists, non-enum oneof cases
    - Weight `2`: non-packable arrays (`String[]`, `byte[][]`, `Msg[]`), `List<Message>`, `@ProtoDeterministic` maps, enum oneof cases
    - Weight `3`: packable repeated fields (alignment checks + hoisted `instanceof` + dual loops)
    - Weight `4`: packable enum arrays (`Status[]`)
  - Normal messages (`<= 80` units) emit flat unsplit methods with zero helper-state allocation and full POJO `_col_<n>` caching.
  - Wide messages (`> 80` units) split into private static chunk helpers (`_computeSize_<k>`, `_writeTo_<k>`, `_readChunk_<k>`, `_collectExcludedEnums_<k>`, `_finalizeArrays_<k>`, `_fillAbsent_<k>`, `_initState_<k>`), backed by private `_ArrayState` (wide POJOs with arrays) or `_ReadState` (wide records/immutables).
- **Namespace isolation**:
  - All user field/component/`@ProtoUnknown` locals are prefixed with `f_` (`Names.safeLocal`).
  - All generated internal locals and helpers use non-`f_` names (with `_`-prefixed chunk helpers and derived temporaries).
  - Per-field helpers are keyed by field number (`packedSizeOf_<n>`, `sizeOfEntry_<n>`, `writeEntry_<n>`, `readEntry_<n>`); enum lookup helpers use injective FQN sanitization (`sanitizeFqn`).

### `.proto` Export (`protovia-processor/.../proto`)

- `ProtoPrinter` + `ProtoFileWriter` emit one `.proto` per `@ProtoMessage` / `@ProtoEnum` under `SOURCE_OUTPUT/proto/` using `lower_snake_case` filenames (`example.v1.FlightOffer` → `example/v1/flight_offer.proto`), rejecting path collisions in the same compilation.
- Optional `-Aprotovia.protoOut=<dir>` mirrors the tree for external tooling (`buf`, Go, Python). Files are not written to `CLASS_OUTPUT` and do not enter application jars.
- Emits valid proto3 accepted directly by `protoc`: enum `0` value first, `UPPER_SNAKE_CASE` prefixed enum constants, leading `.` on WKT and cross-package references, flattened oneof wrappers, adapted fields as their scalar wire types, `@ProtoReserved` declarations, and no redundant `optional` on message fields.

---

## 5. Design Boundaries & Intentional Non-Goals

These constraints are locked architectural decisions:

- **Java stays the schema**: no `.proto`-to-Java import inside Protovia.
- **Explicit field numbers only**: field numbers are never auto-assigned.
- **Zero third-party runtime dependencies & zero `Unsafe`**: `protovia-core` runs on pure Java 21 standard library APIs.
- **Isolating APT**: no global registry generation and no whole-package `.proto` aggregation during `javac`.
- **No runtime schema reflection / `ProtoCodec.schema()`**: external tooling consumes the emitted `.proto` files; breaking-change checks belong to `buf breaking` / CI.
- **Proto3 scope**: no proto2 `required`/custom defaults, no extensions, no group generation (wire groups are skipped or preserved in `UnknownFields`).
