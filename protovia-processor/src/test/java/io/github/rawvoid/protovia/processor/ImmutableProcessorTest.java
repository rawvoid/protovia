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
        assertTrue(codec.contains("return new Point(y, x)"));
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
