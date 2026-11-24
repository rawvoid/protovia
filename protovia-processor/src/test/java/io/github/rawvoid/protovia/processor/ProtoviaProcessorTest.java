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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * @author Rawvoid
 */
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
        assertThat(compilation).generatedSourceFile("demo.internal.PersonProtoCodec");
        assertThat(compilation).generatedSourceFile("demo.internal.CityProtoCodec");
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
        assertThat(compilation).hadErrorContaining("needs a public JavaBean getter");
    }

    @Test
    void fieldAndGetterBothAnnotatedFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Dup",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "@ProtoMessage",
                "public class Dup {",
                "  @ProtoField(number = 1) private int age;",
                "  @ProtoField(number = 1) public int getAge() { return age; }",
                "}"));
        assertThat(compilation).hadErrorContaining("already annotated");
    }

    @Test
    void oneofOnFieldOccupiesNameEvenIfInvalid() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Bad",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage",
                "public class Bad {",
                "  @ProtoOneof({}) public String data;",
                "  @ProtoField(number = 1) public String getData() { return data; }",
                "  public void setData(String data) { this.data = data; }",
                "}"));
        assertThat(compilation).hadErrorContaining("already annotated");
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
    void unrecognizedCannotHaveNumber() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Color",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                "import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;",
                "@ProtoEnum public enum Color {",
                "  @ProtoEnumValue(0) UNKNOWN,",
                "  @ProtoUnrecognized @ProtoEnumValue(-1) UNRECOGNIZED",
                "}"));
        assertThat(compilation).hadErrorContaining("@ProtoUnrecognized");
    }

    @Test
    void oneofGeneratesInstanceofSwitch() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Target",
                    "package demo;",
                    "public sealed interface Target permits Email, Home {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "public record Home(Addr address) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoField(number = 1) public String name;",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("else if (f_target instanceof Home");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoException");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("unexpected type");
    }

    @Test
    void oneofOnGetterIsAccepted() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Target",
                    "package demo;",
                    "public sealed interface Target permits Email, Home {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "public record Home(Addr address) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  private String name;",
                    "  private Target target;",
                    "  @ProtoField(number = 1) public String getName() { return name; }",
                    "  public void setName(String name) { this.name = name; }",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public Target getTarget() { return target; }",
                    "  public void setTarget(Target target) { this.target = target; }",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getTarget()");
    }

    @Test
    void fieldAndOneofCaseWithDistinctExportNamesCompile() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Target",
                    "package demo;",
                    "public sealed interface Target permits Email, Home {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "public record Home(String city) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Mail",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Mail {",
                    "  @ProtoField(number = 1) public String email;",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class, name = \"mail\"),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MailProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_1");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MailProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_10");
    }

    @Test
    void oneofPayloadCodecUsesParentPackage() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "other.Target",
                    "package other;",
                    "public sealed interface Target permits Email, Home {}"),
                JavaFileObjects.forSourceLines(
                    "other.Email",
                    "package other;",
                    "public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "other.Addr",
                    "package other;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "other.Home",
                    "package other;",
                    "public record Home(Addr address) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoField(number = 1) public String name;",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = other.Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = other.Home.class)",
                    "  })",
                    "  public other.Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("import other.internal.AddrProtoCodec");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("AddrProtoCodec.INSTANCE");
    }

    @Test
    void stringCannotBeBytes() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Bad",
                "package demo;",
                "import io.github.rawvoid.protovia.ProtoType;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "@ProtoMessage public class Bad {",
                "  @ProtoField(number = 1, type = ProtoType.BYTES) public String name;",
                "}"));
        assertThat(compilation).hadErrorContaining("ProtoType.BYTES");
    }

    @Test
    void oneofAndUnknownOnSameFieldFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Bad",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                "@ProtoMessage",
                "public class Bad {",
                "  @ProtoOneof({}) @ProtoUnknown public String data;",
                "}"));
        assertThat(compilation).hadErrorContaining("cannot combine @ProtoOneof with @ProtoUnknown");
    }

    @Test
    void recordOneofOnComponentFieldOnAccessorFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Box",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage",
                "public record Box(@ProtoOneof({}) String target) {",
                "  @ProtoField(number = 1) public String target() { return target; }",
                "}"));
        assertThat(compilation).hadErrorContaining("cannot combine @ProtoOneof with @ProtoField");
    }

    @Test
    void recordConflictingOneofAnnotationsDoNotBind() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Box",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage",
                "public record Box(",
                "  @ProtoField(number = 10) String name,",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class, name = \"text\") }) Object data) {",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 11, of = Integer.class) })",
                "  public Object data() { return data; }",
                "}"));
        assertThat(compilation).hadErrorContaining(
            "do not annotate both the record component and its accessor with @ProtoOneof");
        org.junit.jupiter.api.Assertions.assertTrue(compilation.errors().stream().noneMatch(d ->
            d.getMessage(null) != null && d.getMessage(null).contains("duplicate field number")));
    }

    @Test
    void oneofRejectsUnassignableCases() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.Bad",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Bad {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public String target;",
                    "}")));
        assertThat(compilation).hadErrorContaining("is not assignable");
        org.junit.jupiter.api.Assertions.assertTrue(compilation.errors().stream().noneMatch(d ->
            d.getSource() != null && d.getSource().getName().contains("Email")));
    }

    @Test
    void oneofErasesTypeVariableBound() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.Box",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Box<T extends Target> {",
                    "  @ProtoField(number = 1) public boolean success;",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public T data;",
                    "}")));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("else if (f_data instanceof Home");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain(" T ");
    }

    @Test
    void oneofAcceptsTypeVariableOnGenericRecord() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.BoxRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public record BoxRecord<T extends Target>(",
                    "  @ProtoField(number = 1) boolean success,",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  }) T data) {}")));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("Target f_data = existing != null ? existing.data() : null");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("return new BoxRecord(");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxRecordProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain(" T ");
    }

    @Test
    void oneofAcceptsTypeVariableOnGetter() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.Box",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Box<T extends Target> {",
                    "  private T data;",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public T getData() { return data; }",
                    "  public void setData(T data) { this.data = data; }",
                    "}")));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getData()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Email");
    }

    @Test
    void oneofAcceptsIntersectionBound() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Target",
                    "package demo;",
                    "public interface Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "import java.io.Serializable;",
                    "public record Email(String value) implements Target, Serializable {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "import java.io.Serializable;",
                    "public record Home(Addr address) implements Target, Serializable {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Box",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "import java.io.Serializable;",
                    "@ProtoMessage public class Box<T extends Target & Serializable> {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public T data;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Email");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain(" T ");
    }

    @Test
    void oneofErasesUnboundedTypeVariable() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.Box",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Box<T> {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public T data;",
                    "}")));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("Object f_data =");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Email");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain(" T ");
    }

    @Test
    void oneofEmptyCasesRejected() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Bad",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Bad { @ProtoOneof({}) public Object target; }"));
        assertThat(compilation).hadErrorContaining("at least one @ProtoOneof.Case");
    }

    @Test
    void oneofAcceptsSingleWrapperCase() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })",
                    "  public Email target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Email");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("unexpected type");
    }

    @Test
    void oneofAcceptsNakedStringCase() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class, name = \"text\") })",
                "  public Object data;",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof String");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("readString()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeStringNoTag(_c)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("new String(");
    }

    @Test
    void oneofRejectsOverlappingCases() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Animal",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Animal { @ProtoField(number = 1) public String name; }"),
                JavaFileObjects.forSourceLines(
                    "demo.Dog",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Dog extends Animal { @ProtoField(number = 2) public int age; }"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Animal.class),",
                    "    @ProtoOneof.Case(number = 11, of = Dog.class)",
                    "  })",
                    "  public Animal target;",
                    "}"));
        assertThat(compilation).hadErrorContaining("overlap");
        org.junit.jupiter.api.Assertions.assertTrue(compilation.errors().stream().anyMatch(d ->
            d.getMessage(null).contains("overlap")
                && d.getSource() != null
                && d.getSource().getName().contains("Holder")));
    }

    @Test
    void oneofRejectsDuplicateOf() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  @ProtoOneof({",
                "    @ProtoOneof.Case(number = 10, of = String.class, name = \"a\"),",
                "    @ProtoOneof.Case(number = 11, of = String.class, name = \"b\")",
                "  })",
                "  public Object data;",
                "}"));
        assertThat(compilation).hadErrorContaining("overlap");
    }

    @Test
    void oneofCaseNumberCollidesWithField() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoField(number = 10) public String name;",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })",
                    "  public Email target;",
                    "}"));
        assertThat(compilation).hadErrorContaining("duplicate field number");
    }

    @Test
    void oneofRejectsContainerFieldTypes() {
        String message = "must be a reference type (not primitive, array, List, Set, Collection, Map, or Optional)";
        assertThat(compileOneofOn("java.util.List<String> target")).hadErrorContaining(message);
        assertThat(compileOneofOn("java.util.Set<String> target")).hadErrorContaining(message);
        assertThat(compileOneofOn("int target")).hadErrorContaining(message);
        assertThat(compileOneofOn("java.util.Optional<String> target")).hadErrorContaining(message);
        assertThat(compileOneofOn("String[] target")).hadErrorContaining(message);
    }

    @Test
    void oneofEmptyRecordCannotDeclareType() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Empty",
                    "package demo;",
                    "public record Empty() {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "public record Label(String s) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Empty.class, type = ProtoType.SINT32),",
                    "    @ProtoOneof.Case(number = 11, of = Label.class)",
                    "  })",
                    "  public Object event;",
                    "}"));
        assertThat(compilation).hadErrorContaining("cannot declare type");
    }

    @Test
    void oneofMessageWrapperCannotDeclareAdapter() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "public record Home(Addr address) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "public record Label(String s) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Home.class, adapter = LocalDateEpochDay.class),",
                    "    @ProtoOneof.Case(number = 11, of = Label.class)",
                    "  })",
                    "  public Object target;",
                    "}"));
        assertThat(compilation).hadErrorContaining("cannot declare adapter");
    }

    @Test
    void oneofAcceptsUnsealedInterface() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public Target target;",
                    "}")));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Email");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("else if (f_target instanceof Home");
    }

    @Test
    void oneofProtoMessageRecordIsSelfMessage() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Address",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Address(",
                    "  @ProtoField(number = 1) String city,",
                    "  @ProtoField(number = 2) String street) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Bag",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Bag {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = String.class, name = \"text\"),",
                    "    @ProtoOneof.Case(number = 11, of = Address.class)",
                    "  })",
                    "  public Object data;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BagProtoCodec")
            .contentsAsUtf8String()
            .contains("reader.readMessage(AddressProtoCodec.INSTANCE, _cur instanceof Address _prev ? _prev : null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BagProtoCodec")
            .contentsAsUtf8String()
            .contains("AddressProtoCodec.INSTANCE.writeTo(writer, _c)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BagProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("new Address(");
    }

    @Test
    void oneofOneComponentProtoMessageRecordIsNotFlattened() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.City",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record City(@ProtoField(number = 1) String name) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Place",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Place {",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 1, of = City.class) })",
                    "  public City where;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PlaceProtoCodec")
            .contentsAsUtf8String()
            .contains("reader.readMessage(CityProtoCodec.INSTANCE, _cur instanceof City _prev ? _prev : null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PlaceProtoCodec")
            .contentsAsUtf8String()
            .contains("CityProtoCodec.INSTANCE.writeTo(writer, _c)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PlaceProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("new City(");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PlaceProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("reader.readString()");
    }

    @Test
    void oneofAcceptsSealedSubset() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Target",
                    "package demo;",
                    "public sealed interface Target permits Email, Home, Phone {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "public record Home(Addr address) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Phone",
                    "package demo;",
                    "public record Phone(String n) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Email");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("else if (f_target instanceof Home");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("instanceof Phone");
    }

    @Test
    void oneofAcceptsNakedIntegerSint32() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.ProtoType;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  @ProtoOneof({",
                "    @ProtoOneof.Case(number = 10, of = Integer.class, type = ProtoType.SINT32)",
                "  })",
                "  public Object data;",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof Integer");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeSInt32NoTag");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("readSInt32()");
    }

    @Test
    void oneofAcceptsNakedAdaptedLocalDate() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = LocalDate.class, adapter = LocalDateEpochDay.class)",
                    "  })",
                    "  public Object event;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof LocalDate");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire(_c)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
    }

    @Test
    void oneofAcceptsNakedBytes() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = byte[].class, name = \"blob\") })",
                "  public Object data;",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof byte[]");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeBytesNoTag");
    }

    @Test
    void oneofReusesWrappersWithDifferentNumbers() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(withFieldOneof(
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Home.class)",
                    "  })",
                    "  public Target target;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Alias",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Alias {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 1, of = Email.class),",
                    "    @ProtoOneof.Case(number = 2, of = Home.class)",
                    "  })",
                    "  public Target target;",
                    "}")));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_10");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_11");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ContactProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("TAG_1 =");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AliasProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_1");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AliasProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_2");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AliasProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("TAG_10");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AliasProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("TAG_11");
    }

    @Test
    void oneofRejectsPackagePrivateCase() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.LocalNote",
                    "package demo;",
                    "record LocalNote(String value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = LocalNote.class) })",
                    "  public Object note;",
                    "}"));
        assertThat(compilation).hadErrorContaining("is not accessible");
    }

    @Test
    void oneofRejectsPrimitiveOf() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = int.class) })",
                "  public Object data;",
                "}"));
        assertThat(compilation).hadErrorContaining(
            "must be a reference type (use Integer for int32)");
    }

    @Test
    void oneofRejectsGenericCaseType() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Box",
                    "package demo;",
                    "public record Box<T>(T v) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Box.class) })",
                    "  public Object data;",
                    "}"));
        assertThat(compilation).hadErrorContaining("cannot declare type parameters");
    }

    @Test
    void oneofRejectsPrivateNestedCase() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  private record Hidden(String value) {}",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Hidden.class) })",
                "  public Object data;",
                "}"));
        assertThat(compilation).hadErrorContaining("is not accessible");
    }

    @Test
    void oneofRejectsNonStaticInnerCase() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Holder",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Holder {",
                "  @ProtoMessage public class Inner { @ProtoField(number = 1) public String value; }",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Inner.class) })",
                "  public Object data;",
                "}"));
        assertThat(compilation).hadErrorContaining("is not accessible");
    }

    @Test
    void unknownFieldsSlotGeneratesMerge() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Env",
                "package demo;",
                "import io.github.rawvoid.protovia.UnknownFields;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                "@ProtoMessage",
                "public class Env {",
                "  @ProtoField(number = 1) public String name;",
                "  @ProtoUnknown public UnknownFields unknown;",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EnvProtoCodec")
            .contentsAsUtf8String()
            .contains("UnknownFields.newBuilder");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EnvProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.merge(reader)");
    }

    @Test
    void unknownMustBeUnknownFieldsType() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Env",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                "@ProtoMessage",
                "public class Env {",
                "  @ProtoField(number = 1) public String name;",
                "  @ProtoUnknown public byte[] unknown;",
                "}"));
        assertThat(compilation).hadErrorContaining("UnknownFields");
    }

    @Test
    void unknownWrongTypeOnPrivateFieldReportsTypeNotAccess() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Env",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                "@ProtoMessage",
                "public class Env {",
                "  @ProtoField(number = 1) public String name;",
                "  @ProtoUnknown private String extra;",
                "}"));
        assertThat(compilation).hadErrorContaining("UnknownFields");
        org.junit.jupiter.api.Assertions.assertFalse(
            compilation.errors().stream()
                .anyMatch(d -> String.valueOf(d.getMessage(null)).contains("getter and setter")));
    }

    @Test
    void wellKnownAnyAndWrappersUseBuiltinCodecs() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Box",
                "package demo;",
                "import io.github.rawvoid.protovia.ProtoAny;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.wkt.Int32Value;",
                "import java.time.Instant;",
                "@ProtoMessage(name = \"Box\", packageName = \"example.v1\")",
                "public class Box {",
                "  @ProtoField(number = 1) public Instant at;",
                "  @ProtoField(number = 2) public ProtoAny extra;",
                "  @ProtoField(number = 3) public Int32Value count;",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("return \"example.v1.Box\";");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("AnyCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("Int32Value.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("TimestampCodec.INSTANCE");
    }

    @Test
    void packedIntegerListWritesViaIntArrayList() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Nums",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import java.util.List;",
                "@ProtoMessage",
                "public class Nums {",
                "  @ProtoField(number = 1) public List<Integer> ranks;",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("import io.github.rawvoid.protovia.collect.IntArrayList");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof IntArrayList");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("IntArrayList::new");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("getInt(_i)");
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
            .generatedSourceFile("demo.internal.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("toIntArray()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("toArray(Integer[]::new)");
    }

    @Test
    void adapterMissingProtoScalarFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.NoScalar",
                    "package demo;",
                    "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
                    "import java.time.LocalDate;",
                    "public final class NoScalar implements ProtoAdapter<LocalDate, Integer> {",
                    "  public static final NoScalar INSTANCE = new NoScalar();",
                    "  public Integer toWire(LocalDate value) { return 0; }",
                    "  public LocalDate fromWire(Integer wire) { return LocalDate.ofEpochDay(wire); }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = NoScalar.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("must be annotated with @ProtoScalar");
    }

    @Test
    void adapterMissingInstanceFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapterWithoutInstance(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = NoInstance.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("must declare public static final INSTANCE");
    }

    @Test
    void adapterJavaTypeMismatchFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = UuidString.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("handles UUID, not LocalDate");
    }

    @Test
    void adapterOnPrimitiveFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = LocalDateEpochDay.class) public int n;",
                    "}"));
        assertThat(compilation).hadErrorContaining("adapter cannot be applied to primitive field 'n'");
    }

    @Test
    void adapterProtoScalarMessageFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.AsMessage",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
                    "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
                    "import java.time.LocalDate;",
                    "@ProtoScalar(ProtoType.MESSAGE)",
                    "public final class AsMessage implements ProtoAdapter<LocalDate, Integer> {",
                    "  public static final AsMessage INSTANCE = new AsMessage();",
                    "  public Integer toWire(LocalDate value) { return 0; }",
                    "  public LocalDate fromWire(Integer wire) { return LocalDate.ofEpochDay(wire); }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = AsMessage.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("@ProtoScalar must name a scalar ProtoType");
    }

    @Test
    void adapterParameterizedJavaTypeFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Box",
                    "package demo;",
                    "public final class Box<T> { public T value; }"),
                JavaFileObjects.forSourceLines(
                    "demo.BoxAdapter",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
                    "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
                    "import java.time.LocalDate;",
                    "@ProtoScalar(ProtoType.INT32)",
                    "public final class BoxAdapter implements ProtoAdapter<Box<LocalDate>, Integer> {",
                    "  public static final BoxAdapter INSTANCE = new BoxAdapter();",
                    "  public Integer toWire(Box<LocalDate> value) { return 0; }",
                    "  public Box<LocalDate> fromWire(Integer wire) { return new Box<>(); }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = BoxAdapter.class) public Box<java.time.LocalDate> birth;",
                    "}"));
        assertThat(compilation).hadErrorContaining("adapter J must be a non-parameterized class");
    }

    @Test
    void packedAdaptedListUsesArrayListAndFromWire() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.List;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = LocalDateEpochDay.class) public List<LocalDate> days;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("toWire(item)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_1_PACKED");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("fromWire(reader.readInt32())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("ArrayList");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("addInt");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("IntArrayList");
    }

    @Test
    void unpackedAdaptedListConvertsViaToWire() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.List;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 2, packed = false, adapter = LocalDateEpochDay.class)",
                    "  public List<LocalDate> days;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("toWire(item)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("CodedSize.int32(2, itemWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("writeInt32NoTag(itemWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("fromWire(reader.readInt32())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("addInt");
    }

    @Test
    void adapterOnEmptyOneofCaseFailsWithE23() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Event",
                    "package demo;",
                    "public sealed interface Event permits Empty, Label {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Empty",
                    "package demo;",
                    "public record Empty() implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "public record Label(String s) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Empty.class, adapter = LocalDateEpochDay.class),",
                    "    @ProtoOneof.Case(number = 11, of = Label.class)",
                    "  })",
                    "  public Event event;",
                    "}"));
        assertThat(compilation).hadErrorContaining(
            "@ProtoOneof.Case without a scalar payload cannot declare adapter");
    }

    @Test
    void adapterOnProtoMessageOneofCaseFailsWithE23() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Target",
                    "package demo;",
                    "public sealed interface Target permits Addr, Label {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage",
                    "public class Addr implements Target {",
                    "  @ProtoField(number = 1) public String city;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "public record Label(String s) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Addr.class, adapter = LocalDateEpochDay.class),",
                    "    @ProtoOneof.Case(number = 11, of = Label.class)",
                    "  })",
                    "  public Target target;",
                    "}"));
        assertThat(compilation).hadErrorContaining(
            "@ProtoOneof.Case without a scalar payload cannot declare adapter");
    }

    @Test
    void adapterInterfaceAsValueFails() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.AdapterIface",
                    "package demo;",
                    "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
                    "import java.time.LocalDate;",
                    "public interface AdapterIface extends ProtoAdapter<LocalDate, Integer> {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = AdapterIface.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("adapter must be a concrete type, not ProtoAdapter");
    }

    @Test
    void adapterWireTypeMustMatchProtoScalar() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.WrongW",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
                    "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
                    "import java.time.LocalDate;",
                    "@ProtoScalar(ProtoType.INT32)",
                    "public final class WrongW implements ProtoAdapter<LocalDate, String> {",
                    "  public static final WrongW INSTANCE = new WrongW();",
                    "  public String toWire(LocalDate value) { return \"\"; }",
                    "  public LocalDate fromWire(String wire) { return LocalDate.EPOCH; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = WrongW.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("ProtoType.INT32 requires Integer, not String");
    }

    @Test
    void adapterFieldTypeOverrideMustStayInFamily() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, type = ProtoType.STRING, adapter = LocalDateEpochDay.class)",
                    "  public LocalDate birth;",
                    "}"));
        assertThat(compilation).hadErrorContaining("field 'birth' Java type cannot use ProtoType.STRING");
    }

    @Test
    void adapterMustBePublic() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Hidden",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
                    "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
                    "import java.time.LocalDate;",
                    "@ProtoScalar(ProtoType.INT32)",
                    "final class Hidden implements ProtoAdapter<LocalDate, Integer> {",
                    "  public static final Hidden INSTANCE = new Hidden();",
                    "  public Integer toWire(LocalDate value) { return 0; }",
                    "  public LocalDate fromWire(Integer wire) { return LocalDate.ofEpochDay(wire); }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = Hidden.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("adapter Hidden must be a public type");
    }

    @Test
    void singularAdapterGeneratesToWireAndFromWire() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1) public String name;",
                    "  @ProtoField(number = 3, adapter = LocalDateEpochDay.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_birthDate != null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("f_birthDate != 0");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("int _wire_3 =");
    }

    @Test
    void recordComponentAdapterGeneratesToWire() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Event",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage public record Event(",
                    "  @ProtoField(number = 1) String id,",
                    "  @ProtoField(number = 2, adapter = LocalDateEpochDay.class) LocalDate on) {}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("value.on()");
    }

    @Test
    void optionalLocalDateUsesIsPresentAndFromWire() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.Optional;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1, adapter = LocalDateEpochDay.class) public Optional<LocalDate> birthDate;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("isPresent()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("birthDate.get()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("birthDate != 0");
    }

    @Test
    void fieldLevelInstantOverrideIsScalarNotTimestamp() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                instantAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Audit",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.Instant;",
                    "@ProtoMessage public class Audit {",
                    "  @ProtoField(number = 1) public String id;",
                    "  @ProtoField(number = 2, adapter = InstantEpochMilli.class) public Instant created;",
                    "  @ProtoField(number = 3) public Instant published;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("writeUInt64NoTag(_wire_2)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("TimestampCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("TimestampCodec.INSTANCE.writeTo(writer, f_created)");
    }

    @Test
    void oneofCaseTypeEmitsSint32() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Event",
                    "package demo;",
                    "public sealed interface Event permits Count, Label {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Count",
                    "package demo;",
                    "public record Count(int n) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "public record Label(String s) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Count.class, type = ProtoType.SINT32),",
                    "    @ProtoOneof.Case(number = 11, of = Label.class)",
                    "  })",
                    "  public Event event;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeSInt32NoTag");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("CodedSize.sint32");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("readSInt32()");
    }

    @Test
    void mapValueAdapterConvertsThenSkipsOnWire() throws Exception {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.Map;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoField(number = 5, adapter = LocalDateEpochDay.class)",
                    "  public Map<String, LocalDate> dates;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("int vWire = LocalDateEpochDay.INSTANCE.toWire(v)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("if (vWire != 0)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("int vWire = 0");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire(vWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("fromWire(\"\")");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("fromWire(0)");
        String source = compilation.generatedSourceFile("demo.internal.HolderProtoCodec")
            .orElseThrow()
            .getCharContent(false)
            .toString();
        if (source.indexOf("reader.popLimit(oldLimit)") >= source.lastIndexOf("fromWire(vWire)")) {
            throw new AssertionError("fromWire must run after popLimit:\n" + source);
        }
    }

    @Test
    void mapKeyBytesAdapterFailsWithE14() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                uuidBytesAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.Map;",
                    "import java.util.UUID;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoField(number = 1, adapter = UuidBytes.class) public Map<UUID, String> ids;",
                    "}"));
        assertThat(compilation).hadErrorContaining(
            "map key of field 'ids' must be an integral type, bool, or string");
    }

    @Test
    void mapBytesValueAdapterSkipsOnLength() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                uuidBytesAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.Map;",
                    "import java.util.UUID;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoField(number = 1, adapter = UuidBytes.class) public Map<String, UUID> ids;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("vWire.length != 0");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("if (vWire != 0)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("byte[] vWire = new byte[0]");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("UuidBytes.INSTANCE.fromWire(vWire)");
    }

    @Test
    void oneofAdaptedCaseAlwaysWritesIncludingZero() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Event",
                    "package demo;",
                    "public sealed interface Event permits Born, Label {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Born",
                    "package demo;",
                    "import java.time.LocalDate;",
                    "public record Born(LocalDate d) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "public record Label(String s) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Born.class, adapter = LocalDateEpochDay.class),",
                    "    @ProtoOneof.Case(number = 11, of = Label.class)",
                    "  })",
                    "  public Event event;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("int _wire_10 = LocalDateEpochDay.INSTANCE.toWire(_p)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("if (_p == null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("CodedSize.int32(10, _wire_10)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeRawByte(TAG_10)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeInt32NoTag(_wire_10)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("new Born(LocalDateEpochDay.INSTANCE.fromWire(reader.readInt32()))");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("if (_wire_10 != 0)");
    }

    @Test
    void messageLevelAdaptersApplyToTwoLocalDateFields() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage",
                    "@ProtoAdapters(LocalDateEpochDay.class)",
                    "public class Person {",
                    "  @ProtoField(number = 1) public LocalDate birth;",
                    "  @ProtoField(number = 2) public LocalDate hired;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire(f_birth)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire(f_hired)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
    }

    @Test
    void fieldLevelAdapterOverridesMessageLevel() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                localDateIsoAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage",
                    "@ProtoAdapters(LocalDateEpochDay.class)",
                    "public class Person {",
                    "  @ProtoField(number = 1) public LocalDate birth;",
                    "  @ProtoField(number = 2, adapter = LocalDateIso.class) public LocalDate hired;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire(f_birth)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateIso.INSTANCE.toWire(f_hired)");
    }

    @Test
    void messageLevelAdaptersDoNotEmitUnsupportedType() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage",
                    "@ProtoAdapters(LocalDateEpochDay.class)",
                    "public class Person {",
                    "  @ProtoField(number = 1) public LocalDate birth;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
    }

    @Test
    void classLevelInstantOverrideLeavesSiblingAsTimestamp() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                instantAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Event",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.Instant;",
                    "@ProtoMessage",
                    "@ProtoAdapters(InstantEpochMilli.class)",
                    "public class Event {",
                    "  @ProtoField(number = 1) public Instant created;",
                    "  @ProtoField(number = 2) public Instant updated;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Timed",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.Instant;",
                    "@ProtoMessage public class Timed {",
                    "  @ProtoField(number = 1) public Instant at;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.toWire(f_created)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.toWire(f_updated)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("TimestampCodec");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.TimedProtoCodec")
            .contentsAsUtf8String()
            .contains("TimestampCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.TimedProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("InstantEpochMilli");
    }

    @Test
    void classLevelIntegerAdapterUsesReferencePresence() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                alwaysWriteIntAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Counter",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage",
                    "@ProtoAdapters(AlwaysWriteInt.class)",
                    "public class Counter {",
                    "  @ProtoField(number = 1) public Integer count;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CounterProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_count != null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CounterProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("f_count != 0");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CounterProtoCodec")
            .contentsAsUtf8String()
            .contains("AlwaysWriteInt.INSTANCE.toWire");
    }

    @Test
    void protoAdaptedJavaTypeMismatchIsE8() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Money",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapted;",
                    "@ProtoAdapted(LocalDateEpochDay.class)",
                    "public record Money(long cents) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Order",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Order {",
                    "  @ProtoField(number = 1) public Money total;",
                    "}"));
        assertThat(compilation).hadErrorContaining("handles LocalDate, not Money");
        assertThat(compilation).failed();
    }

    @Test
    void protoAdaptedOnMoneyWorks() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                moneyType(),
                moneyCentsRecordAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Order",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Order {",
                    "  @ProtoField(number = 1) public Money total;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OrderProtoCodec")
            .contentsAsUtf8String()
            .contains("MoneyCents.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OrderProtoCodec")
            .contentsAsUtf8String()
            .contains("MoneyCents.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OrderProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_total != null)");
    }

    @Test
    void protoAdaptedIsNotInherited() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                moneyClass(),
                moneyCentsClassAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.SpecialMoney",
                    "package demo;",
                    "public class SpecialMoney extends Money {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Order",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Order {",
                    "  @ProtoField(number = 1) public SpecialMoney extra;",
                    "}"));
        assertThat(compilation).hadErrorContaining("unsupported type");
    }

    @Test
    void protoAdaptedOnProtoMessageIsE12() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                addressJsonAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Address",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapted;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage",
                    "@ProtoAdapted(AddressJson.class)",
                    "public class Address {",
                    "  @ProtoField(number = 1) public String city;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1) public Address home;",
                    "}"));
        assertThat(compilation).hadErrorContaining(
            "@ProtoAdapted cannot be applied to @ProtoMessage type Address");
    }

    @Test
    void duplicateJavaTypeInProtoAdaptersIsE13() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                localDateIsoAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage",
                    "@ProtoAdapters({LocalDateEpochDay.class, LocalDateIso.class})",
                    "public class Person {",
                    "  @ProtoField(number = 1) public LocalDate birth;",
                    "}"));
        assertThat(compilation).hadErrorContaining("duplicate adapter for LocalDate");
    }

    @Test
    void packageInfoAdaptersApplyInSameCompile() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.package-info",
                    "@ProtoAdapters({LocalDateEpochDay.class, UuidString.class})",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;"),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.UUID;",
                    "@ProtoMessage public class Person {",
                    "  @ProtoField(number = 1) public LocalDate birth;",
                    "  @ProtoField(number = 2) public UUID id;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("UuidString.INSTANCE.toWire");
    }

    @Test
    void protoAdaptersAdaptMapKeyAndValueWithWireLocals() throws Exception {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.Map;",
                    "import java.util.UUID;",
                    "@ProtoMessage",
                    "@ProtoAdapters({UuidString.class, LocalDateEpochDay.class})",
                    "public class Holder {",
                    "  @ProtoField(number = 1) public Map<UUID, LocalDate> ids;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("String kWire = \"\"");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("fromWire(\"\")");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("UuidString.INSTANCE.fromWire(kWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire(vWire)");
        String source = compilation.generatedSourceFile("demo.internal.HolderProtoCodec")
            .orElseThrow()
            .getCharContent(false)
            .toString();
        if (source.indexOf("reader.popLimit(oldLimit)") >= source.lastIndexOf("fromWire(kWire)")) {
            throw new AssertionError("fromWire must run after popLimit:\n" + source);
        }
    }

    @Test
    void wrongMapFieldAdapterIsE8EvenWhenDiscoveryMatchesASide() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "import java.util.Map;",
                    "@ProtoMessage",
                    "@ProtoAdapters(LocalDateEpochDay.class)",
                    "public class Holder {",
                    "  @ProtoField(number = 1, adapter = UuidString.class)",
                    "  public Map<String, LocalDate> dates;",
                    "}"));
        assertThat(compilation).hadErrorContaining("handles UUID, not Map");
        assertThat(compilation).failed();
    }

    @Test
    void wrongFieldAdapterDoesNotFallThroughToMessageAdapters() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Person",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.LocalDate;",
                    "@ProtoMessage",
                    "@ProtoAdapters(LocalDateEpochDay.class)",
                    "public class Person {",
                    "  @ProtoField(number = 1, adapter = UuidString.class) public LocalDate birthDate;",
                    "}"));
        assertThat(compilation).hadErrorContaining("handles UUID, not LocalDate");
        assertThat(compilation).failed();
    }

    @Test
    void mapFieldAdapterMatchingNeitherSideIsE8() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.Map;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoField(number = 1, adapter = UuidString.class)",
                    "  public Map<String, Integer> scores;",
                    "}"));
        assertThat(compilation).hadErrorContaining("handles UUID, not Map");
    }

    @Test
    void overloadedGettersAndSettersPickCompatibleOverloads() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Overloaded",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "@ProtoMessage",
                "public class Overloaded {",
                "  @ProtoField(number = 1) private String name;",
                "  @ProtoField(number = 2) private boolean active;",
                "  private String title;",
                "  public String getName() { return name; }",
                "  public String getName(int index) { return name; }",
                "  public void setName(String name) { this.name = name; }",
                "  public void setName(int index) {}",
                "  public String isActive() { return String.valueOf(active); }",
                "  public boolean getActive() { return active; }",
                "  public void setActive(String wrong) {}",
                "  public void setActive(boolean active) { this.active = active; }",
                "  @ProtoField(number = 3) public String getTitle() { return title; }",
                "  public String getTitle(int index) { return title; }",
                "  public void setTitle(int wrong) {}",
                "  public void setTitle(String title) { this.title = title; }",
                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OverloadedProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getName()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OverloadedProtoCodec")
            .contentsAsUtf8String()
            .contains("msg.setName(");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OverloadedProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getActive()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OverloadedProtoCodec")
            .contentsAsUtf8String()
            .contains("msg.setActive(");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OverloadedProtoCodec")
            .contentsAsUtf8String()
            .contains("value.getTitle()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OverloadedProtoCodec")
            .contentsAsUtf8String()
            .contains("msg.setTitle(");
    }

    @Test
    void incompatibleSetterOverloadIsNotTreatedAsWritable() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.BadSetter",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "@ProtoMessage",
                "public class BadSetter {",
                "  @ProtoField(number = 1) private String name;",
                "  public String getName() { return name; }",
                "  public void setName(int wrong) {}",
                "}"));
        assertThat(compilation).hadErrorContaining("cannot be instantiated");
    }

    @Test
    void unnamedDefaultPackageMessageFails() {
        Compilation pojo = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "RootMessage",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "@ProtoMessage",
                "public class RootMessage {",
                "  @ProtoField(number = 1) public String name;",
                "}"));
        assertThat(pojo).hadErrorContaining("cannot be in the default (unnamed) package");

        Compilation record = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "RootRecord",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "@ProtoMessage",
                "public record RootRecord(@ProtoField(number = 1) String name) {}"));
        assertThat(record).hadErrorContaining("cannot be in the default (unnamed) package");
    }

    @Test
    void optionalByteArrayAndJavaOptionalByteArrayCompile() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.BlobPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.Optional;",
                    "@ProtoMessage",
                    "public class BlobPojo {",
                    "  @ProtoField(number = 1, optional = true) public byte[] raw;",
                    "  @ProtoField(number = 2) public Optional<byte[]> opt = Optional.empty();",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.BlobRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.Optional;",
                    "@ProtoMessage",
                    "public record BlobRecord(",
                    "  @ProtoField(number = 1, optional = true) byte[] raw,",
                    "  @ProtoField(number = 2) Optional<byte[]> opt) {}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BlobPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_raw != null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BlobPojoProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("f_raw.length != 0");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BlobPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_opt != null && f_opt.isPresent())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BlobPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("Optional.of(reader.readBytes())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BlobRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("Optional.of(reader.readBytes())");
    }

    @Test
    void unsupportedAbstractCollectionsAndMapsFail() {
        Compilation queue = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.BadQueue",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import java.util.Queue;",
                "@ProtoMessage public class BadQueue {",
                "  @ProtoField(number = 1) public Queue<String> items;",
                "}"));
        assertThat(queue).hadErrorContaining("unsupported collection type Queue");

        Compilation deque = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.BadDeque",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoField;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import java.util.Deque;",
                "@ProtoMessage public class BadDeque {",
                "  @ProtoField(number = 1) public Deque<String> items;",
                "}"));
        assertThat(deque).hadErrorContaining("unsupported collection type Deque");

        Compilation customMap = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.MyMap",
                    "package demo;",
                    "import java.util.Map;",
                    "public interface MyMap<K, V> extends Map<K, V> {}"),
                JavaFileObjects.forSourceLines(
                    "demo.BadMap",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class BadMap {",
                    "  @ProtoField(number = 1) public MyMap<String, Integer> scores;",
                    "}"));
        assertThat(customMap).hadErrorContaining("unsupported map type MyMap");

        Compilation noDefaultCtor = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.NoDefaultList",
                    "package demo;",
                    "import java.util.ArrayList;",
                    "public class NoDefaultList<E> extends ArrayList<E> {",
                    "  public NoDefaultList(int cap) { super(cap); }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.BadList",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class BadList {",
                    "  @ProtoField(number = 1) public NoDefaultList<String> items;",
                    "}"));
        assertThat(noDefaultCtor).hadErrorContaining("must have a public no-arg constructor");

        Compilation hiddenList = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.HiddenList",
                    "package demo;",
                    "import java.util.ArrayList;",
                    "class HiddenList extends ArrayList<String> {}"),
                JavaFileObjects.forSourceLines(
                    "demo.BadHidden",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public class BadHidden {",
                    "  @ProtoField(number = 1) public HiddenList items;",
                    "}"));
        assertThat(hiddenList).hadErrorContaining("is not accessible from demo.internal");
    }

    @Test
    void concreteSortedAndPrimitiveCollectionsCompileOnPojoAndRecord() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.StringList",
                    "package demo;",
                    "import java.util.ArrayList;",
                    "public class StringList extends ArrayList<String> {}"),
                JavaFileObjects.forSourceLines(
                    "demo.ScoreMap",
                    "package demo;",
                    "import java.util.HashMap;",
                    "public class ScoreMap extends HashMap<String, Integer> {}"),
                JavaFileObjects.forSourceLines(
                    "demo.AllCollectionsPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.collect.BooleanArrayList;",
                    "import io.github.rawvoid.protovia.collect.DoubleArrayList;",
                    "import io.github.rawvoid.protovia.collect.FloatArrayList;",
                    "import io.github.rawvoid.protovia.collect.IntArrayList;",
                    "import io.github.rawvoid.protovia.collect.LongArrayList;",
                    "import java.util.*;",
                    "import java.util.concurrent.*;",
                    "@ProtoMessage",
                    "public class AllCollectionsPojo {",
                    "  @ProtoField(number = 1) public ArrayList<String> arrayList;",
                    "  @ProtoField(number = 2) public LinkedList<String> linkedList;",
                    "  @ProtoField(number = 3) public LinkedList<Integer> linkedInts;",
                    "  @ProtoField(number = 4) public HashSet<String> hashSet;",
                    "  @ProtoField(number = 5) public LinkedHashSet<String> linkedHashSet;",
                    "  @ProtoField(number = 6) public TreeSet<String> treeSet;",
                    "  @ProtoField(number = 7) public SortedSet<String> sortedSet;",
                    "  @ProtoField(number = 8) public NavigableSet<String> navigableSet;",
                    "  @ProtoField(number = 9) public Collection<String> collection;",
                    "  @ProtoField(number = 10) public Collection<Integer> intCollection;",
                    "  @ProtoField(number = 11) public ArrayDeque<String> arrayDeque;",
                    "  @ProtoField(number = 12) public IntArrayList ints;",
                    "  @ProtoField(number = 13) public LongArrayList longs;",
                    "  @ProtoField(number = 14) public FloatArrayList floats;",
                    "  @ProtoField(number = 15) public DoubleArrayList doubles;",
                    "  @ProtoField(number = 16) public BooleanArrayList bools;",
                    "  @ProtoField(number = 17) public HashMap<String, Integer> hashMap;",
                    "  @ProtoField(number = 18) public LinkedHashMap<String, Integer> linkedHashMap;",
                    "  @ProtoField(number = 19) public TreeMap<String, Integer> treeMap;",
                    "  @ProtoField(number = 20) public SortedMap<String, Integer> sortedMap;",
                    "  @ProtoField(number = 21) public NavigableMap<String, Integer> navigableMap;",
                    "  @ProtoField(number = 22) public ConcurrentMap<String, Integer> concurrentMap;",
                    "  @ProtoField(number = 23) public ConcurrentNavigableMap<String, Integer> concurrentNavigableMap;",
                    "  @ProtoField(number = 24) public ConcurrentHashMap<String, Integer> concurrentHashMap;",
                    "  @ProtoField(number = 25) public StringList customList;",
                    "  @ProtoField(number = 26) public ScoreMap customMap;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.AllCollectionsRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.collect.IntArrayList;",
                    "import java.util.*;",
                    "import java.util.concurrent.*;",
                    "@ProtoMessage",
                    "public record AllCollectionsRecord(",
                    "  @ProtoField(number = 1) ArrayList<String> arrayList,",
                    "  @ProtoField(number = 2) LinkedList<String> linkedList,",
                    "  @ProtoField(number = 3) HashSet<String> hashSet,",
                    "  @ProtoField(number = 4) TreeSet<String> treeSet,",
                    "  @ProtoField(number = 5) SortedSet<String> sortedSet,",
                    "  @ProtoField(number = 6) NavigableSet<String> navigableSet,",
                    "  @ProtoField(number = 7) Collection<String> collection,",
                    "  @ProtoField(number = 8) Collection<Integer> intCollection,",
                    "  @ProtoField(number = 9) IntArrayList ints,",
                    "  @ProtoField(number = 10) HashMap<String, Integer> hashMap,",
                    "  @ProtoField(number = 11) LinkedHashMap<String, Integer> linkedHashMap,",
                    "  @ProtoField(number = 12) TreeMap<String, Integer> treeMap,",
                    "  @ProtoField(number = 13) SortedMap<String, Integer> sortedMap,",
                    "  @ProtoField(number = 14) NavigableMap<String, Integer> navigableMap,",
                    "  @ProtoField(number = 15) StringList customList,",
                    "  @ProtoField(number = 16) ScoreMap customMap) {}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoLists.ensureMutableCollection(msg.collection, ArrayList::new)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoLists.ensureMutableCollection(msg.intCollection, IntArrayList::new)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoLists.ensureMutableSet(msg.sortedSet, TreeSet::new)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoLists.ensureMutablePrimitiveList(msg.ints, IntArrayList::new)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("ProtoLists.ensureMutableMap(msg.sortedMap, TreeMap::new)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("new TreeSet<>(existing.sortedSet())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("customList.addAll(existing.customList())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AllCollectionsRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("customMap.putAll(existing.customMap())");
    }

    @Test
    void nonCollectionOneArgConstructorIsNotTreatedAsCopyConstructor() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.TaggedList",
                    "package demo;",
                    "import java.io.Serializable;",
                    "import java.util.ArrayList;",
                    "public class TaggedList extends ArrayList<String> {",
                    "  public TaggedList() {}",
                    "  public TaggedList(Object tag) {}",
                    "  public TaggedList(Serializable tag) {}",
                    "  public TaggedList(Iterable<String> it) {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.TaggedMap",
                    "package demo;",
                    "import java.util.HashMap;",
                    "public class TaggedMap extends HashMap<String, Integer> {",
                    "  public TaggedMap() {}",
                    "  public TaggedMap(Object tag) {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage",
                    "public record Holder(",
                    "  @ProtoField(number = 1) TaggedList items,",
                    "  @ProtoField(number = 2) TaggedMap entries) {}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("items.addAll(existing.items())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("entries.putAll(existing.entries())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("new TaggedList(existing.items())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("new TaggedMap(existing.entries())");
    }

    @Test
    void collidingFieldNamesCompileOnPublicPojoGettersPojoRecordAndBuilder() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                uuidAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.State",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum State { @ProtoEnumValue(0) UNKNOWN, @ProtoEnumValue(1) ON }"),
                JavaFileObjects.forSourceLines(
                    "demo.Wrap",
                    "package demo;",
                    "import java.time.LocalDate;",
                    "public record Wrap(LocalDate f_x) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.CollidingPublicPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoAdapters;",
                    "import io.github.rawvoid.protovia.annotation.ProtoDeterministic;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.time.LocalDate;",
                    "import java.util.List;",
                    "import java.util.Map;",
                    "import java.util.UUID;",
                    "@ProtoMessage",
                    "@ProtoAdapters({LocalDateEpochDay.class, UuidString.class})",
                    "public class CollidingPublicPojo {",
                    "  @ProtoField(number = 1) public String value;",
                    "  @ProtoField(number = 2) public int size;",
                    "  @ProtoField(number = 3) public String cache;",
                    "  @ProtoField(number = 4) public String writer;",
                    "  @ProtoField(number = 5) public String reader;",
                    "  @ProtoField(number = 6) public String existing;",
                    "  @ProtoField(number = 7) public String msg;",
                    "  @ProtoField(number = 8) public int tag;",
                    "  @ProtoField(number = 9) public List<String> item;",
                    "  @ProtoField(number = 10) public Map<String, Integer> e;",
                    "  @ProtoField(number = 11) public List<Integer> oldLimit;",
                    "  @ProtoField(number = 12) public State _n;",
                    "  @ProtoField(number = 13) public State _e;",
                    "  @ProtoField(number = 14) public String _c;",
                    "  @ProtoField(number = 15) public String _p;",
                    "  @ProtoField(number = 16) public List<State> _item;",
                    "  @ProtoField(number = 17) public String builder;",
                    "  @ProtoField(number = 18) public String f_size;",
                    "  @ProtoField(number = 19) public String _sz_1;",
                    "  @ProtoField(number = 20) public List<Integer> foo;",
                    "  @ProtoField(number = 21) public List<Integer> Foo;",
                    "  @ProtoField(number = 22) public Map<String, Integer> bar;",
                    "  @ProtoField(number = 23) public Map<String, Integer> Bar;",
                    "  @ProtoField(number = 24) public Addr x;",
                    "  @ProtoField(number = 25) public int xSize;",
                    "  @ProtoField(number = 26) public int xSlot;",
                    "  @ProtoField(number = 27) public int[] arr;",
                    "  @ProtoField(number = 28) public String arrBuilder;",
                    "  @ProtoField(number = 29) public List<Integer> nums;",
                    "  @ProtoField(number = 30) public String numsPacked;",
                    "  @ProtoField(number = 31) public String numsPrim;",
                    "  @ProtoField(number = 32) public LocalDate day;",
                    "  @ProtoField(number = 33) public String dayWire;",
                    "  @ProtoField(number = 34) public State st;",
                    "  @ProtoField(number = 35) public String stNumber;",
                    "  @ProtoField(number = 36) public String k;",
                    "  @ProtoField(number = 37) @ProtoDeterministic public Map<UUID, Integer> detMap;",
                    "  @ProtoField(number = 38) public String xWire;",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 50, of = Wrap.class) }) public Object choice;",
                    "  @ProtoUnknown public UnknownFields _old;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.CollidingGetterPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.util.List;",
                    "import java.util.Map;",
                    "import java.util.Optional;",
                    "@ProtoMessage",
                    "public class CollidingGetterPojo {",
                    "  @ProtoField(number = 1) private List<String> msg;",
                    "  @ProtoField(number = 2) private Map<String, Integer> existing;",
                    "  @ProtoField(number = 3) private List<Integer> oldLimit;",
                    "  @ProtoField(number = 4) private int[] builder;",
                    "  @ProtoField(number = 5) private Optional<Addr> _cur = Optional.empty();",
                    "  @ProtoUnknown private UnknownFields tag;",
                    "  public List<String> getMsg() { return msg; }",
                    "  public void setMsg(List<String> msg) { this.msg = msg; }",
                    "  public Map<String, Integer> getExisting() { return existing; }",
                    "  public void setExisting(Map<String, Integer> existing) { this.existing = existing; }",
                    "  public List<Integer> getOldLimit() { return oldLimit; }",
                    "  public void setOldLimit(List<Integer> oldLimit) { this.oldLimit = oldLimit; }",
                    "  public int[] getBuilder() { return builder; }",
                    "  public void setBuilder(int[] builder) { this.builder = builder; }",
                    "  public Optional<Addr> get_cur() { return _cur; }",
                    "  public void set_cur(Optional<Addr> _cur) { this._cur = _cur; }",
                    "  public UnknownFields getTag() { return tag; }",
                    "  public void setTag(UnknownFields tag) { this.tag = tag; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.CollidingUnknownMsgPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "@ProtoMessage",
                    "public class CollidingUnknownMsgPojo {",
                    "  @ProtoField(number = 1) public String value;",
                    "  @ProtoUnknown public UnknownFields msg;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.CollidingRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.util.List;",
                    "import java.util.Map;",
                    "@ProtoMessage",
                    "public record CollidingRecord(",
                    "  @ProtoField(number = 1) String value,",
                    "  @ProtoField(number = 2) int size,",
                    "  @ProtoField(number = 3) String cache,",
                    "  @ProtoField(number = 4) String writer,",
                    "  @ProtoField(number = 5) String reader,",
                    "  @ProtoField(number = 6) String existing,",
                    "  @ProtoField(number = 7) String msg,",
                    "  @ProtoField(number = 8) int tag,",
                    "  @ProtoField(number = 9) List<String> item,",
                    "  @ProtoField(number = 10) Map<String, Integer> e,",
                    "  @ProtoField(number = 11) List<Integer> oldLimit,",
                    "  @ProtoField(number = 12) String _n,",
                    "  @ProtoField(number = 13) String _e,",
                    "  @ProtoField(number = 14) String _c,",
                    "  @ProtoField(number = 15) String _p,",
                    "  @ProtoField(number = 16) String _item,",
                    "  @ProtoField(number = 17) String builder,",
                    "  @ProtoField(number = 18) int[] x,",
                    "  @ProtoField(number = 19) String xBuilder,",
                    "  @ProtoField(number = 20) List<Integer> foo,",
                    "  @ProtoField(number = 21) List<Integer> Foo,",
                    "  @ProtoUnknown UnknownFields _old,",
                    "  int _i,",
                    "  String _cur) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.CollidingBuilderEntity",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage",
                    "public class CollidingBuilderEntity {",
                    "  @ProtoField(number = 1) private final String builder;",
                    "  @ProtoField(number = 2) private final int size;",
                    "  private CollidingBuilderEntity(String builder, int size) { this.builder = builder; this.size = size; }",
                    "  public String getBuilder() { return builder; }",
                    "  public int getSize() { return size; }",
                    "  public static Builder builder() { return new Builder(); }",
                    "  public static final class Builder {",
                    "    private String builder;",
                    "    private int size;",
                    "    public Builder builder(String builder) { this.builder = builder; return this; }",
                    "    public Builder size(int size) { this.size = size; return this; }",
                    "    public CollidingBuilderEntity build() { return new CollidingBuilderEntity(builder, size); }",
                    "  }",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CollidingPublicPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("packedSizeOf_20");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CollidingPublicPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("packedSizeOf_21");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CollidingPublicPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("sizeOfEntry_22");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CollidingPublicPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("sizeOfEntry_23");
    }

    @Test
    void crossPackageSameSimpleNameEnumsCompileInSameMessage() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "com.a.Status",
                    "package com.a;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum Status { @ProtoEnumValue(0) UNKNOWN, @ProtoEnumValue(1) A_ONE }"),
                JavaFileObjects.forSourceLines(
                    "com.b.Status",
                    "package com.b;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;",
                    "@ProtoEnum public enum Status {",
                    "  @ProtoEnumValue(0) UNKNOWN,",
                    "  @ProtoEnumValue(2) B_TWO,",
                    "  @ProtoUnrecognized UNRECOGNIZED",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "com.a_b.Status",
                    "package com.a_b;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum Status { @ProtoEnumValue(0) ZERO, @ProtoEnumValue(3) THREE }"),
                JavaFileObjects.forSourceLines(
                    "com.a.b_Status",
                    "package com.a;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum b_Status { @ProtoEnumValue(0) ZERO, @ProtoEnumValue(4) FOUR }"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.List;",
                    "import java.util.Map;",
                    "import java.util.Optional;",
                    "@ProtoMessage",
                    "public class Holder {",
                    "  @ProtoField(number = 1) public com.a.Status first;",
                    "  @ProtoField(number = 2) public com.b.Status second;",
                    "  @ProtoField(number = 3) public Optional<com.b.Status> optSecond = Optional.empty();",
                    "  @ProtoField(number = 4) public List<com.a.Status> firstList;",
                    "  @ProtoField(number = 5) public Map<String, com.b.Status> secondMap;",
                    "  @ProtoField(number = 6) public com.a_b.Status third;",
                    "  @ProtoField(number = 7) public com.a.b_Status fourth;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("numberOf_com_a_Status");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("numberOf_com_b_Status");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("numberOf_com_a_0b_Status");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("numberOf_com_a_b_0Status");
    }

    @Test
    void optionalMessageAndOptionalEnumCompileOnPojoAndRecord() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Status",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;",
                    "@ProtoEnum public enum Status {",
                    "  @ProtoEnumValue(0) UNKNOWN,",
                    "  @ProtoEnumValue(1) ACTIVE,",
                    "  @ProtoUnrecognized UNRECOGNIZED",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.Kind",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum Kind { @ProtoEnumValue(0) NONE, @ProtoEnumValue(1) ONE }"),
                JavaFileObjects.forSourceLines(
                    "demo.OptPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.Instant;",
                    "import java.util.Optional;",
                    "@ProtoMessage",
                    "public class OptPojo {",
                    "  @ProtoField(number = 1) public Optional<Addr> addr = Optional.empty();",
                    "  @ProtoField(number = 2) public Optional<Status> status = Optional.empty();",
                    "  @ProtoField(number = 3) public Optional<Kind> kind = Optional.empty();",
                    "  @ProtoField(number = 4) public Optional<Instant> at = Optional.empty();",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.OptRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.time.Instant;",
                    "import java.util.Optional;",
                    "@ProtoMessage",
                    "public record OptRecord(",
                    "  @ProtoField(number = 1) Optional<Addr> addr,",
                    "  @ProtoField(number = 2) Optional<Status> status,",
                    "  @ProtoField(number = 3) Optional<Kind> kind,",
                    "  @ProtoField(number = 4) Optional<Instant> at) {}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OptPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_addr != null && f_addr.isPresent())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OptPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("if (f_status != null && f_status.isPresent())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OptPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("if (_ev_2 != Status.UNRECOGNIZED)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.OptRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("Instant _msg_4 = f_at.get()");
    }

    @Test
    void mapOfByteBufferAndByteArrayArrayCompileOnPojoAndRecord() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.BytesPojo",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.nio.ByteBuffer;",
                    "import java.util.Map;",
                    "@ProtoMessage",
                    "public class BytesPojo {",
                    "  @ProtoField(number = 1) public Map<String, ByteBuffer> buffers;",
                    "  @ProtoField(number = 2) public byte[][] chunks;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.BytesRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.nio.ByteBuffer;",
                    "import java.util.Map;",
                    "@ProtoMessage",
                    "public record BytesRecord(",
                    "  @ProtoField(number = 1) Map<String, ByteBuffer> buffers,",
                    "  @ProtoField(number = 2) byte[][] chunks) {}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BytesPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("ByteBuffer.allocate(0)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BytesPojoProtoCodec")
            .contentsAsUtf8String()
            .contains("toArray(byte[][]::new)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BytesRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("ByteBuffer.allocate(0)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BytesRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("toArray(byte[][]::new)");
    }

    @Test
    void oneofWrapperScalarNullGuardEmittedOnlyForNonPrimitiveScalars() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                localDateAdapter(),
                JavaFileObjects.forSourceLines(
                    "demo.Item",
                    "package demo;",
                    "import java.time.LocalDate;",
                    "public sealed interface Item permits Email, Count, Num, Born, Blob {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Email",
                    "package demo;",
                    "public record Email(String value) implements Item {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Count",
                    "package demo;",
                    "public record Count(int n) implements Item {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Num",
                    "package demo;",
                    "public record Num(Integer n) implements Item {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Born",
                    "package demo;",
                    "import java.time.LocalDate;",
                    "public record Born(LocalDate d) implements Item {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Blob",
                    "package demo;",
                    "public record Blob(byte[] data) implements Item {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage",
                    "public class Holder {",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
                    "    @ProtoOneof.Case(number = 11, of = Count.class),",
                    "    @ProtoOneof.Case(number = 12, of = Num.class),",
                    "    @ProtoOneof.Case(number = 13, of = Born.class, adapter = LocalDateEpochDay.class),",
                    "    @ProtoOneof.Case(number = 14, of = Blob.class)",
                    "  })",
                    "  public Item item;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("oneof case email payload cannot be null");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("oneof case num payload cannot be null");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("oneof case born payload cannot be null");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("oneof case blob payload cannot be null");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("oneof case count payload cannot be null");
    }

    @Test
    void unknownBuilderAndEnumExclusionCodegen() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.OpenEnum",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;",
                    "@ProtoEnum public enum OpenEnum {",
                    "  @ProtoEnumValue(0) ZERO,",
                    "  @ProtoEnumValue(1) ONE,",
                    "  @ProtoUnrecognized UNRECOGNIZED",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.ClosedEnum",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum ClosedEnum {",
                    "  @ProtoEnumValue(0) ZERO,",
                    "  @ProtoEnumValue(1) ONE",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.OpenWrap",
                    "package demo;",
                    "public record OpenWrap(OpenEnum value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.ClosedWrap",
                    "package demo;",
                    "public record ClosedWrap(ClosedEnum value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.EmptyBranch",
                    "package demo;",
                    "public record EmptyBranch() {}"),
                JavaFileObjects.forSourceLines(
                    "demo.PlainUnknown",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.util.List;",
                    "@ProtoMessage public class PlainUnknown {",
                    "  @ProtoField(number = 1) public String name;",
                    "  @ProtoField(number = 2) public List<OpenEnum> flags;",
                    "  @ProtoUnknown public UnknownFields extra;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.RichRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "@ProtoMessage public record RichRecord(",
                    "  @ProtoUnknown UnknownFields extra,",
                    "  @ProtoField(number = 1) OpenEnum status,",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = OpenWrap.class),",
                    "    @ProtoOneof.Case(number = 11, of = ClosedWrap.class),",
                    "    @ProtoOneof.Case(number = 12, of = OpenEnum.class),",
                    "    @ProtoOneof.Case(number = 13, of = ClosedEnum.class),",
                    "    @ProtoOneof.Case(number = 14, of = EmptyBranch.class),",
                    "    @ProtoOneof.Case(number = 15, of = String.class, name = \"label\")",
                    "  }) Object choice) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.ImmutableWithUnknown",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoCreator;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "@ProtoMessage public class ImmutableWithUnknown {",
                    "  @ProtoField(number = 1) private final OpenEnum status;",
                    "  @ProtoUnknown private final UnknownFields extra;",
                    "  private ImmutableWithUnknown(OpenEnum status, UnknownFields extra) {",
                    "    this.status = status; this.extra = extra;",
                    "  }",
                    "  public OpenEnum getStatus() { return status; }",
                    "  public UnknownFields getExtra() { return extra; }",
                    "  @ProtoCreator public static ImmutableWithUnknown of(OpenEnum status, UnknownFields extra) {",
                    "    return new ImmutableWithUnknown(status, extra);",
                    "  }",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PlainUnknownProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("serializedSizeExcluding");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PlainUnknownProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("writeToExcluding");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("UnknownFields.newBuilder(existing != null ? existing.extra() : null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("UnknownFields f_extra = _ub.build()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.removeField(1)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.removeFields(10, 11, 12, 13)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.removeFields(11, 12, 13)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.removeFields(10, 12, 13)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.removeFields(10, 11, 13)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.removeFields(10, 11, 12)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("f_choice = null;");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("serializedSizeExcluding(_excl, _exclN)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.RichRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("writeToExcluding(writer, _excl, _exclN)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ImmutableWithUnknownProtoCodec")
            .contentsAsUtf8String()
            .contains("UnknownFields f_extra = _ub.build()");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ImmutableWithUnknownProtoCodec")
            .contentsAsUtf8String()
            .contains("return ImmutableWithUnknown.of(f_status, f_extra)");
    }

    @Test
    void closedEnumUnknownBranchClearsFieldAndSingleEnumOneofSkipsSelfRemoval() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.ClosedEnum",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "@ProtoEnum public enum ClosedEnum {",
                    "  @ProtoEnumValue(0) ZERO,",
                    "  @ProtoEnumValue(1) ONE",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.ClosedWrap",
                    "package demo;",
                    "public record ClosedWrap(ClosedEnum value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.ClosedHolder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.util.Optional;",
                    "@ProtoMessage public class ClosedHolder {",
                    "  @ProtoField(number = 1) public ClosedEnum plain = ClosedEnum.ZERO;",
                    "  @ProtoField(number = 2) public Optional<ClosedEnum> opt = Optional.empty();",
                    "  @ProtoOneof({",
                    "    @ProtoOneof.Case(number = 10, of = ClosedWrap.class),",
                    "    @ProtoOneof.Case(number = 11, of = String.class, name = \"label\")",
                    "  }) public Object choice;",
                    "  @ProtoUnknown public UnknownFields extra;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ClosedHolderProtoCodec")
            .contentsAsUtf8String()
            .contains("msg.plain = null;");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ClosedHolderProtoCodec")
            .contentsAsUtf8String()
            .contains("msg.opt = Optional.empty();");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ClosedHolderProtoCodec")
            .contentsAsUtf8String()
            .contains("msg.choice = null;");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.ClosedHolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("_ub.removeFields(");
    }

    @Test
    void oneofRecordMergeAndMapUnknownEnumCodegen() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.City",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record City(@ProtoField(number = 1) String name) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.CityWrap",
                    "package demo;",
                    "public record CityWrap(City city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.SingleCaseRecord",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public record SingleCaseRecord(",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 1, of = City.class) }) City direct,",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 2, of = CityWrap.class, name = \"wrappedCity\") }) CityWrap wrapped) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.State",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;",
                    "@ProtoEnum public enum State {",
                    "  @ProtoEnumValue(0) ZERO,",
                    "  @ProtoEnumValue(1) ON,",
                    "  @ProtoUnrecognized UNRECOGNIZED",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.MapHolderWithUnknown",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.util.Map;",
                    "@ProtoMessage public class MapHolderWithUnknown {",
                    "  @ProtoField(number = 1) public Map<String, State> states;",
                    "  @ProtoField(number = 2) public Map<String, City> cities;",
                    "  @ProtoUnknown public UnknownFields extra;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.MapHolderBare",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import java.util.Map;",
                    "@ProtoMessage public class MapHolderBare {",
                    "  @ProtoField(number = 1) public Map<String, State> states;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.SingleCaseRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("reader.readMessage(CityProtoCodec.INSTANCE, f_direct instanceof City _prev ? _prev : null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.SingleCaseRecordProtoCodec")
            .contentsAsUtf8String()
            .contains("new CityWrap(reader.readMessage(CityProtoCodec.INSTANCE, f_wrapped instanceof CityWrap _prev ? _prev.city() : null))");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MapHolderWithUnknownProtoCodec")
            .contentsAsUtf8String()
            .contains("readEntry_1(reader, _col_1, _ub)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MapHolderWithUnknownProtoCodec")
            .contentsAsUtf8String()
            .contains("_ub.mergeRange(reader, TAG_1, _entryStart, reader.position())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MapHolderWithUnknownProtoCodec")
            .contentsAsUtf8String()
            .contains("v = reader.readMessage(CityProtoCodec.INSTANCE, v)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MapHolderBareProtoCodec")
            .contentsAsUtf8String()
            .contains("readEntry_1(reader, _col_1)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.MapHolderBareProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("mergeRange");
    }

    @Test
    void collidingNamesWithUnknownBuilderAndExclusionsCompile() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "demo.State",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnum;",
                    "import io.github.rawvoid.protovia.annotation.ProtoEnumValue;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;",
                    "@ProtoEnum public enum State {",
                    "  @ProtoEnumValue(0) ZERO,",
                    "  @ProtoEnumValue(1) ON,",
                    "  @ProtoUnrecognized UNRECOGNIZED",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "demo.StateWrap",
                    "package demo;",
                    "public record StateWrap(State value) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.CollidingUnknowns",
                    "package demo;",
                    "import io.github.rawvoid.protovia.UnknownFields;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "import io.github.rawvoid.protovia.annotation.ProtoUnknown;",
                    "import java.util.Map;",
                    "@ProtoMessage public class CollidingUnknowns {",
                    "  @ProtoField(number = 1) public State _excl;",
                    "  @ProtoField(number = 2) public int _exclN;",
                    "  @ProtoField(number = 3) public String _prev;",
                    "  @ProtoField(number = 4) public String _unknownEnum;",
                    "  @ProtoField(number = 5) public int _entryStart;",
                    "  @ProtoField(number = 6) public String _uc_10;",
                    "  @ProtoField(number = 7) public Map<String, State> _map;",
                    "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = StateWrap.class) }) public Object _oneof;",
                    "  @ProtoUnknown public UnknownFields _ub;",
                    "}"));
        assertThat(compilation).succeeded();
    }

    private static java.util.List<javax.tools.JavaFileObject> withFieldOneof(
        javax.tools.JavaFileObject... extras) {
        java.util.ArrayList<javax.tools.JavaFileObject> files = new java.util.ArrayList<>();
        files.add(JavaFileObjects.forSourceLines(
            "demo.Target",
            "package demo;",
            "public interface Target {}"));
        files.add(JavaFileObjects.forSourceLines(
            "demo.Email",
            "package demo;",
            "public record Email(String value) implements Target {}"));
        files.add(JavaFileObjects.forSourceLines(
            "demo.Addr",
            "package demo;",
            "import io.github.rawvoid.protovia.annotation.ProtoField;",
            "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
            "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"));
        files.add(JavaFileObjects.forSourceLines(
            "demo.Home",
            "package demo;",
            "public record Home(Addr address) implements Target {}"));
        files.addAll(java.util.List.of(extras));
        return files;
    }

    private static Compilation compileOneofOn(String fieldDecl) {
        return javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Bad",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Bad {",
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class, name = \"text\") })",
                "  public " + fieldDecl + ";",
                "}"));
    }

    private static javax.tools.JavaFileObject localDateAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.LocalDateEpochDay",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "import java.time.LocalDate;",
            "@ProtoScalar(ProtoType.INT32)",
            "public final class LocalDateEpochDay implements ProtoAdapter<LocalDate, Integer> {",
            "  public static final LocalDateEpochDay INSTANCE = new LocalDateEpochDay();",
            "  private LocalDateEpochDay() {}",
            "  public Integer toWire(LocalDate value) { return (int) value.toEpochDay(); }",
            "  public LocalDate fromWire(Integer wire) { return LocalDate.ofEpochDay(wire); }",
            "}");
    }

    private static javax.tools.JavaFileObject localDateAdapterWithoutInstance() {
        return JavaFileObjects.forSourceLines(
            "demo.NoInstance",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "import java.time.LocalDate;",
            "@ProtoScalar(ProtoType.INT32)",
            "public final class NoInstance implements ProtoAdapter<LocalDate, Integer> {",
            "  public Integer toWire(LocalDate value) { return (int) value.toEpochDay(); }",
            "  public LocalDate fromWire(Integer wire) { return LocalDate.ofEpochDay(wire); }",
            "}");
    }

    private static javax.tools.JavaFileObject uuidAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.UuidString",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "import java.util.UUID;",
            "@ProtoScalar(ProtoType.STRING)",
            "public final class UuidString implements ProtoAdapter<UUID, String> {",
            "  public static final UuidString INSTANCE = new UuidString();",
            "  public String toWire(UUID value) { return value.toString(); }",
            "  public UUID fromWire(String wire) { return UUID.fromString(wire); }",
            "}");
    }

    private static javax.tools.JavaFileObject uuidBytesAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.UuidBytes",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "import java.util.UUID;",
            "@ProtoScalar(ProtoType.BYTES)",
            "public final class UuidBytes implements ProtoAdapter<UUID, byte[]> {",
            "  public static final UuidBytes INSTANCE = new UuidBytes();",
            "  public byte[] toWire(UUID value) { return new byte[16]; }",
            "  public UUID fromWire(byte[] wire) { return new UUID(0L, 0L); }",
            "}");
    }

    private static javax.tools.JavaFileObject instantAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.InstantEpochMilli",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "import java.time.Instant;",
            "@ProtoScalar(ProtoType.INT64)",
            "public final class InstantEpochMilli implements ProtoAdapter<Instant, Long> {",
            "  public static final InstantEpochMilli INSTANCE = new InstantEpochMilli();",
            "  public Long toWire(Instant value) { return value.toEpochMilli(); }",
            "  public Instant fromWire(Long wire) { return Instant.ofEpochMilli(wire); }",
            "}");
    }

    private static javax.tools.JavaFileObject localDateIsoAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.LocalDateIso",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "import java.time.LocalDate;",
            "@ProtoScalar(ProtoType.STRING)",
            "public final class LocalDateIso implements ProtoAdapter<LocalDate, String> {",
            "  public static final LocalDateIso INSTANCE = new LocalDateIso();",
            "  public String toWire(LocalDate value) { return value.toString(); }",
            "  public LocalDate fromWire(String wire) { return LocalDate.parse(wire); }",
            "}");
    }

    private static javax.tools.JavaFileObject alwaysWriteIntAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.AlwaysWriteInt",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "@ProtoScalar(ProtoType.INT32)",
            "public final class AlwaysWriteInt implements ProtoAdapter<Integer, Integer> {",
            "  public static final AlwaysWriteInt INSTANCE = new AlwaysWriteInt();",
            "  public Integer toWire(Integer value) { return value; }",
            "  public Integer fromWire(Integer wire) { return wire; }",
            "}");
    }

    private static javax.tools.JavaFileObject moneyType() {
        return JavaFileObjects.forSourceLines(
            "demo.Money",
            "package demo;",
            "import io.github.rawvoid.protovia.annotation.ProtoAdapted;",
            "@ProtoAdapted(MoneyCents.class)",
            "public record Money(long cents) {}");
    }

    private static javax.tools.JavaFileObject moneyClass() {
        return JavaFileObjects.forSourceLines(
            "demo.Money",
            "package demo;",
            "import io.github.rawvoid.protovia.annotation.ProtoAdapted;",
            "@ProtoAdapted(MoneyCents.class)",
            "public class Money {",
            "  public long cents;",
            "}");
    }

    private static javax.tools.JavaFileObject moneyCentsRecordAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.MoneyCents",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "@ProtoScalar(ProtoType.INT64)",
            "public final class MoneyCents implements ProtoAdapter<Money, Long> {",
            "  public static final MoneyCents INSTANCE = new MoneyCents();",
            "  public Long toWire(Money value) { return value.cents(); }",
            "  public Money fromWire(Long wire) { return new Money(wire); }",
            "}");
    }

    private static javax.tools.JavaFileObject moneyCentsClassAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.MoneyCents",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "@ProtoScalar(ProtoType.INT64)",
            "public final class MoneyCents implements ProtoAdapter<Money, Long> {",
            "  public static final MoneyCents INSTANCE = new MoneyCents();",
            "  public Long toWire(Money value) { return value.cents; }",
            "  public Money fromWire(Long wire) { Money m = new Money(); m.cents = wire; return m; }",
            "}");
    }

    private static javax.tools.JavaFileObject addressJsonAdapter() {
        return JavaFileObjects.forSourceLines(
            "demo.AddressJson",
            "package demo;",
            "import io.github.rawvoid.protovia.ProtoType;",
            "import io.github.rawvoid.protovia.annotation.ProtoScalar;",
            "import io.github.rawvoid.protovia.codec.ProtoAdapter;",
            "@ProtoScalar(ProtoType.STRING)",
            "public final class AddressJson implements ProtoAdapter<Address, String> {",
            "  public static final AddressJson INSTANCE = new AddressJson();",
            "  public String toWire(Address value) { return value.city; }",
            "  public Address fromWire(String wire) { Address a = new Address(); a.city = wire; return a; }",
            "}");
    }
}
