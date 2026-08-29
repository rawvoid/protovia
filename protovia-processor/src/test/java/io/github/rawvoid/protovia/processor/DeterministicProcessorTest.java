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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * @author Rawvoid
 */
class DeterministicProcessorTest {

    @Test
    void unmarkedMapKeepsIterationOrder() {
        Compilation compilation = compile(src("demo.Holder", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import java.util.Map;
            @ProtoMessage
            public class Holder {
              @ProtoField(number = 1) public Map<String, Integer> scores;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("ProtoMaps");
    }

    @Test
    void fieldAnnotationSortsOnlyThatMap() {
        Compilation compilation = compile(src("demo.Holder", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import java.util.Map;
            @ProtoMessage
            public class Holder {
              @ProtoField(number = 1) @ProtoDeterministic public Map<String, Integer> scores;
              @ProtoField(number = 2) public Map<String, Integer> extra;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries(scores, Comparator.naturalOrder())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("extra.entrySet()");
    }

    @Test
    void typeAnnotationSortsAllMapsUnlessFieldOptsOut() {
        Compilation compilation = compile(src("demo.Holder", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import java.util.Map;
            @ProtoMessage
            @ProtoDeterministic
            public class Holder {
              @ProtoField(number = 1) public Map<String, Integer> headers;
              @ProtoField(number = 2) @ProtoDeterministic(false) public Map<String, Integer> blobs;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries(headers, Comparator.naturalOrder())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("blobs.entrySet()");
    }

    @Test
    void mixinAnnotationAppliesToInheritedAndLeafMaps() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import java.util.Map;
                @ProtoDeterministic
                public class Base {
                  @ProtoField(number = 1) public Map<String, Integer> inherited;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public Map<String, Integer> own;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries(inherited, Comparator.naturalOrder())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries(own, Comparator.naturalOrder())");
    }

    @Test
    void leafFalseOverridesMixin() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import java.util.Map;
                @ProtoDeterministic
                public class Base {
                  @ProtoField(number = 1) public Map<String, Integer> inherited;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                @ProtoMessage
                @ProtoDeterministic(false)
                public class User extends Base {
                  @ProtoField(number = 16) public Map<String, Integer> own;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("ProtoMaps");
    }

    @Test
    void packageInfoAppliesWhenNothingCloserIsSet() {
        Compilation compilation = compile(
            src("demo.package-info", """
                @ProtoDeterministic
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                """),
            src("demo.Holder", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                @ProtoMessage
                public class Holder {
                  @ProtoField(number = 1) public Map<String, Integer> scores;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries");
    }

    @Test
    void fieldFalseBeatsPackageInfo() {
        Compilation compilation = compile(
            src("demo.package-info", """
                @ProtoDeterministic
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                """),
            src("demo.Holder", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                @ProtoMessage
                public class Holder {
                  @ProtoField(number = 1) @ProtoDeterministic(false) public Map<String, Integer> scores;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("ProtoMaps");
    }

    @Test
    void nestedMessageIsResolvedIndependently() {
        Compilation compilation = compile(
            src("demo.Inner", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                @ProtoMessage
                public class Inner {
                  @ProtoField(number = 1) public Map<String, Integer> items;
                }
                """),
            src("demo.Outer", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                @ProtoMessage
                @ProtoDeterministic
                public class Outer {
                  @ProtoField(number = 1) public Map<String, Integer> items;
                  @ProtoField(number = 2) public Inner inner;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OuterProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.InnerProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("ProtoMaps");
    }

    @Test
    void adapterKeySortsByWireValue() {
        Compilation compilation = compile(
            uuidAdapter(),
            src("demo.Holder", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoAdapters;
                import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.Map;
                import java.util.UUID;
                @ProtoMessage
                @ProtoAdapters(UuidString.class)
                public class Holder {
                  @ProtoField(number = 1) @ProtoDeterministic public Map<UUID, Integer> ids;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("UuidString.INSTANCE.toWire(k)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoMaps.sortedEntries");
    }

    @Test
    void uint64UsesUnsignedOrder() {
        Compilation compilation = compile(src("demo.Holder", """
            package demo;
            import io.github.rawvoid.protovia.ProtoType;
            import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import java.util.Map;
            @ProtoMessage
            public class Holder {
              @ProtoField(number = 1, keyType = ProtoType.UINT64)
              @ProtoDeterministic
              public Map<Long, String> values;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("Long::compareUnsigned");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("sortedEntries(values, Long::compareUnsigned)");
    }

    @Test
    void nonMapFieldIsRejected() {
        Compilation compilation = compile(src("demo.Bad", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Bad {
              @ProtoField(number = 1) @ProtoDeterministic public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("@ProtoDeterministic is only valid on Map fields");
    }

    @Test
    void enumTypeIsRejected() {
        Compilation compilation = compile(src("demo.Color", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoDeterministic;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            @ProtoEnum
            @ProtoDeterministic
            public enum Color {
              @ProtoEnumValue(0) UNKNOWN
            }
            """));
        assertThat(compilation).hadErrorContaining("@ProtoDeterministic is only valid on Map fields");
    }

    private static Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ProtoviaProcessor()).compile(files);
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }

    private static JavaFileObject uuidAdapter() {
        return src("demo.UuidString", """
            package demo;
            import io.github.rawvoid.protovia.ProtoType;
            import io.github.rawvoid.protovia.annotation.ProtoScalar;
            import io.github.rawvoid.protovia.codec.ProtoAdapter;
            import java.util.UUID;
            @ProtoScalar(ProtoType.STRING)
            public final class UuidString implements ProtoAdapter<UUID, String> {
              public static final UuidString INSTANCE = new UuidString();
              public String toWire(UUID value) { return value.toString(); }
              public UUID fromWire(String wire) { return UUID.fromString(wire); }
            }
            """);
    }
}
