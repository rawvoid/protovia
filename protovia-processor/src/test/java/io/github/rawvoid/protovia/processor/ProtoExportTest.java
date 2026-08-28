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
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Rawvoid
 */
class ProtoExportTest {

    @Test
    void exportsMessageEnumMapOptionalAndUnpacked() {
        Compilation compilation = compile(
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
            src("demo.Address", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public record Address(@ProtoField(number = 1) String city) {}
                """),
            src("demo.User", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.List;
                import java.util.Map;
                @ProtoMessage
                public class User {
                  @ProtoField(number = 1) public String name;
                  @ProtoField(number = 2) public int age;
                  @ProtoField(number = 4) public List<String> tags;
                  @ProtoField(number = 5) public Address address;
                  @ProtoField(number = 6) public Map<String, Integer> scores;
                  @ProtoField(number = 7) public Status status;
                  @ProtoField(number = 8, optional = true) public Integer level;
                  @ProtoField(number = 10, packed = false) public List<Integer> unpacked;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            import "address.proto";
            import "status.proto";

            message User {
              string name = 1;
              int32 age = 2;
              repeated string tags = 4;
              Address address = 5;
              map<string, int32> scores = 6;
              Status status = 7;
              optional int32 level = 8;
              repeated int32 unpacked = 10 [packed = false];
            }
            """, proto(compilation, "user.proto"));
        assertEquals("""
            syntax = "proto3";

            enum Status {
              STATUS_UNKNOWN = 0;
              STATUS_ACTIVE = 1;
            }
            """, proto(compilation, "status.proto"));
    }

    @Test
    void prefixesEnumConstantsWithTypeName() {
        Compilation compilation = compile(
            src("demo.ErrorCategory", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoEnum;
                import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                @ProtoEnum
                public enum ErrorCategory {
                  @ProtoEnumValue(0) SYSTEM,
                  @ProtoEnumValue(5) SEAT
                }
                """),
            src("demo.AncillaryCategory", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoEnum;
                import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                @ProtoEnum
                public enum AncillaryCategory {
                  @ProtoEnumValue(0) BAGGAGE,
                  @ProtoEnumValue(1) SEAT
                }
                """),
            src("demo.Cabin", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoEnum;
                import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
                @ProtoEnum(name = "CabinClass")
                public enum Cabin {
                  @ProtoEnumValue(0) FIRST,
                  @ProtoEnumValue(1) BUSINESS
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            enum ErrorCategory {
              ERROR_CATEGORY_SYSTEM = 0;
              ERROR_CATEGORY_SEAT = 5;
            }
            """, proto(compilation, "errorcategory.proto"));
        assertEquals("""
            syntax = "proto3";

            enum AncillaryCategory {
              ANCILLARY_CATEGORY_BAGGAGE = 0;
              ANCILLARY_CATEGORY_SEAT = 1;
            }
            """, proto(compilation, "ancillarycategory.proto"));
        assertEquals("""
            syntax = "proto3";

            enum CabinClass {
              CABIN_CLASS_FIRST = 0;
              CABIN_CLASS_BUSINESS = 1;
            }
            """, proto(compilation, "cabinclass.proto"));
    }

    @Test
    void flattensOneofWrappersNotJavaTypes() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Address", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public record Address(@ProtoField(number = 1) String city) {}
                """),
            src("demo.Home", """
                package demo;
                public record Home(Address address) {}
                """),
            src("demo.Contact", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                @ProtoMessage
                public class Contact {
                  @ProtoField(number = 1) public String name;
                  @ProtoOneof({
                    @ProtoOneof.Case(number = 10, of = Email.class),
                    @ProtoOneof.Case(number = 11, of = Home.class)
                  })
                  public Object target;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            import "address.proto";

            message Contact {
              string name = 1;
              oneof target {
                string email = 10;
                Address address = 11;
              }
            }
            """, proto(compilation, "contact.proto"));
    }

    @Test
    void selfMessageOneofKeepsMessageType() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public record Email(@ProtoField(number = 1) String text) {}
                """),
            src("demo.Box", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                @ProtoMessage
                public class Box {
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object data;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            import "email.proto";

            message Box {
              oneof data {
                Email email = 10;
              }
            }
            """, proto(compilation, "box.proto"));
    }

    @Test
    void nestsEmptyPlaceholderAndRejectsKeywordTypeName() {
        Compilation ok = compile(
            src("demo.Ping", """
                package demo;
                public record Ping() {}
                """),
            src("demo.Box", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                @ProtoMessage
                public class Box {
                  @ProtoOneof({ @ProtoOneof.Case(number = 20, of = Ping.class) })
                  public Object event;
                }
                """));
        assertThat(ok).succeeded();
        assertEquals("""
            syntax = "proto3";

            message Box {
              message Ping {
              }
              oneof event {
                Ping ping = 20;
              }
            }
            """, proto(ok, "box.proto"));

        Compilation bad = compile(
            src("demo.string", """
                package demo;
                public record string() {}
                """),
            src("demo.Box", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                @ProtoMessage
                public class Box {
                  @ProtoOneof({ @ProtoOneof.Case(number = 20, of = string.class, name = "ping") })
                  public Object event;
                }
                """));
        assertThat(bad).hadErrorContaining("empty oneof case type 'string' is a proto keyword; rename the Java type");
    }

    @Test
    void exportsReservedWktAdaptersAndPackage() {
        Compilation compilation = compile(
            src("demo.Carrier", """
                package demo;
                import io.github.rawvoid.protovia.ProtoAny;
                import io.github.rawvoid.protovia.adapter.LocalDateEpochDayAdapter;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoReserved;
                import io.github.rawvoid.protovia.wkt.Int32Value;
                import java.time.Instant;
                import java.time.LocalDate;
                @ProtoMessage(name = "Carrier", packageName = "example.v1")
                @ProtoReserved(numbers = {4, 5}, names = "legacy_tag")
                @ProtoReserved(ranges = @ProtoReserved.Range(from = 10, to = 12))
                public class Carrier {
                  @ProtoField(number = 1) public String name;
                  @ProtoField(number = 2) public ProtoAny extra;
                  @ProtoField(number = 3) public Int32Value count;
                  @ProtoField(number = 6) public Instant at;
                  @ProtoField(number = 7, adapter = LocalDateEpochDayAdapter.class) public LocalDate born;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            package example.v1;

            import "google/protobuf/any.proto";
            import "google/protobuf/timestamp.proto";
            import "google/protobuf/wrappers.proto";

            message Carrier {
              reserved 4, 5, 10 to 12;
              reserved "legacy_tag";
              string name = 1;
              google.protobuf.Any extra = 2;
              google.protobuf.Int32Value count = 3;
              google.protobuf.Timestamp at = 6;
              int32 born = 7;
            }
            """, proto(compilation, "example/v1/carrier.proto"));
    }

    @Test
    void leafProtoIncludesFlattenedSuperFields() {
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
                  @ProtoField(number = 16) public String name;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            message User {
              string id = 1;
              string name = 16;
            }
            """, proto(compilation, "user.proto"));
    }

    @Test
    void repeatedMessageImportsPeerFile() {
        Compilation compilation = compile(
            src("demo.Address", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public record Address(@ProtoField(number = 1) String city) {}
                """),
            src("demo.Book", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import java.util.List;
                @ProtoMessage
                public class Book {
                  @ProtoField(number = 1) public List<Address> places;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            import "address.proto";

            message Book {
              repeated Address places = 1;
            }
            """, proto(compilation, "book.proto"));
    }

    @Test
    void selfRecursiveMessageDoesNotImportItself() {
        Compilation compilation = compile(
            src("demo.Node", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage
                public class Node {
                  @ProtoField(number = 1) public Node next;
                }
                """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            message Node {
              Node next = 1;
            }
            """, proto(compilation, "node.proto"));
    }

    @Test
    void crossPackageUsesQualifiedName(@TempDir Path protoOut) {
        Compilation compilation = javac()
            .withProcessors(new ProtoviaProcessor())
            .withOptions("-Aprotovia.protoOut=" + protoOut)
            .compile(
                src("other.Addr", """
                    package other;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    @ProtoMessage(packageName = "other.v1", name = "Addr")
                    public record Addr(@ProtoField(number = 1) String city) {}
                    """),
                src("demo.Wrap", """
                    package demo;
                    import io.github.rawvoid.protovia.annotation.ProtoField;
                    import io.github.rawvoid.protovia.annotation.ProtoMessage;
                    import other.Addr;
                    @ProtoMessage(packageName = "example.v1")
                    public class Wrap {
                      @ProtoField(number = 1) public Addr addr;
                    }
                    """));
        assertThat(compilation).succeeded();
        assertEquals("""
            syntax = "proto3";

            package example.v1;

            import "other/v1/addr.proto";

            message Wrap {
              other.v1.Addr addr = 1;
            }
            """, proto(compilation, "example/v1/wrap.proto"));
        assertEquals(
            proto(compilation, "example/v1/wrap.proto"),
            Files.exists(protoOut.resolve("example/v1/wrap.proto"))
                ? read(protoOut.resolve("example/v1/wrap.proto"))
                : "missing");
    }

    private static String proto(Compilation compilation, String path) {
        JavaFileObject file = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, path)
            .orElseThrow(() -> new AssertionError("missing " + path + ": " + compilation.generatedFiles()));
        try {
            return file.getCharContent(true).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ProtoviaProcessor()).compile(files);
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }
}
