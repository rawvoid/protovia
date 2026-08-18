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
        assertThat(compilation).hadErrorContaining("getter and setter");
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
        assertThat(compilation).hadErrorContaining("getter and setter");
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
            .contains("else if (target instanceof Home");
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
    void fieldAndOneofCaseSharingANameStillCompile() {
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
                    "    @ProtoOneof.Case(number = 10, of = Email.class),",
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
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class) }) Object data) {",
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
            .contains("else if (data instanceof Home");
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
            .contains("Target data = existing != null ? existing.data() : null");
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
            .contains("Object data =");
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
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class) })",
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
                "    @ProtoOneof.Case(number = 10, of = String.class),",
                "    @ProtoOneof.Case(number = 11, of = String.class)",
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
            .contains("else if (target instanceof Home");
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
                    "    @ProtoOneof.Case(number = 10, of = String.class),",
                    "    @ProtoOneof.Case(number = 11, of = Address.class)",
                    "  })",
                    "  public Object data;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.BagProtoCodec")
            .contentsAsUtf8String()
            .contains("reader.readMessage(AddressProtoCodec.INSTANCE)");
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
            .contains("reader.readMessage(CityProtoCodec.INSTANCE)");
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
            .contains("else if (target instanceof Home");
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
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = byte[].class) })",
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
            .contains("UnknownFields.merge");
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
            .contains("toArray(new Integer[0])");
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
            .contains("if (birthDate != null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("birthDate != 0");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("int birthDateWire =");
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
            .contains("writeUInt64NoTag(createdWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("TimestampCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.AuditProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("TimestampCodec.INSTANCE.writeTo(writer, created)");
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
            .contains("int dWire = LocalDateEpochDay.INSTANCE.toWire(_c.d())");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("CodedSize.int32(10, dWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeUInt32NoTag(TAG_10)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeInt32NoTag(dWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("new Born(LocalDateEpochDay.INSTANCE.fromWire(reader.readInt32()))");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.HolderProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("if (dWire != 0)");
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
            .contains("LocalDateEpochDay.INSTANCE.toWire(birth)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire(hired)");
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
            .contains("LocalDateEpochDay.INSTANCE.toWire(birth)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateIso.INSTANCE.toWire(hired)");
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
            .contains("InstantEpochMilli.INSTANCE.toWire(created)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.toWire(updated)");
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
            .contains("if (count != null)");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.CounterProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("count != 0");
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
            .contains("if (total != null)");
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
                "  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class) })",
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
