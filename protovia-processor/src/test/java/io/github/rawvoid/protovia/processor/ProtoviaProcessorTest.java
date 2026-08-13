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
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(10) public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(11) public record Home(Addr address) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoField(number = 1) public String name;",
                    "  @ProtoOneof public Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("else if (target instanceof Home");
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
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(10) public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Addr",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(11) public record Home(Addr address) implements Target {}"),
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
                    "  @ProtoOneof public Target getTarget() { return target; }",
                    "  public void setTarget(Target target) { this.target = target; }",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.ContactProtoCodec")
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
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(10) public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Home",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(11) public record Home(String city) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Mail",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Mail {",
                    "  @ProtoField(number = 1) public String email;",
                    "  @ProtoOneof public Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.MailProtoCodec")
            .contentsAsUtf8String()
            .contains("TAG_1");
        assertThat(compilation)
            .generatedSourceFile("demo.MailProtoCodec")
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
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(10) public record Email(String value) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "other.Addr",
                    "package other;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "@ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}"),
                JavaFileObjects.forSourceLines(
                    "other.Home",
                    "package other;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(11) public record Home(Addr address) implements Target {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Contact",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoField;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Contact {",
                    "  @ProtoField(number = 1) public String name;",
                    "  @ProtoOneof public other.Target target;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.ContactProtoCodec")
            .contentsAsUtf8String()
            .contains("import other.AddrProtoCodec");
        assertThat(compilation)
            .generatedSourceFile("demo.ContactProtoCodec")
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
    void oneofRejectsNonSealed() {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .compile(JavaFileObjects.forSourceLines(
                "demo.Bad",
                "package demo;",
                "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                "@ProtoMessage public class Bad { @ProtoOneof public String target; }"));
        assertThat(compilation).hadErrorContaining("sealed");
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
            .generatedSourceFile("demo.EnvProtoCodec")
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
            .generatedSourceFile("demo.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("return \"example.v1.Box\";");
        assertThat(compilation)
            .generatedSourceFile("demo.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("AnyCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("Int32Value.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.BoxProtoCodec")
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
            .generatedSourceFile("demo.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("import io.github.rawvoid.protovia.collect.IntArrayList");
        assertThat(compilation)
            .generatedSourceFile("demo.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("instanceof IntArrayList");
        assertThat(compilation)
            .generatedSourceFile("demo.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("IntArrayList::new");
        assertThat(compilation)
            .generatedSourceFile("demo.NumsProtoCodec")
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
            .generatedSourceFile("demo.NumsProtoCodec")
            .contentsAsUtf8String()
            .contains("toIntArray()");
        assertThat(compilation)
            .generatedSourceFile("demo.NumsProtoCodec")
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
    void adapterOnRepeatedFailsWithE23() {
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
        assertThat(compilation).hadErrorContaining("adapters on repeated/map/oneof are not enabled yet");
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
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("if (birthDate != null)");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("birthDate != 0");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
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
            .generatedSourceFile("demo.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.EventProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.EventProtoCodec")
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
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("isPresent()");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("LocalDateEpochDay.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
            .contentsAsUtf8String()
            .contains("birthDate.get()");
        assertThat(compilation)
            .generatedSourceFile("demo.PersonProtoCodec")
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
            .generatedSourceFile("demo.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.toWire");
        assertThat(compilation)
            .generatedSourceFile("demo.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("InstantEpochMilli.INSTANCE.fromWire");
        assertThat(compilation)
            .generatedSourceFile("demo.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("writeUInt64NoTag(createdWire)");
        assertThat(compilation)
            .generatedSourceFile("demo.AuditProtoCodec")
            .contentsAsUtf8String()
            .contains("TimestampCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.AuditProtoCodec")
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
                    "import io.github.rawvoid.protovia.ProtoType;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(value = 10, type = ProtoType.SINT32)",
                    "public record Count(int n) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Label",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneofCase;",
                    "@ProtoOneofCase(11) public record Label(String s) implements Event {}"),
                JavaFileObjects.forSourceLines(
                    "demo.Holder",
                    "package demo;",
                    "import io.github.rawvoid.protovia.annotation.ProtoMessage;",
                    "import io.github.rawvoid.protovia.annotation.ProtoOneof;",
                    "@ProtoMessage public class Holder {",
                    "  @ProtoOneof public Event event;",
                    "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("writeSInt32NoTag");
        assertThat(compilation)
            .generatedSourceFile("demo.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("CodedSize.sint32");
        assertThat(compilation)
            .generatedSourceFile("demo.HolderProtoCodec")
            .contentsAsUtf8String()
            .contains("readSInt32()");
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
}
