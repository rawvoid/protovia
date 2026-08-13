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
            .contains("other.AddrProtoCodec.INSTANCE");
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
            .contains("io.github.rawvoid.protovia.wkt.AnyCodec.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("io.github.rawvoid.protovia.wkt.Int32Value.INSTANCE");
        assertThat(compilation)
            .generatedSourceFile("demo.BoxProtoCodec")
            .contentsAsUtf8String()
            .contains("io.github.rawvoid.protovia.wkt.TimestampCodec.INSTANCE");
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
            .contains("instanceof io.github.rawvoid.protovia.collect.IntArrayList");
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
            .contains("toArray(new java.lang.Integer[0])");
    }
}
