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
class ReservedProcessorTest {

    @Test
    void unusedReservedCompiles() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(numbers = {4, 5}, names = "legacy_tag")
            @ProtoReserved(ranges = @ProtoReserved.Range(from = 10, to = 12))
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("demo.internal.UserProtoCodec");
    }

    @Test
    void overlappingRepeatableReservedIsIdempotent() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(numbers = 11, ranges = @ProtoReserved.Range(from = 10, to = 12))
            @ProtoReserved(numbers = 11)
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).succeeded();
    }

    @Test
    void emptyReservedIsNoop() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).succeeded();
    }

    @Test
    void recordWithUnusedReservedCompiles() {
        Compilation compilation = compile(src("demo.City", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(numbers = 9)
            public record City(@ProtoField(number = 1) String name) {}
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("demo.internal.CityProtoCodec");
    }

    @Test
    void enumUnusedReservedCompiles() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoEnum
            @ProtoReserved(numbers = 2, names = "BANNED")
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN,
              @ProtoEnumValue(1) ACTIVE
            }
            """));
        assertThat(compilation).succeeded();
    }

    @Test
    void noReservedEntityStillCompiles() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("demo.internal.UserProtoCodec");
    }

    @Test
    void reservedFieldNumberFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(numbers = 4)
            public class User {
              @ProtoField(number = 4) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("field number 4 is reserved");
    }

    @Test
    void reservedRangeNumberFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(ranges = @ProtoReserved.Range(from = 10, to = 12))
            public class User {
              @ProtoField(number = 11) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("field number 11 is reserved");
    }

    @Test
    void reservedFieldNameFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(names = "legacy_tag")
            public class User {
              @ProtoField(number = 1) public String legacy_tag;
            }
            """));
        assertThat(compilation).hadErrorContaining("proto name 'legacy_tag' is reserved");
    }

    @Test
    void reservedOneofCaseNumberFails() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Contact", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                import io.github.rawvoid.protovia.annotation.ProtoReserved;
                @ProtoMessage
                @ProtoReserved(numbers = 10)
                public class Contact {
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object target;
                }
                """));
        assertThat(compilation).hadErrorContaining("field number 10 is reserved");
    }

    @Test
    void reservedOneofGroupNameFails() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Contact", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                import io.github.rawvoid.protovia.annotation.ProtoReserved;
                @ProtoMessage
                @ProtoReserved(names = "target")
                public class Contact {
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object target;
                }
                """));
        assertThat(compilation).hadErrorContaining("proto name 'target' is reserved");
    }

    @Test
    void reservedOneofCaseDefaultNameFails() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Contact", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                import io.github.rawvoid.protovia.annotation.ProtoReserved;
                @ProtoMessage
                @ProtoReserved(names = "email")
                public class Contact {
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object target;
                }
                """));
        assertThat(compilation).hadErrorContaining("proto name 'email' is reserved");
    }

    @Test
    void reservedNakedScalarDefaultNameFails() {
        Compilation compilation = compile(src("demo.Box", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoOneof;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(names = "string")
            public class Box {
              @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class) })
              public Object data;
            }
            """));
        assertThat(compilation).hadErrorContaining("proto name 'string' is reserved");
    }

    @Test
    void reservedOneofMessageComponentNameFails() {
        Compilation compilation = compile(
            src("demo.Addr", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                @ProtoMessage public record Addr(@ProtoField(number = 1) String city) {}
                """),
            src("demo.Home", """
                package demo;
                public record Home(Addr address) {}
                """),
            src("demo.Contact", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                import io.github.rawvoid.protovia.annotation.ProtoReserved;
                @ProtoMessage
                @ProtoReserved(names = "address")
                public class Contact {
                  @ProtoOneof({ @ProtoOneof.Case(number = 11, of = Home.class) })
                  public Object target;
                }
                """));
        assertThat(compilation).hadErrorContaining("proto name 'address' is reserved");
    }

    @Test
    void reservedEnumNumberFails() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoEnum
            @ProtoReserved(numbers = 2)
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN,
              @ProtoEnumValue(1) ACTIVE,
              @ProtoEnumValue(2) BANNED
            }
            """));
        assertThat(compilation).hadErrorContaining("enum number 2 is reserved");
    }

    @Test
    void reservedEnumNameFails() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoEnum
            @ProtoReserved(names = "BANNED")
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN,
              @ProtoEnumValue(1) BANNED
            }
            """));
        assertThat(compilation).hadErrorContaining("proto name 'BANNED' is reserved");
    }

    @Test
    void reservedEnumZeroFails() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoEnum
            @ProtoReserved(numbers = 0)
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN
            }
            """));
        assertThat(compilation).hadErrorContaining("enum number 0 is reserved");
    }

    @Test
    void unrecognizedDoesNotClashWithReservedName() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;
            @ProtoEnum
            @ProtoReserved(names = "UNRECOGNIZED")
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN,
              @ProtoUnrecognized UNRECOGNIZED
            }
            """));
        assertThat(compilation).succeeded();
    }

    @Test
    void fromGreaterThanToFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(ranges = @ProtoReserved.Range(from = 12, to = 10))
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("invalid reserved range");
    }

    @Test
    void reservedMessageNumberZeroFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(numbers = 0)
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("invalid reserved field number 0");
    }

    @Test
    void reservedProtobufInternalNumberFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(numbers = 19000)
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("invalid reserved field number 19000");
    }

    @Test
    void rangeCrossingProtobufInternalFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(ranges = @ProtoReserved.Range(from = 18999, to = 20000))
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("invalid reserved range");
    }

    @Test
    void illegalReservedNameFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(names = "legacy-tag")
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("is not a proto identifier");
    }

    @Test
    void reservedKeywordNameIsAllowed() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(names = "string")
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).succeeded();
    }

    private static Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ProtoviaProcessor()).compile(files);
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }
}
