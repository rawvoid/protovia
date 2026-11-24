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
                default -> i == 159 ? "Status" : "String";
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

        // 41 enum fields (> CHUNK_SIZE = 40) -> _collectExcludedEnums splits into _collectExcludedEnums_0 / _1.
        String accessorSource = generated(compilation, "demo.internal.WideAccessorPojoProtoCodec");
        assertTrue(accessorSource.contains("_collectExcludedEnums_0("));
        assertTrue(accessorSource.contains("_fillAbsent_0("));

        assertAllMethodsUnderLimit(compilation, "demo/internal/WideAccessorPojoProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/WideBuilderMsgProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/WideBuilderMsgProtoCodec$_ReadState.class");
    }

    @Test
    void mixed70FieldPojoAndRecordStayUnsplitBelowHugeMethodLimit() throws Exception {
        // 70 fields with 5 packable repeated fields: 5 * 3 + 65 * 1 = 80 units (== SPLIT_THRESHOLD, stays unsplit).
        StringBuilder pojo = new StringBuilder();
        pojo.append("package demo;\n");
        pojo.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        pojo.append("import java.util.List;\n");
        pojo.append("import java.util.Map;\n");
        pojo.append("import java.util.Optional;\n");
        pojo.append("@ProtoMessage\n");
        pojo.append("public class MediumPojo {\n");
        for (int i = 1; i <= 70; i++) {
            String type = switch (i) {
                case 1, 2 -> "List<Integer>";
                case 3 -> "List<Double>";
                case 4, 5 -> "int[]";
                default -> switch (i % 7) {
                    case 0 -> "Map<String, Integer>";
                    case 1 -> "String";
                    case 2 -> "int";
                    case 3 -> "long";
                    case 4 -> "boolean";
                    case 5 -> "Optional<String>";
                    default -> (i % 2 == 0) ? "Status" : "List<String>";
                };
            };
            pojo.append("  @ProtoField(number = ").append(i).append(") public ").append(type).append(" f").append(i).append(";\n");
        }
        pojo.append("  @ProtoUnknown public UnknownFields unknown;\n");
        pojo.append("}\n");

        StringBuilder rec = new StringBuilder();
        rec.append("package demo;\n");
        rec.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        rec.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        rec.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        rec.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        rec.append("import java.util.List;\n");
        rec.append("import java.util.Map;\n");
        rec.append("import java.util.Optional;\n");
        rec.append("@ProtoMessage\n");
        rec.append("public record MediumRecord(\n");
        for (int i = 1; i <= 70; i++) {
            String type = switch (i) {
                case 1, 2 -> "List<Integer>";
                case 3 -> "List<Double>";
                case 4, 5 -> "int[]";
                default -> switch (i % 7) {
                    case 0 -> "Map<String, Integer>";
                    case 1 -> "String";
                    case 2 -> "int";
                    case 3 -> "long";
                    case 4 -> "boolean";
                    case 5 -> "Optional<String>";
                    default -> (i % 2 == 0) ? "Status" : "List<String>";
                };
            };
            rec.append("  @ProtoField(number = ").append(i).append(") ").append(type).append(" f").append(i).append(",\n");
        }
        rec.append("  @ProtoUnknown UnknownFields unknown) {}\n");

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
                src("demo.MediumPojo", pojo.toString()),
                src("demo.MediumRecord", rec.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String pojoSrc = generated(compilation, "demo.internal.MediumPojoProtoCodec");
        assertFalse(pojoSrc.contains("_readChunk_0("));
        assertFalse(pojoSrc.contains("_computeSize_0("));
        assertFalse(pojoSrc.contains("_writeTo_0("));
        assertFalse(pojoSrc.contains("_ArrayState"));
        assertTrue(pojoSrc.contains("_col_1"));

        String recSrc = generated(compilation, "demo.internal.MediumRecordProtoCodec");
        assertFalse(recSrc.contains("_readChunk_0("));
        assertFalse(recSrc.contains("_computeSize_0("));
        assertFalse(recSrc.contains("_writeTo_0("));
        assertFalse(recSrc.contains("_ReadState"));

        assertAllMethodsUnderLimit(compilation, "demo/internal/MediumPojoProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/MediumRecordProtoCodec.class");
    }

    @Test
    void fortyPackedDoubleListsSplitAutomaticallyBelowHugeMethodLimit() throws Exception {
        // 40 packed List<Double> fields: 40 * 3 = 120 units > SPLIT_THRESHOLD (80).
        StringBuilder pojo = new StringBuilder();
        pojo.append("package demo;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        pojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        pojo.append("import java.util.List;\n");
        pojo.append("@ProtoMessage\n");
        pojo.append("public class AllPackedDoublesPojo {\n");
        for (int i = 1; i <= 40; i++) {
            pojo.append("  @ProtoField(number = ").append(i).append(") public List<Double> d").append(i).append(";\n");
        }
        pojo.append("}\n");

        StringBuilder rec = new StringBuilder();
        rec.append("package demo;\n");
        rec.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        rec.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        rec.append("import java.util.List;\n");
        rec.append("@ProtoMessage\n");
        rec.append("public record AllPackedDoublesRecord(\n");
        for (int i = 1; i <= 40; i++) {
            rec.append("  @ProtoField(number = ").append(i).append(") List<Double> d").append(i)
                .append(i < 40 ? ",\n" : ") {}\n");
        }

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.AllPackedDoublesPojo", pojo.toString()),
                src("demo.AllPackedDoublesRecord", rec.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String pojoSrc = generated(compilation, "demo.internal.AllPackedDoublesPojoProtoCodec");
        assertTrue(pojoSrc.contains("_readChunk_0("));
        assertTrue(pojoSrc.contains("_computeSize_0("));
        assertTrue(pojoSrc.contains("_writeTo_0("));

        String recSrc = generated(compilation, "demo.internal.AllPackedDoublesRecordProtoCodec");
        assertTrue(recSrc.contains("private static final class _ReadState"));
        assertTrue(recSrc.contains("_readChunk_0("));
        assertTrue(recSrc.contains("_computeSize_0("));
        assertTrue(recSrc.contains("_writeTo_0("));

        assertAllMethodsUnderLimit(compilation, "demo/internal/AllPackedDoublesPojoProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/AllPackedDoublesRecordProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/AllPackedDoublesRecordProtoCodec$_ReadState.class");
    }

    @Test
    void multiCaseEnumOneofExclusionsChunkByUnitWeight() throws Exception {
        // 21 enum oneofs (2 enum cases * 2 = 4 units each -> 84 units > CHUNK_SIZE 40, though exclFields.size() == 21 <= 40)
        // + 40 scalar ints = 124 units > SPLIT_THRESHOLD 80.
        StringBuilder sb = new StringBuilder();
        sb.append("package demo;\n");
        sb.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoOneof;\n");
        sb.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        sb.append("@ProtoMessage\n");
        sb.append("public class ManyEnumOneofs {\n");
        for (int i = 1; i <= 21; i++) {
            int n1 = i * 2 - 1;
            int n2 = i * 2;
            sb.append("  @ProtoOneof({\n");
            sb.append("    @ProtoOneof.Case(number = ").append(n1).append(", name = \"c").append(i).append("_a\", of = CaseA.class),\n");
            sb.append("    @ProtoOneof.Case(number = ").append(n2).append(", name = \"c").append(i).append("_b\", of = CaseB.class)\n");
            sb.append("  })\n");
            sb.append("  public EnumChoice c").append(i).append(";\n");
        }
        for (int i = 101; i <= 140; i++) {
            sb.append("  @ProtoField(number = ").append(i).append(") public int s").append(i).append(";\n");
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
                    @ProtoEnum
                    public enum Status {
                      @ProtoEnumValue(0) UNKNOWN,
                      @ProtoEnumValue(1) ACTIVE
                    }
                    """),
                src("demo.EnumChoice", """
                    package demo;
                    public sealed interface EnumChoice permits CaseA, CaseB {}
                    """),
                src("demo.CaseA", """
                    package demo;
                    public record CaseA(Status a) implements EnumChoice {}
                    """),
                src("demo.CaseB", """
                    package demo;
                    public record CaseB(Status b) implements EnumChoice {}
                    """),
                src("demo.ManyEnumOneofs", sb.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String source = generated(compilation, "demo.internal.ManyEnumOneofsProtoCodec");
        assertTrue(source.contains("_collectExcludedEnums_0("));
        assertTrue(source.contains("_collectExcludedEnums_1("));
        assertAllMethodsUnderLimit(compilation, "demo/internal/ManyEnumOneofsProtoCodec.class");
    }

    @Test
    void adversarialNestedMessageListsAndNonPackableArraysSplitBelowHugeMethodLimit() throws Exception {
        // 80 List<Addr> (2 units each = 160), 80 String[] (2 units each = 160), 80 Addr[] (2 units each = 160).
        StringBuilder listPojo = new StringBuilder();
        listPojo.append("package demo;\n");
        listPojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        listPojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        listPojo.append("import java.util.List;\n");
        listPojo.append("@ProtoMessage\n");
        listPojo.append("public class NestedList80Pojo {\n");
        for (int i = 1; i <= 80; i++) {
            listPojo.append("  @ProtoField(number = ").append(i).append(") public List<Addr> f").append(i).append(";\n");
        }
        listPojo.append("}\n");

        StringBuilder strArrPojo = new StringBuilder();
        strArrPojo.append("package demo;\n");
        strArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        strArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        strArrPojo.append("@ProtoMessage\n");
        strArrPojo.append("public class StringArray80Pojo {\n");
        for (int i = 1; i <= 80; i++) {
            strArrPojo.append("  @ProtoField(number = ").append(i).append(") public String[] f").append(i).append(";\n");
        }
        strArrPojo.append("}\n");

        StringBuilder addrArrPojo = new StringBuilder();
        addrArrPojo.append("package demo;\n");
        addrArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        addrArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        addrArrPojo.append("@ProtoMessage\n");
        addrArrPojo.append("public class AddrArray80Pojo {\n");
        for (int i = 1; i <= 80; i++) {
            addrArrPojo.append("  @ProtoField(number = ").append(i).append(") public Addr[] f").append(i).append(";\n");
        }
        addrArrPojo.append("}\n");

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Addr", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    @ProtoMessage
                    public class Addr {
                      @ProtoField(number = 1) public String city;
                    }
                    """),
                src("demo.NestedList80Pojo", listPojo.toString()),
                src("demo.StringArray80Pojo", strArrPojo.toString()),
                src("demo.AddrArray80Pojo", addrArrPojo.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        for (String name : new String[]{"NestedList80Pojo", "StringArray80Pojo", "AddrArray80Pojo"}) {
            String src = generated(compilation, "demo.internal." + name + "ProtoCodec");
            assertTrue(src.contains("_computeSize_0("), name + " should split computeSize");
            assertTrue(src.contains("_writeTo_0("), name + " should split writeTo");
            assertTrue(src.contains("_readChunk_0("), name + " should split read");
            assertAllMethodsUnderLimit(compilation, "demo/internal/" + name + "ProtoCodec.class");
        }
        assertAllMethodsUnderLimit(compilation, "demo/internal/StringArray80PojoProtoCodec$_ArrayState.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/AddrArray80PojoProtoCodec$_ArrayState.class");
    }

    @Test
    void adversarialDeterministicMapsSplitBelowHugeMethodLimit() throws Exception {
        // 80 @ProtoDeterministic Map<String, Addr> (2 units each = 160 > SPLIT_THRESHOLD 80).
        StringBuilder mapPojo = new StringBuilder();
        mapPojo.append("package demo;\n");
        mapPojo.append("import io.github.rawvoid.protovia.annotation.ProtoDeterministic;\n");
        mapPojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        mapPojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        mapPojo.append("import java.util.Map;\n");
        mapPojo.append("@ProtoMessage\n");
        mapPojo.append("public class DetMap80Pojo {\n");
        for (int i = 1; i <= 80; i++) {
            mapPojo.append("  @ProtoField(number = ").append(i)
                .append(") @ProtoDeterministic public Map<String, Addr> m").append(i).append(";\n");
        }
        mapPojo.append("}\n");

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Addr", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    @ProtoMessage
                    public class Addr {
                      @ProtoField(number = 1) public String city;
                    }
                    """),
                src("demo.DetMap80Pojo", mapPojo.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String src = generated(compilation, "demo.internal.DetMap80PojoProtoCodec");
        assertTrue(src.contains("_computeSize_0("));
        assertTrue(src.contains("_writeTo_0("));
        assertTrue(src.contains("_readChunk_0("));
        assertAllMethodsUnderLimit(compilation, "demo/internal/DetMap80PojoProtoCodec.class");
    }

    @Test
    void adversarialEnumOneofsAndEnumArraysSplitBelowHugeMethodLimit() throws Exception {
        // 40 2-case enum @ProtoOneof + @ProtoUnknown (40 * 4 = 160 units > SPLIT_THRESHOLD 80).
        StringBuilder oneofPojo = new StringBuilder();
        oneofPojo.append("package demo;\n");
        oneofPojo.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        oneofPojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        oneofPojo.append("import io.github.rawvoid.protovia.annotation.ProtoOneof;\n");
        oneofPojo.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        oneofPojo.append("@ProtoMessage\n");
        oneofPojo.append("public class EnumOneof40Pojo {\n");
        for (int i = 1; i <= 40; i++) {
            int n1 = i * 2 - 1;
            int n2 = i * 2;
            oneofPojo.append("  @ProtoOneof({\n");
            oneofPojo.append("    @ProtoOneof.Case(number = ").append(n1).append(", name = \"c").append(i).append("_a\", of = CaseA.class),\n");
            oneofPojo.append("    @ProtoOneof.Case(number = ").append(n2).append(", name = \"c").append(i).append("_b\", of = CaseB.class)\n");
            oneofPojo.append("  })\n");
            oneofPojo.append("  public EnumChoice c").append(i).append(";\n");
        }
        oneofPojo.append("  @ProtoUnknown public UnknownFields unknown;\n");
        oneofPojo.append("}\n");

        // 26 Status[] with @ProtoUnrecognized + @ProtoUnknown on getter/setter POJO (26 * 4 = 104 units > 80).
        StringBuilder enumArrPojo = new StringBuilder();
        enumArrPojo.append("package demo;\n");
        enumArrPojo.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        enumArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        enumArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        enumArrPojo.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        enumArrPojo.append("@ProtoMessage\n");
        enumArrPojo.append("public class EnumArray26AccessorPojo {\n");
        for (int i = 1; i <= 26; i++) {
            enumArrPojo.append("  @ProtoField(number = ").append(i).append(") private Status[] s").append(i).append(";\n");
            enumArrPojo.append("  public Status[] getS").append(i).append("() { return s").append(i).append("; }\n");
            enumArrPojo.append("  public void setS").append(i).append("(Status[] v) { this.s").append(i).append(" = v; }\n");
        }
        enumArrPojo.append("  @ProtoUnknown private UnknownFields unknown;\n");
        enumArrPojo.append("  public UnknownFields getUnknown() { return unknown; }\n");
        enumArrPojo.append("  public void setUnknown(UnknownFields unknown) { this.unknown = unknown; }\n");
        enumArrPojo.append("}\n");

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
                src("demo.EnumChoice", """
                    package demo;
                    public sealed interface EnumChoice permits CaseA, CaseB {}
                    """),
                src("demo.CaseA", """
                    package demo;
                    public record CaseA(Status a) implements EnumChoice {}
                    """),
                src("demo.CaseB", """
                    package demo;
                    public record CaseB(Status b) implements EnumChoice {}
                    """),
                src("demo.EnumOneof40Pojo", oneofPojo.toString()),
                src("demo.EnumArray26AccessorPojo", enumArrPojo.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        String oneofSrc = generated(compilation, "demo.internal.EnumOneof40PojoProtoCodec");
        assertTrue(oneofSrc.contains("_computeSize_0("));
        assertTrue(oneofSrc.contains("_writeTo_0("));
        assertTrue(oneofSrc.contains("_readChunk_0("));
        assertTrue(oneofSrc.contains("_collectExcludedEnums_0("));
        assertAllMethodsUnderLimit(compilation, "demo/internal/EnumOneof40PojoProtoCodec.class");

        String enumArrSrc = generated(compilation, "demo.internal.EnumArray26AccessorPojoProtoCodec");
        assertTrue(enumArrSrc.contains("_computeSize_0("));
        assertTrue(enumArrSrc.contains("_writeTo_0("));
        assertTrue(enumArrSrc.contains("_readChunk_0("));
        assertAllMethodsUnderLimit(compilation, "demo/internal/EnumArray26AccessorPojoProtoCodec.class");
        assertAllMethodsUnderLimit(compilation, "demo/internal/EnumArray26AccessorPojoProtoCodec$_ArrayState.class");
    }

    @Test
    void heavyShapesAtExact80UnitBoundaryStayUnsplitBelowHugeMethodLimit() throws Exception {
        // Exact SPLIT_THRESHOLD = 80 units unsplit for each heavy category:
        // 40 List<Addr> (40 * 2 = 80), 40 Addr[] (40 * 2 = 80), 40 @ProtoDeterministic Map<String, Addr> (40 * 2 = 80),
        // 20 2-case enum @ProtoOneof + @ProtoUnknown (20 * 4 = 80), 20 Status[] getter/setter + @ProtoUnknown (20 * 4 = 80).
        StringBuilder list40 = new StringBuilder();
        list40.append("package demo;\n");
        list40.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        list40.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        list40.append("import java.util.List;\n");
        list40.append("@ProtoMessage\npublic class List40Boundary {\n");
        for (int i = 1; i <= 40; i++) {
            list40.append("  @ProtoField(number = ").append(i).append(") public List<Addr> f").append(i).append(";\n");
        }
        list40.append("}\n");

        StringBuilder arr40 = new StringBuilder();
        arr40.append("package demo;\n");
        arr40.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        arr40.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        arr40.append("@ProtoMessage\npublic class AddrArr40Boundary {\n");
        for (int i = 1; i <= 40; i++) {
            arr40.append("  @ProtoField(number = ").append(i).append(") public Addr[] f").append(i).append(";\n");
        }
        arr40.append("}\n");

        StringBuilder map40 = new StringBuilder();
        map40.append("package demo;\n");
        map40.append("import io.github.rawvoid.protovia.annotation.ProtoDeterministic;\n");
        map40.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        map40.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        map40.append("import java.util.Map;\n");
        map40.append("@ProtoMessage\npublic class DetMap40Boundary {\n");
        for (int i = 1; i <= 40; i++) {
            map40.append("  @ProtoField(number = ").append(i)
                .append(") @ProtoDeterministic public Map<String, Addr> m").append(i).append(";\n");
        }
        map40.append("}\n");

        StringBuilder oneof20 = new StringBuilder();
        oneof20.append("package demo;\n");
        oneof20.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        oneof20.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        oneof20.append("import io.github.rawvoid.protovia.annotation.ProtoOneof;\n");
        oneof20.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        oneof20.append("@ProtoMessage\npublic class EnumOneof20Boundary {\n");
        for (int i = 1; i <= 20; i++) {
            int n1 = i * 2 - 1;
            int n2 = i * 2;
            oneof20.append("  @ProtoOneof({\n");
            oneof20.append("    @ProtoOneof.Case(number = ").append(n1).append(", name = \"c").append(i).append("_a\", of = CaseA.class),\n");
            oneof20.append("    @ProtoOneof.Case(number = ").append(n2).append(", name = \"c").append(i).append("_b\", of = CaseB.class)\n");
            oneof20.append("  })\n");
            oneof20.append("  public EnumChoice c").append(i).append(";\n");
        }
        oneof20.append("  @ProtoUnknown public UnknownFields unknown;\n");
        oneof20.append("}\n");

        StringBuilder enumArr20 = new StringBuilder();
        enumArr20.append("package demo;\n");
        enumArr20.append("import io.github.rawvoid.protovia.UnknownFields;\n");
        enumArr20.append("import io.github.rawvoid.protovia.annotation.ProtoField;\n");
        enumArr20.append("import io.github.rawvoid.protovia.annotation.ProtoMessage;\n");
        enumArr20.append("import io.github.rawvoid.protovia.annotation.ProtoUnknown;\n");
        enumArr20.append("@ProtoMessage\npublic class EnumArr20Boundary {\n");
        for (int i = 1; i <= 20; i++) {
            enumArr20.append("  @ProtoField(number = ").append(i).append(") private Status[] s").append(i).append(";\n");
            enumArr20.append("  public Status[] getS").append(i).append("() { return s").append(i).append("; }\n");
            enumArr20.append("  public void setS").append(i).append("(Status[] v) { this.s").append(i).append(" = v; }\n");
        }
        enumArr20.append("  @ProtoUnknown private UnknownFields unknown;\n");
        enumArr20.append("  public UnknownFields getUnknown() { return unknown; }\n");
        enumArr20.append("  public void setUnknown(UnknownFields unknown) { this.unknown = unknown; }\n");
        enumArr20.append("}\n");

        Compilation compilation = javac()
            .withOptions("--release", "21", "-Xlint:unchecked", "-Werror")
            .withProcessors(new ProtoviaProcessor())
            .compile(
                src("demo.Addr", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    @ProtoMessage
                    public class Addr {
                      @ProtoField(number = 1) public String city;
                    }
                    """),
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
                src("demo.EnumChoice", """
                    package demo;
                    public sealed interface EnumChoice permits CaseA, CaseB {}
                    """),
                src("demo.CaseA", """
                    package demo;
                    public record CaseA(Status a) implements EnumChoice {}
                    """),
                src("demo.CaseB", """
                    package demo;
                    public record CaseB(Status b) implements EnumChoice {}
                    """),
                src("demo.List40Boundary", list40.toString()),
                src("demo.AddrArr40Boundary", arr40.toString()),
                src("demo.DetMap40Boundary", map40.toString()),
                src("demo.EnumOneof20Boundary", oneof20.toString()),
                src("demo.EnumArr20Boundary", enumArr20.toString()));
        assertThat(compilation).succeededWithoutWarnings();

        for (String name : new String[]{
            "List40Boundary", "AddrArr40Boundary", "DetMap40Boundary", "EnumOneof20Boundary", "EnumArr20Boundary"}) {
            String src = generated(compilation, "demo.internal." + name + "ProtoCodec");
            assertFalse(src.contains("_computeSize_0("), name + " should stay unsplit at exact 80 units");
            assertFalse(src.contains("_writeTo_0("), name + " should stay unsplit at exact 80 units");
            assertFalse(src.contains("_readChunk_0("), name + " should stay unsplit at exact 80 units");
            assertAllMethodsUnderLimit(compilation, "demo/internal/" + name + "ProtoCodec.class");
        }
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
