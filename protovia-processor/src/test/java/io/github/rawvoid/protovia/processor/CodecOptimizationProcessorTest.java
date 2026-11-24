/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time codegen and bytecode verification for Commit 10 optimizations.
 *
 * @author Rawvoid
 */
class CodecOptimizationProcessorTest {

    private static final int HOTSPOT_HUGE_METHOD_LIMIT = 8000;

    @Test
    void oneByteTagFastWriteForLowFieldsAndMapInnerTags() throws Exception {
        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.OneofBase", """
                    package demo;
                    public sealed interface OneofBase permits LowCase, HighCase {}
                    """),
                src("demo.LowCase", """
                    package demo;
                    public record LowCase(String s) implements OneofBase {}
                    """),
                src("demo.HighCase", """
                    package demo;
                    public record HighCase(int v) implements OneofBase {}
                    """),
                src("demo.Tagged", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import io.github.rawvoid.protovia.annotation.ProtoOneof;
                    import java.util.List;
                    import java.util.Map;
                    @ProtoMessage
                    public class Tagged {
                      @ProtoField(number = 1) public int f1;
                      @ProtoField(number = 14) public List<Integer> packedLow;
                      @ProtoField(number = 15) public Map<String, Integer> mapLow;
                      @ProtoField(number = 16) public int f16;
                      @ProtoField(number = 17) public List<Integer> packedHigh;
                      @ProtoField(number = 18) public Map<String, Integer> mapHigh;
                      @ProtoOneof({
                        @ProtoOneof.Case(number = 13, of = LowCase.class),
                        @ProtoOneof.Case(number = 20, of = HighCase.class)
                      })
                      public OneofBase choice;
                    }
                    """));
        assertThat(compilation).succeededWithoutWarnings();
        String code = generated(compilation, "demo.internal.TaggedProtoCodec");
        assertTrue(code.contains("writer.writeRawByte(TAG_1);"));
        assertTrue(code.contains("writer.writeRawByte(TAG_13);"));
        assertTrue(code.contains("writer.writeRawByte(TAG_14_PACKED);"));
        assertTrue(code.contains("writer.writeRawByte(TAG_15);"));
        assertTrue(code.contains("writer.writeUInt32NoTag(TAG_16);"));
        assertTrue(code.contains("writer.writeUInt32NoTag(TAG_17_PACKED);"));
        assertTrue(code.contains("writer.writeUInt32NoTag(TAG_18);"));
        assertTrue(code.contains("writer.writeUInt32NoTag(TAG_20);"));
        // Map inner tags 1 (string -> 10) and 2 (int32 -> 16) must use writeRawByte.
        assertTrue(code.contains("writer.writeRawByte(10);"));
        assertTrue(code.contains("writer.writeRawByte(16);"));
        assertFalse(code.contains("writer.writeUInt32NoTag(10)"));
        assertFalse(code.contains("writer.writeUInt32NoTag(16)"));
    }

    @Test
    void flatMessagesSkipSizeCacheWhileStructuredMessagesKeepIt() throws Exception {
        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Color", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoEnum;
                    import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                    @ProtoEnum
                    public enum Color {
                      @ProtoEnumValue(0) UNKNOWN,
                      @ProtoEnumValue(1) RED
                    }
                    """),
                src("demo.ScalarChoice", """
                    package demo;
                    public sealed interface ScalarChoice permits TextCase, EmptyCase {}
                    """),
                src("demo.TextCase", """
                    package demo;
                    public record TextCase(String text) implements ScalarChoice {}
                    """),
                src("demo.EmptyCase", """
                    package demo;
                    public record EmptyCase() implements ScalarChoice {}
                    """),
                src("demo.FlatMsg", """
                    package demo;
                    import io.github.rawvoid.protovia.UnknownFields;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import io.github.rawvoid.protovia.annotation.ProtoOneof;
                    import io.github.rawvoid.protovia.annotation.ProtoUnknown;
                    import java.util.List;
                    @ProtoMessage
                    public class FlatMsg {
                      @ProtoField(number = 1) public int id;
                      @ProtoField(number = 2) public String name;
                      @ProtoField(number = 3) public byte[] raw;
                      @ProtoField(number = 4) public Color color;
                      @ProtoField(number = 5) public List<String> tags;
                      @ProtoField(number = 6, packed = false) public List<Integer> unpackedNums;
                      @ProtoOneof({
                        @ProtoOneof.Case(number = 7, of = TextCase.class),
                        @ProtoOneof.Case(number = 8, of = EmptyCase.class)
                      })
                      public ScalarChoice choice;
                      @ProtoUnknown public UnknownFields unknown;
                    }
                    """),
                src("demo.HasNested", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    @ProtoMessage
                    public class HasNested {
                      @ProtoField(number = 1) public FlatMsg nested;
                    }
                    """),
                src("demo.HasMap", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import java.util.Map;
                    @ProtoMessage
                    public class HasMap {
                      @ProtoField(number = 1) public Map<String, Integer> scores;
                    }
                    """),
                src("demo.HasPacked", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import java.util.List;
                    @ProtoMessage
                    public class HasPacked {
                      @ProtoField(number = 1) public List<Integer> nums;
                    }
                    """),
                src("demo.MsgCase", """
                    package demo;
                    public record MsgCase(FlatMsg msg) {}
                    """),
                src("demo.HasMsgOneof", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import io.github.rawvoid.protovia.annotation.ProtoOneof;
                    @ProtoMessage
                    public class HasMsgOneof {
                      @ProtoOneof({ @ProtoOneof.Case(number = 1, of = MsgCase.class) })
                      public MsgCase choice;
                    }
                    """));
        assertThat(compilation).succeededWithoutWarnings();

        String flat = generated(compilation, "demo.internal.FlatMsgProtoCodec");
        assertTrue(flat.contains("public boolean needsSizeCache()"));
        assertTrue(flat.contains("return false;"));
        assertTrue(flat.contains("public boolean cachesNestedSizes()"));

        assertFalse(generated(compilation, "demo.internal.HasNestedProtoCodec").contains("needsSizeCache()"));
        assertFalse(generated(compilation, "demo.internal.HasMapProtoCodec").contains("needsSizeCache()"));
        assertFalse(generated(compilation, "demo.internal.HasPackedProtoCodec").contains("needsSizeCache()"));
        assertFalse(generated(compilation, "demo.internal.HasMsgOneofProtoCodec").contains("needsSizeCache()"));
    }

    @Test
    void packedDecodeChecksFixedAlignmentSkipsVarintPreallocAndHoistsInstanceof() throws Exception {
        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(src("demo.PackedHolder", """
                package demo;
                import io.github.rawvoid.protovia.ProtoType;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.collect.DoubleArrayList;
                import io.github.rawvoid.protovia.collect.IntArrayList;
                import java.util.List;
                @ProtoMessage
                public class PackedHolder {
                  @ProtoField(number = 1) public List<Double> doubles;
                  @ProtoField(number = 2, type = ProtoType.FIXED32) public List<Integer> fixed32s;
                  @ProtoField(number = 3) public List<Long> varintLongs;
                  @ProtoField(number = 4) public IntArrayList concreteInts;
                  @ProtoField(number = 5) public DoubleArrayList concreteDoubles;
                  @ProtoField(number = 6) public double[] doubleArray;
                  @ProtoField(number = 7) public List<Boolean> bools;
                }
                """));
        assertThat(compilation).succeededWithoutWarnings();
        String code = generated(compilation, "demo.internal.PackedHolderProtoCodec");
        assertTrue(code.contains("(_rem & 7) != 0"));
        assertTrue(code.contains("(_rem & 3) != 0"));
        assertTrue(code.contains("ProtoLists.ensureDoubleCapacity(_prim, _rem >>> 3)"));
        assertTrue(code.contains("ProtoLists.ensureIntCapacity(_prim, _rem >>> 2)"));
        assertTrue(code.contains("ProtoLists.ensureBooleanCapacity(_prim, reader.remaining())"));
        assertFalse(code.contains("ProtoLists.ensureLongCapacity"));
        assertTrue(code.contains("if (_col_1 instanceof DoubleArrayList _prim)"));
        assertTrue(code.contains("if (_col_3 instanceof LongArrayList _prim)"));
        // Concrete IntArrayList and DoubleArrayList must NOT emit unconditional instanceof.
        assertFalse(code.contains("_col_4 instanceof IntArrayList"));
        assertFalse(code.contains("_col_5 instanceof DoubleArrayList"));
        assertTrue(code.contains("_col_4.addInt(reader.readInt32())"));
        assertTrue(code.contains("_col_5.addDouble(reader.readDouble())"));
    }

    @Test
    void deterministicMapCachesSortedArrayAndAdaptedKeysWithoutUncheckedWarnings() throws Exception {
        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.KeyAdapter", """
                    package demo;
                    import io.github.rawvoid.protovia.ProtoType;
                    import io.github.rawvoid.protovia.annotation.ProtoScalar;
                    import io.github.rawvoid.protovia.codec.ProtoAdapter;
                    import java.util.UUID;
                    @ProtoScalar(ProtoType.STRING)
                    public final class KeyAdapter implements ProtoAdapter<UUID, String> {
                      public static final KeyAdapter INSTANCE = new KeyAdapter();
                      public String toWire(UUID value) { return value.toString(); }
                      public UUID fromWire(String wire) { return UUID.fromString(wire); }
                    }
                    """),
                src("demo.DetMaps", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoAdapters;
                    import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import java.util.Map;
                    import java.util.UUID;
                    @ProtoMessage
                    @ProtoAdapters(KeyAdapter.class)
                    public class DetMaps {
                      @ProtoField(number = 1) @ProtoDeterministic public Map<String, Integer> plain;
                      @ProtoField(number = 2) @ProtoDeterministic public Map<UUID, Integer> adapted;
                    }
                    """));
        assertThat(compilation).succeededWithoutWarnings();
        String code = generated(compilation, "demo.internal.DetMapsProtoCodec");
        assertTrue(code.contains("ProtoMaps.sortedEntriesArray(f_plain, Comparator.naturalOrder())"));
        assertTrue(code.contains("cache.pushObject(_sorted_1)"));
        assertTrue(code.contains("writer.hasCachedObject() ? writer.takeObject() : ProtoMaps.sortedEntriesArray("));
        assertTrue(code.contains("ProtoMaps.sortedAdaptedEntries(f_adapted, _k -> KeyAdapter.INSTANCE.toWire(_k), Comparator.naturalOrder())"));
        assertTrue(code.contains("cache.pushObject(_sorted_2)"));
        assertTrue(code.contains("_e.wireKey(), _e.value()"));
        // sizeOfEntry_2 and writeEntry_2 accept pre-adapted String k directly.
        assertTrue(code.contains("private static int sizeOfEntry_2(String k, Integer v, SizeCache cache)"));
        assertTrue(code.contains("private static void writeEntry_2(ProtoWriter writer, String k, Integer v)"));
    }

    @Test
    void widePojo160FieldsSplitsMethodsBelowHotSpotHugeMethodLimit() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("package demo;\n");
        sb.append("import io.github.rawvoid.protovia.ProtoType;\n");
        sb.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoDeterministic;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Optional;\n");
        sb.append("@ProtoMessage\n");
        sb.append("public class WidePojo {\n");
        for (int i = 1; i <= 160; i++) {
            switch (i % 10) {
                case 0 -> sb.append("  @ProtoField(number = ").append(i).append(") public List<Integer> list").append(i).append(";\n");
                case 1 -> sb.append("  @ProtoField(number = ").append(i).append(") public String str").append(i).append(";\n");
                case 2 -> sb.append("  @ProtoField(number = ").append(i).append(") public int num").append(i).append(";\n");
                case 3 -> sb.append("  @ProtoField(number = ").append(i).append(") public long lng").append(i).append(";\n");
                case 4 -> sb.append("  @ProtoField(number = ").append(i).append(") public Optional<String> opt").append(i).append(";\n");
                case 5 -> sb.append("  @ProtoField(number = ").append(i).append(") public Status st").append(i).append(";\n");
                case 6 -> sb.append("  @ProtoField(number = ").append(i).append(") public List<Double> dbl").append(i).append(";\n");
                case 7 -> sb.append("  @ProtoField(number = ").append(i).append(") @ProtoDeterministic public Map<String, Integer> map").append(i).append(";\n");
                case 8 -> sb.append("  @ProtoField(number = ").append(i).append(") public int[] arr").append(i).append(";\n");
                default -> sb.append("  @ProtoField(number = ").append(i).append(", type = ProtoType.FIXED32) public int fix").append(i).append(";\n");
            }
        }
        sb.append("  @ProtoUnknown public UnknownFields unknown;\n");
        sb.append("}\n");

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Status", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoEnum;
                    import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                    import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;
                    @ProtoEnum
                    public enum Status {
                      @ProtoEnumValue(0) UNKNOWN,
                      @ProtoEnumValue(1) ACTIVE,
                      @ProtoUnrecognized UNRECOGNIZED
                    }
                    """),
                src("demo.WidePojo", sb.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String source = generated(compilation, "demo.internal.WidePojoProtoCodec");
        assertTrue(source.contains("_computeSize_0("));
        assertTrue(source.contains("_writeTo_0("));
        assertTrue(source.contains("_readChunk_0("));
        assertTrue(source.contains("_unknownSize("));
        assertTrue(source.contains("_writeUnknown("));
        assertTrue(source.contains("_collectExcludedEnums("));

        assertAllMethodsUnderLimit(compilation, "demo/internal/WidePojoProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/WidePojoProtoCodec$_ArrayState.class");
    }

    @Test
    void wideRecord160FieldsSplitsMethodsBelowHotSpotHugeMethodLimit() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("package demo;\n");
        sb.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Optional;\n");
        sb.append("@ProtoMessage\n");
        sb.append("public record WideRecord(\n");
        for (int i = 1; i <= 160; i++) {
            switch (i % 8) {
                case 0 -> sb.append("  @ProtoField(number = ").append(i).append(") List<Integer> list").append(i).append(",\n");
                case 1 -> sb.append("  @ProtoField(number = ").append(i).append(") String str").append(i).append(",\n");
                case 2 -> sb.append("  @ProtoField(number = ").append(i).append(") int num").append(i).append(",\n");
                case 3 -> sb.append("  @ProtoField(number = ").append(i).append(") boolean flag").append(i).append(",\n");
                case 4 -> sb.append("  @ProtoField(number = ").append(i).append(") Optional<String> opt").append(i).append(",\n");
                case 5 -> sb.append("  @ProtoField(number = ").append(i).append(") Status st").append(i).append(",\n");
                case 6 -> sb.append("  @ProtoField(number = ").append(i).append(") Map<String, Integer> map").append(i).append(",\n");
                default -> sb.append("  @ProtoField(number = ").append(i).append(") int[] arr").append(i).append(",\n");
            }
        }
        sb.append("  @ProtoUnknown UnknownFields unknown,\n");
        sb.append("  String transientNote) {}\n");

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Status", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoEnum;
                    import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                    @ProtoEnum
                    public enum Status {
                      @ProtoEnumValue(0) UNKNOWN,
                      @ProtoEnumValue(1) ACTIVE
                    }
                    """),
                src("demo.WideRecord", sb.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String source = generated(compilation, "demo.internal.WideRecordProtoCodec");
        assertTrue(source.contains("private static final class _ReadState"));
        assertTrue(source.contains("_initState_0("));
        assertTrue(source.contains("_readChunk_0("));
        assertTrue(source.contains("_finalizeArrays_0("));
        assertTrue(source.contains("_computeSize_0("));
        assertTrue(source.contains("_writeTo_0("));

        assertAllMethodsUnderLimit(compilation, "demo/internal/WideRecordProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/WideRecordProtoCodec$_ReadState.class");
    }

    @Test
    void wideGetterSetterPojoAndBuilderImmutable160FieldsStayBelowHugeMethodLimit() throws Exception {
        StringBuilder pojo = new StringBuilder();
        pojo.append("package demo;\n");
        pojo.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        pojo.append("import java.util.List;\n");
        pojo.append("import java.util.Map;\n");
        pojo.append("@ProtoMessage\n");
        pojo.append("public class WideAccessorPojo {\n");
        for (int i = 1; i <= 160; i++) {
            String type = switch (i % 4) {
                case 0 -> "Status";
                case 1 -> "List<Integer>";
                case 2 -> "Map<String, Integer>";
                default -> "String";
            };
            pojo.append("  @ProtoField(number = ").append(i).append(") private ").append(type).append(" f").append(i).append(";\n");
            pojo.append("  public ").append(type).append(" getF").append(i).append("() { return f").append(i).append("; }\n");
            pojo.append("  public void setF").append(i).append("(").append(type).append(" v) { this.f").append(i).append(" = v; }\n");
        }
        pojo.append("  @ProtoUnknown private UnknownFields unknown;\n");
        pojo.append("  public UnknownFields getUnknown() { return unknown; }\n");
        pojo.append("  public void setUnknown(UnknownFields unknown) { this.unknown = unknown; }\n");
        pojo.append("}\n");

        StringBuilder builderMsg = new StringBuilder();
        builderMsg.append("package demo;\n");
        builderMsg.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        builderMsg.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        builderMsg.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        builderMsg.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        builderMsg.append("import java.util.List;\n");
        builderMsg.append("@ProtoMessage\n");
        builderMsg.append("public final class WideBuilderMsg {\n");
        for (int i = 1; i <= 160; i++) {
            String type = (i % 5 == 0) ? "List<Integer>" : "int";
            builderMsg.append("  @ProtoField(number = ").append(i).append(") private final ").append(type).append(" f").append(i).append(";\n");
            builderMsg.append("  public ").append(type).append(" getF").append(i).append("() { return f").append(i).append("; }\n");
        }
        builderMsg.append("  @ProtoUnknown private final UnknownFields unknown;\n");
        builderMsg.append("  public UnknownFields getUnknown() { return unknown; }\n");
        builderMsg.append("  private WideBuilderMsg(Builder b) {\n");
        for (int i = 1; i <= 160; i++) {
            builderMsg.append("    this.f").append(i).append(" = b.f").append(i).append(";\n");
        }
        builderMsg.append("    this.unknown = b.unknown;\n");
        builderMsg.append("  }\n");
        builderMsg.append("  public static Builder builder() { return new Builder(); }\n");
        builderMsg.append("  public static final class Builder {\n");
        for (int i = 1; i <= 160; i++) {
            String type = (i % 5 == 0) ? "List<Integer>" : "int";
            builderMsg.append("    private ").append(type).append(" f").append(i).append(";\n");
            builderMsg.append("    public Builder f").append(i).append("(").append(type).append(" v) { this.f").append(i).append(" = v; return this; }\n");
        }
        builderMsg.append("    private UnknownFields unknown;\n");
        builderMsg.append("    public Builder unknown(UnknownFields v) { this.unknown = v; return this; }\n");
        builderMsg.append("    public WideBuilderMsg build() { return new WideBuilderMsg(this); }\n");
        builderMsg.append("  }\n");
        builderMsg.append("}\n");

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Status", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoEnum;
                    import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                    @ProtoEnum
                    public enum Status {
                      @ProtoEnumValue(0) UNKNOWN,
                      @ProtoEnumValue(1) ACTIVE
                    }
                    """),
                src("demo.WideAccessorPojo", pojo.toString()),
                src("demo.WideBuilderMsg", builderMsg.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        // 40 enum fields -> _collectExcludedEnums splits into _collectExcludedEnums_0 / _1.
        String accessorSource = generated(compilation, "demo.internal.WideAccessorPojoProtoCodec");
        assertTrue(accessorSource.contains("_collectExcludedEnums_0("));
        assertTrue(accessorSource.contains("_fillAbsent_0("));

        assertAllMethodsUnderLimit(compilation, "demo/internal/WideAccessorPojoProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/WideBuilderMsgProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/WideBuilderMsgProtoCodec$_ReadState.class");
    }

    private static void assertAllMethodsUnderLimit(Compilation compilation, String classPath) throws IOException {
        JavaFileObject classFile = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, classPath)
            .orElseThrow(() -> new AssertionError("missing generated class file: " + classPath));
        byte[] bytes;
        try (InputStream in = classFile.openInputStream()) {
            bytes = in.readAllBytes();
        }
        Map<String, Integer> codeLengths = parseMethodCodeLengths(bytes);
        assertFalse(codeLengths.isEmpty(), "expected methods in " + classPath);
        for (Map.Entry<String, Integer> entry : codeLengths.entrySet()) {
            assertTrue(entry.getValue() < HOTSPOT_HUGE_METHOD_LIMIT,
                () -> classPath + "#" + entry.getKey() + " bytecode length "
                    + entry.getValue() + " >= " + HOTSPOT_HUGE_METHOD_LIMIT);
        }
    }

    /**
     * Minimal JVMS §4.1 class-file parser returning each method's {@code Code} attribute {@code code_length}.
     */
    private static Map<String, Integer> parseMethodCodeLengths(byte[] classBytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes));
        int magic = in.readInt();
        if (magic != 0xCAFEBABE) {
            throw new IllegalArgumentException("not a class file");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int cpCount = in.readUnsignedShort();
        String[] utf8 = new String[cpCount];
        for (int i = 1; i < cpCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    int len = in.readUnsignedShort();
                    byte[] data = in.readNBytes(len);
                    utf8[i] = new String(data, StandardCharsets.UTF_8);
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 5, 6 -> {
                    in.skipNBytes(8);
                    i++;
                }
                case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
                case 15 -> in.skipNBytes(3);
                default -> throw new IllegalStateException("unknown constant pool tag: " + tag);
            }
        }
        in.skipNBytes(6); // access_flags, this_class, super_class
        int interfacesCount = in.readUnsignedShort();
        in.skipNBytes(interfacesCount * 2L);
        int fieldsCount = in.readUnsignedShort();
        for (int i = 0; i < fieldsCount; i++) {
            in.skipNBytes(6);
            skipAttributes(in);
        }
        Map<String, Integer> lengths = new LinkedHashMap<>();
        int methodsCount = in.readUnsignedShort();
        for (int i = 0; i < methodsCount; i++) {
            in.readUnsignedShort(); // access_flags
            int nameIdx = in.readUnsignedShort();
            int descIdx = in.readUnsignedShort();
            int attrCount = in.readUnsignedShort();
            for (int a = 0; a < attrCount; a++) {
                int attrNameIdx = in.readUnsignedShort();
                long attrLen = Integer.toUnsignedLong(in.readInt());
                if ("Code".equals(utf8[attrNameIdx])) {
                    in.readUnsignedShort(); // max_stack
                    in.readUnsignedShort(); // max_locals
                    int codeLen = in.readInt();
                    lengths.put(utf8[nameIdx] + utf8[descIdx], codeLen);
                    in.skipNBytes(attrLen - 8L);
                } else {
                    in.skipNBytes(attrLen);
                }
            }
        }
        return lengths;
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attrCount = in.readUnsignedShort();
        for (int a = 0; a < attrCount; a++) {
            in.readUnsignedShort(); // name_index
            long attrLen = Integer.toUnsignedLong(in.readInt());
            in.skipNBytes(attrLen);
        }
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }

    private static String generated(Compilation compilation, String fqcn) throws IOException {
        return compilation.generatedSourceFile(fqcn).orElseThrow().getCharContent(false).toString();
    }
}
