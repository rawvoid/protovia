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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Rawvoid
 */
class ImmutableProcessorTest {

    @Test
    void allArgsConstructorAndGetters() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Point {
              @ProtoField(number = 1) private final int x;
              @ProtoField(number = 2) private final int y;
              public Point(int x, int y) { this.x = x; this.y = y; }
              public int getX() { return x; }
              public int getY() { return y; }
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("demo.internal.PointProtoCodec");
        assertTrue(generated(compilation, "demo.internal.PointProtoCodec").contains("return new Point("));
        assertTrue(generated(compilation, "demo.internal.PointProtoCodec").contains("existing.getX()"));
    }

    @Test
    void protoCreatorFactory() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoCreator;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Point {
              @ProtoField(number = 1) private final int x;
              @ProtoField(number = 2) private final int y;
              private Point(int x, int y) { this.x = x; this.y = y; }
              public int getX() { return x; }
              public int getY() { return y; }
              @ProtoCreator
              public static Point of(int x, int y) { return new Point(x, y); }
            }
            """));
        assertThat(compilation).succeeded();
        assertTrue(generated(compilation, "demo.internal.PointProtoCodec").contains("return Point.of("));
    }

    @Test
    void protoCreatorRebindsByParameterName() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoCreator;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Point {
              @ProtoField(number = 1) private final int x;
              @ProtoField(number = 2) private final int y;
              public int getX() { return x; }
              public int getY() { return y; }
              @ProtoCreator
              public Point(int y, int x) { this.x = x; this.y = y; }
            }
            """));
        assertThat(compilation).succeeded();
        String codec = generated(compilation, "demo.internal.PointProtoCodec");
        assertTrue(codec.contains("return new Point(f_y, f_x)"));
    }

    @Test
    void handwrittenBuilder() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Point {
              @ProtoField(number = 1) private final int x;
              @ProtoField(number = 2) private final int y;
              private Point(int x, int y) { this.x = x; this.y = y; }
              public int getX() { return x; }
              public int getY() { return y; }
              public static Builder builder() { return new Builder(); }
              public static final class Builder {
                private int x;
                private int y;
                public Builder x(int x) { this.x = x; return this; }
                public Builder y(int y) { this.y = y; return this; }
                public Point build() { return new Point(x, y); }
              }
            }
            """));
        assertThat(compilation).succeeded();
        String codec = generated(compilation, "demo.internal.PointProtoCodec");
        assertTrue(codec.contains("Point.builder()"));
        assertTrue(codec.contains("builder.x("));
        assertTrue(codec.contains("builder.build()"));
    }

    @Test
    void protoBuilderWithPrefix() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoBuilder;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            @ProtoBuilder(setterPrefix = "with")
            public class Point {
              @ProtoField(number = 1) private final int x;
              public int getX() { return x; }
              private Point(int x) { this.x = x; }
              public static Builder builder() { return new Builder(); }
              public static final class Builder {
                private int x;
                public Builder withX(int x) { this.x = x; return this; }
                public Point build() { return new Point(x); }
              }
            }
            """));
        assertThat(compilation).succeeded();
        assertTrue(generated(compilation, "demo.internal.PointProtoCodec").contains("builder.withX("));
    }

    @Test
    void nestedBuilderWithoutFactoryMethod() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoBuilder;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            @ProtoBuilder(builderMethod = "")
            public class Point {
              @ProtoField(number = 1) private final int x;
              public int getX() { return x; }
              private Point(int x) { this.x = x; }
              public static final class Builder {
                private int x;
                public Builder x(int x) { this.x = x; return this; }
                public Point build() { return new Point(x); }
              }
            }
            """));
        assertThat(compilation).succeeded();
        assertTrue(generated(compilation, "demo.internal.PointProtoCodec").contains("new Point.Builder()"));
    }

    @Test
    void mutablePojoStillPreferredOverBuilder() throws Exception {
        Compilation compilation = compile(src("demo.Point", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Point {
              @ProtoField(number = 1) private int x;
              public int getX() { return x; }
              public void setX(int x) { this.x = x; }
              public static Builder builder() { return new Builder(); }
              public static final class Builder {
                private int x;
                public Builder x(int x) { this.x = x; return this; }
                public Point build() { Point p = new Point(); p.x = x; return p; }
              }
            }
            """));
        assertThat(compilation).succeeded();
        String codec = generated(compilation, "demo.internal.PointProtoCodec");
        assertTrue(codec.contains("new Point()"));
        assertTrue(!codec.contains("Point.builder()"));
    }

    @Test
    void getterOnlyWithoutConstructionPathFails() {
        Compilation compilation = compile(src("demo.Bad", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Bad {
              @ProtoField(number = 1) private int x;
              public int getX() { return x; }
            }
            """));
        assertThat(compilation).hadErrorContaining("cannot be instantiated");
    }

    @Test
    void privateAllArgsConstructorFails() {
        Compilation compilation = compile(src("demo.Bad", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Bad {
              @ProtoField(number = 1) private final int x;
              public int getX() { return x; }
              private Bad(int x) { this.x = x; }
            }
            """));
        assertThat(compilation).hadErrorContaining("cannot be instantiated");
    }

    @Test
    void twoProtoCreatorsFail() {
        Compilation compilation = compile(src("demo.Bad", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoCreator;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Bad {
              @ProtoField(number = 1) private final int x;
              public int getX() { return x; }
              @ProtoCreator public Bad(int x) { this.x = x; }
              @ProtoCreator public static Bad of(int x) { return new Bad(x); }
            }
            """));
        assertThat(compilation).hadErrorContaining("at most one @ProtoCreator");
    }

    @Test
    void creatorMissingMemberFails() {
        Compilation compilation = compile(src("demo.Bad", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoCreator;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Bad {
              @ProtoField(number = 1) private final int x;
              @ProtoField(number = 2) private final int y;
              public int getX() { return x; }
              public int getY() { return y; }
              @ProtoCreator public Bad(int x) { this.x = x; this.y = 0; }
            }
            """));
        assertThat(compilation).hadErrorContaining("constructor has 1 parameters but the message has 2 proto members");
    }

    @Test
    void ambiguousPositionalConstructorFails() {
        Compilation compilation = compile(src("demo.Person", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Person {
              @ProtoField(number = 1) private final String d;
              @ProtoField(number = 2) private final String b;
              @ProtoField(number = 3) private final String c;
              @ProtoField(number = 4) private final String a;
              public Person(String w, String x, String y, String z) {
                this.d = w; this.b = x; this.c = y; this.a = z;
              }
              public String getD() { return d; }
              public String getB() { return b; }
              public String getC() { return c; }
              public String getA() { return a; }
            }
            """));
        assertThat(compilation).hadErrorContaining("ambiguous positional match");
    }

    @Test
    void ambiguousPositionalProtoCreatorFails() {
        Compilation compilation = compile(src("demo.Person", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoCreator;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Person {
              @ProtoField(number = 1) private final String d;
              @ProtoField(number = 2) private final String b;
              @ProtoField(number = 3) private final String c;
              @ProtoField(number = 4) private final String a;
              @ProtoCreator
              public Person(String w, String x, String y, String z) {
                this.d = w; this.b = x; this.c = y; this.a = z;
              }
              public String getD() { return d; }
              public String getB() { return b; }
              public String getC() { return c; }
              public String getA() { return a; }
            }
            """));
        assertThat(compilation).hadErrorContaining("ambiguous positional match");
    }

    @Test
    void ambiguousPositionalFactoryFails() {
        Compilation compilation = compile(src("demo.Pair", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Pair {
              @ProtoField(number = 1) private final String first;
              @ProtoField(number = 2) private final String second;
              private Pair(String first, String second) { this.first = first; this.second = second; }
              public String getFirst() { return first; }
              public String getSecond() { return second; }
              public static Pair of(String x, String y) { return new Pair(x, y); }
            }
            """));
        assertThat(compilation).hadErrorContaining("ambiguous positional match");
    }

    @Test
    void unambiguousPositionalConstructorPreservesDeclarationOrder() throws Exception {
        Compilation compilation = compile(src("demo.RecordLike", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class RecordLike {
              @ProtoField(number = 3) private final String zebra;
              @ProtoField(number = 1) private final int middle;
              @ProtoField(number = 2) private final boolean alpha;
              public RecordLike(String p0, int p1, boolean p2) {
                this.zebra = p0; this.middle = p1; this.alpha = p2;
              }
              public String getZebra() { return zebra; }
              public int getMiddle() { return middle; }
              public boolean isAlpha() { return alpha; }
            }
            """));
        assertThat(compilation).succeeded();
        assertTrue(generated(compilation, "demo.internal.RecordLikeProtoCodec")
            .contains("return new RecordLike(f_zebra, f_middle, f_alpha)"));
    }

    @Test
    void namedConstructorPreferredOverAmbiguousOverload() throws Exception {
        Compilation compilation = compile(src("demo.Pair", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Pair {
              @ProtoField(number = 1) private final String first;
              @ProtoField(number = 2) private final String second;
              public Pair(CharSequence x, CharSequence y) { this(x.toString(), y.toString()); }
              public Pair(String second, String first) { this.first = first; this.second = second; }
              public String getFirst() { return first; }
              public String getSecond() { return second; }
            }
            """));
        assertThat(compilation).succeeded();
        assertTrue(generated(compilation, "demo.internal.PairProtoCodec")
            .contains("return new Pair(f_second, f_first)"));
    }

    @Test
    void unannotatedOverloadDoesNotReorderAnnotatedGetterDeclarationOrder() throws Exception {
        Compilation compilation = compile(src("demo.Holder", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Holder {
              private final String a;
              private final int b;
              public Holder(String p0, int p1) { this.a = p0; this.b = p1; }
              public int getB(int index) { return b + index; }
              @ProtoField(number = 1) public String getA() { return a; }
              @ProtoField(number = 2) public int getB() { return b; }
            }
            """));
        assertThat(compilation).succeeded();
        assertTrue(generated(compilation, "demo.internal.HolderProtoCodec")
            .contains("return new Holder(f_a, f_b)"));
    }

    private static Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ProtoviaProcessor()).compile(files);
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }

    private static String generated(Compilation compilation, String fqcn) throws Exception {
        return compilation.generatedSourceFile(fqcn).orElseThrow().getCharContent(false).toString();
    }
}
