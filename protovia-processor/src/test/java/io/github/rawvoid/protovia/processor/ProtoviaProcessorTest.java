package io.github.rawvoid.protovia.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class ProtoviaProcessorTest {

    @Test
    void generatesPojoAndRecordCodecs() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "demo.Person",
                                "package demo;",
                                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                                "@ProtoMessage",
                                "public class Person {",
                                "  @ProtoField(number = 1) private String name;",
                                "  @ProtoField(number = 2) private int age;",
                                "  public String getName() { return name; }",
                                "  public void setName(String name) { this.name = name; }",
                                "  public int getAge() { return age; }",
                                "  public void setAge(int age) { this.age = age; }",
                                "}"),
                        JavaFileObjects.forSourceLines(
                                "demo.City",
                                "package demo;",
                                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                                "@ProtoMessage",
                                "public record City(@ProtoField(number = 1) String name) {}"));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("demo.PersonProtoCodec");
        assertThat(compilation).generatedSourceFile("demo.CityProtoCodec");
    }

    @Test
    void privateFieldWithoutAccessorsFails() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "demo.Bad",
                        "package demo;",
                        "import io.github.rawvoid.protovia.annotation.ProtoField;",
                        "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                        "@ProtoMessage",
                        "public class Bad {",
                        "  @ProtoField(number = 1) private int age;",
                        "}"));
        assertThat(compilation).hadErrorContaining("getter and setter");
    }

    @Test
    void duplicateFieldNumberFails() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "demo.Dup",
                        "package demo;",
                        "import io.github.rawvoid.protovia.annotation.ProtoField;",
                        "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                        "@ProtoMessage",
                        "public class Dup {",
                        "  @ProtoField(number = 1) public String a;",
                        "  @ProtoField(number = 1) public String b;",
                        "}"));
        assertThat(compilation).hadErrorContaining("duplicate field number");
    }

    @Test
    void reservedFieldNumberFails() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "demo.Reserved",
                        "package demo;",
                        "import io.github.rawvoid.protovia.annotation.ProtoField;",
                        "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                        "@ProtoMessage",
                        "public class Reserved {",
                        "  @ProtoField(number = 19000) public String a;",
                        "}"));
        assertThat(compilation).hadErrorContaining("invalid field number");
    }

    @Test
    void optionalPrimitiveFails() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "demo.Opt",
                        "package demo;",
                        "import io.github.rawvoid.protovia.annotation.ProtoField;",
                        "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                        "@ProtoMessage",
                        "public class Opt {",
                        "  @ProtoField(number = 1, optional = true) public int age;",
                        "}"));
        assertThat(compilation).hadErrorContaining("cannot be a primitive");
    }

    @Test
    void enumMissingZeroFails() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "demo.Color",
                        "package demo;",
                        "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                        "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                        "@ProtoEnum",
                        "public enum Color {",
                        "  @ProtoEnumValue(1) RED",
                        "}"));
        assertThat(compilation).hadErrorContaining("number 0");
    }

    @Test
    void mapKeyMustBeScalar() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "demo.Addr",
                                "package demo;",
                                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                                "@ProtoMessage",
                                "public class Addr { @ProtoField(number = 1) public String city; }"),
                        JavaFileObjects.forSourceLines(
                                "demo.BadMap",
                                "package demo;",
                                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                                "import java.util.Map;",
                                "@ProtoMessage",
                                "public class BadMap {",
                                "  @ProtoField(number = 1) public Map<Addr, String> values;",
                                "}"));
        assertThat(compilation).hadErrorContaining("map key");
    }

    @Test
    void boxedIntegerArrayUsesToArrayNotToIntArray() {
        Compilation compilation = javac()
                .withProcessors(new ProtoviaProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "demo.Nums",
                        "package demo;",
                        "import io.github.rawvoid.protovia.annotation.ProtoField;",
                        "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                        "@ProtoMessage",
                        "public class Nums {",
                        "  @ProtoField(number = 1) public int[] primitive;",
                        "  @ProtoField(number = 2) public Integer[] boxed;",
                        "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("demo.NumsProtoCodec")
                .contentsAsUtf8String()
                .contains("toIntArray()");
        assertThat(compilation)
                .generatedSourceFile("demo.NumsProtoCodec")
                .contentsAsUtf8String()
                .contains("toArray(new java.lang.Integer[0])");
    }
}
