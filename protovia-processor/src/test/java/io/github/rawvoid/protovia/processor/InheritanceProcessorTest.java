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
class InheritanceProcessorTest {

    @Test
    void flattensConcreteParentGetters() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) private long id;
                  public long getId() { return id; }
                  public void setId(long id) { this.id = id; }
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getId()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("setId");
    }

    @Test
    void flattensAbstractParentAndEmptyLeaf() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public abstract class Base {
                  @ProtoField(number = 1) public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {}
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("((demo.Base) value).id");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("((demo.Base) msg).id");
    }

    @Test
    void flattensMultiLevelAndCustomProtoName() {
        Compilation compilation = compile(
            src("demo.A", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class A {
                  @ProtoField(number = 1, name = "created_at") public long createdAt;
                }
                """),
            src("demo.B", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class B extends A {
                  @ProtoField(number = 2) public String tenant;
                }
                """),
            src("demo.C", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class C extends B {
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CProtoCodec")
            .contentsAsUtf8String()
            .contains("((demo.A) value).createdAt");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CProtoCodec")
            .contentsAsUtf8String()
            .contains("((demo.B) value).tenant");
    }

    @Test
    void specializesGenericList() {
        Compilation compilation = compile(
            src("demo.Item", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Item {
                  @ProtoField(number = 1) public String name;
                }
                """),
            src("demo.PageResult", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import java.util.List;
                public class PageResult<T> {
                  @ProtoField(number = 1) public List<T> items;
                  @ProtoField(number = 2) public long total;
                }
                """),
            src("demo.ItemPage", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class ItemPage extends PageResult<Item> {}
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ItemPageProtoCodec")
            .contentsAsUtf8String()
            .contains("ItemProtoCodec");
    }

    @Test
    void specializesMidLayer() {
        Compilation compilation = compile(
            src("demo.Node", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Node {
                  @ProtoField(number = 1) public String name;
                }
                """),
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base<T> {
                  @ProtoField(number = 1) public T data;
                }
                """),
            src("demo.Mid", """
                package demo;
                public class Mid<T> extends Base<T> {}
                """),
            src("demo.Leaf", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Leaf extends Mid<Node> {}
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.LeafProtoCodec")
            .contentsAsUtf8String()
            .contains("NodeProtoCodec");
    }

    @Test
    void leafAdaptersRemapInheritedField() {
        Compilation compilation = compile(
            localDateAdapter(),
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import java.time.LocalDate;
                public class Base {
                  @ProtoField(number = 1) public LocalDate born;
                }
                """),
            src("demo.Person", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoAdapters;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                @ProtoAdapters(LocalDateEpochDay.class)
                public class Person extends Base {}
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
    }

    @Test
    void parentReservedBlocksLeafNumber() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoReserved;
                @ProtoReserved(numbers = 16, names = "legacy")
                public class Base {
                  @ProtoField(number = 1) public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).hadErrorContaining("field number 16 is reserved");
    }

    @Test
    void flattensInheritedOneof() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                public class Base {
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object target;
                }
                """),
            src("demo.Box", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Box extends Base {
                  @ProtoField(number = 1) public boolean ok;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_10");
    }

    @Test
    void flattensInheritedUnknown() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.UnknownFields;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoUnknown;
                public class Base {
                  @ProtoField(number = 1) public String name;
                  @ProtoUnknown public UnknownFields unknown;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {}
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("UnknownFields.merge");
    }

    @Test
    void packagePrivateMixinPublicFieldFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                class Base {
                  @ProtoField(number = 1) public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {}
                """));
        assertThat(compilation).hadErrorContaining("not accessible from demo.internal");
    }

    @Test
    void packagePrivateMixinPublicAccessorsWork() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                abstract class Base {
                  @ProtoField(number = 1) private long id;
                  public long getId() { return id; }
                  public void setId(long id) { this.id = id; }
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getId()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("demo.Base");
    }

    @Test
    void crossPackagePublicFieldWorks() {
        Compilation compilation = compile(
            src("lib.Base", """
                package lib;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) public int n;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import lib.Base;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("((lib.Base) value).n");
    }

    @Test
    void crossPackagePublicAccessorsWork() {
        Compilation compilation = compile(
            src("lib.Base", """
                package lib;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) private int n;
                  public int getN() { return n; }
                  public void setN(int n) { this.n = n; }
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import lib.Base;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getN()");
    }

    @Test
    void protoMessageParentFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Base {
                  @ProtoField(number = 1) public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 2) public String name;
                }
                """));
        assertThat(compilation).hadErrorContaining("inheritance of @ProtoMessage types is not supported");
    }

    @Test
    void interfaceMixinFails() {
        Compilation compilation = compile(
            src("demo.HasId", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public interface HasId {
                  @ProtoField(number = 1) String getId();
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User implements HasId {
                  @ProtoField(number = 16) public String name;
                  public String getId() { return "x"; }
                }
                """));
        assertThat(compilation).hadErrorContaining("interface mixin is not supported");
    }

    @Test
    void duplicateNumberFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 1) public String name;
                }
                """));
        assertThat(compilation).hadErrorContaining("duplicate field number 1");
    }

    @Test
    void duplicateProtoNameFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 2, name = "id") public long userId;
                }
                """));
        assertThat(compilation).hadErrorContaining("duplicate proto field name 'id'");
    }

    @Test
    void duplicateJavaPropertyFailsEvenWithDifferentProtoNames() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1, name = "legacy_id") public String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 2, name = "id") public String id;
                }
                """));
        assertThat(compilation).hadErrorContaining("duplicate Java property 'id'");
    }

    @Test
    void boxedVsPrimitiveSameJavaPropertyFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) public int count;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 2, name = "count_box") public Integer count;
                }
                """));
        assertThat(compilation).hadErrorContaining("duplicate Java property 'count'");
    }

    @Test
    void rawSuperclassFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base<T> {
                  @ProtoField(number = 1) public T data;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {}
                """));
        assertThat(compilation).hadErrorContaining("raw superclass");
    }

    @Test
    void wildcardSuperclassFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base<T> {
                  @ProtoField(number = 1) public T data;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User<T> extends Base<T> {}
                """));
        assertThat(compilation).hadErrorContaining("unbound type arguments");
    }

    @Test
    void packagePrivateInheritedFieldFails() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                public class Base {
                  @ProtoField(number = 1) String id;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class User extends Base {}
                """));
        assertThat(compilation).hadErrorContaining("needs a public JavaBean getter");
    }

    @Test
    void twoUnknownSlotsFail() {
        Compilation compilation = compile(
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.UnknownFields;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoUnknown;
                public class Base {
                  @ProtoField(number = 1) public String id;
                  @ProtoUnknown public UnknownFields unknown;
                }
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.UnknownFields;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoUnknown;
                @ProtoMessage
                public class User extends Base {
                  @ProtoField(number = 16) public String name;
                  @ProtoUnknown public UnknownFields extra;
                }
                """));
        assertThat(compilation).hadErrorContaining("at most one @ProtoUnknown per message");
    }

    @Test
    void childFieldCollidesWithParentOneofCaseNumber() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Base", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                public class Base {
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object target;
                }
                """),
            src("demo.Box", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Box extends Base {
                  @ProtoField(number = 10) public String name;
                }
                """));
        assertThat(compilation).hadErrorContaining("duplicate field number 10");
    }

    private static JavaFileObject localDateAdapter() {
        return src("demo.LocalDateEpochDay", """
            package demo;
            import io.github.rawvoid.protovia.ProtoType;
            import io.github.rawvoid.protovia.annotation.ProtoScalar;
            import io.github.rawvoid.protovia.codec.ProtoAdapter;
            import java.time.LocalDate;
            @ProtoScalar(ProtoType.INT32)
            public final class LocalDateEpochDay implements ProtoAdapter<LocalDate, Integer> {
              public static final LocalDateEpochDay INSTANCE = new LocalDateEpochDay();
              private LocalDateEpochDay() {}
              public Integer toWire(LocalDate value) { return (int) value.toEpochDay(); }
              public LocalDate fromWire(Integer wire) { return LocalDate.ofEpochDay(wire); }
            }
            """);
    }

    private static Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ProtoviaProcessor()).compile(files);
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }
}
