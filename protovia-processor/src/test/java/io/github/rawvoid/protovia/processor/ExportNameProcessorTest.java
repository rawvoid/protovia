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
class ExportNameProcessorTest {

    @Test
    void fieldNameOverrideCompilesAndKeepsJavaHelperNames() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import java.util.List;
            @ProtoMessage
            public class User {
              @ProtoField(number = 1, name = "the_tags") public List<Integer> tags;
            }
            """));
        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .contains("packedSizeOfTags");
        assertThat(compilation)
            .generatedSourceFile("demo.internal.UserProtoCodec")
            .contentsAsUtf8String()
            .doesNotContain("packedSizeOfThe_tags");
    }

    @Test
    void illegalIdentFails() {
        Compilation dash = compile(userField("name = \"has-dash\""));
        assertThat(dash).hadErrorContaining("is not a proto identifier");
        Compilation leadingDigit = compile(userField("name = \"1abc\""));
        assertThat(leadingDigit).hadErrorContaining("is not a proto identifier");
    }

    @Test
    void keywordNameFails() {
        Compilation compilation = compile(userField("name = \"string\""));
        assertThat(compilation).hadErrorContaining("is a proto keyword");
    }

    @Test
    void nakedStringDefaultNameFails() {
        Compilation compilation = compile(src("demo.Box", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoOneof;
            @ProtoMessage
            public class Box {
              @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class) })
              public Object data;
            }
            """));
        assertThat(compilation).hadErrorContaining("is a proto keyword");
    }

    @Test
    void nakedBytesDefaultNameFails() {
        Compilation compilation = compile(src("demo.Box", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoOneof;
            @ProtoMessage
            public class Box {
              @ProtoOneof({ @ProtoOneof.Case(number = 10, of = byte[].class) })
              public Object data;
            }
            """));
        assertThat(compilation).hadErrorContaining("is a proto keyword");
    }

    @Test
    void nakedStringWithNameCompiles() {
        Compilation compilation = compile(src("demo.Box", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoOneof;
            @ProtoMessage
            public class Box {
              @ProtoOneof({ @ProtoOneof.Case(number = 10, of = String.class, name = "email") })
              public Object data;
            }
            """));
        assertThat(compilation).succeeded();
    }

    @Test
    void duplicateProtoNamesFail() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class User {
              @ProtoField(number = 1, name = "display_name") public String a;
              @ProtoField(number = 2, name = "display_name") public String b;
            }
            """));
        assertThat(compilation).hadErrorContaining("duplicate proto field name 'display_name'");
    }

    @Test
    void fieldAndOneofCaseNameClash() {
        Compilation compilation = compile(
            src("demo.Email", """
                package demo;
                public record Email(String value) {}
                """),
            src("demo.Contact", """
                package demo;
                import io.github.rawvoid.protovia.annotation.ProtoField;
                import io.github.rawvoid.protovia.annotation.ProtoMessage;
                import io.github.rawvoid.protovia.annotation.ProtoOneof;
                @ProtoMessage
                public class Contact {
                  @ProtoField(number = 1) public String email;
                  @ProtoOneof({ @ProtoOneof.Case(number = 10, of = Email.class) })
                  public Object target;
                }
                """));
        assertThat(compilation).hadErrorContaining("duplicate proto field name 'email'");
    }

    @Test
    void reservedChecksOverrideNameNotJavaName() {
        Compilation reservedOverride = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(names = "legacy_tag")
            public class User {
              @ProtoField(number = 1, name = "legacy_tag") public String displayName;
            }
            """));
        assertThat(reservedOverride).hadErrorContaining("proto name 'legacy_tag' is reserved");

        Compilation javaNameNotReserved = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            import io.github.rawvoid.protovia.annotation.ProtoReserved;
            @ProtoMessage
            @ProtoReserved(names = "displayName")
            public class User {
              @ProtoField(number = 1, name = "display_name") public String displayName;
            }
            """));
        assertThat(javaNameNotReserved).succeeded();
    }

    @Test
    void oneofOverrideNamesAndReserved() {
        Compilation reservedGroup = compile(
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
                @ProtoReserved(names = "payload")
                public class Contact {
                  @ProtoOneof(name = "payload", value = {
                    @ProtoOneof.Case(number = 10, of = Email.class)
                  })
                  public Object target;
                }
                """));
        assertThat(reservedGroup).hadErrorContaining("proto name 'payload' is reserved");

        Compilation reservedCase = compile(
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
                @ProtoReserved(names = "mail")
                public class Contact {
                  @ProtoOneof({
                    @ProtoOneof.Case(number = 10, of = Email.class, name = "mail")
                  })
                  public Object target;
                }
                """));
        assertThat(reservedCase).hadErrorContaining("proto name 'mail' is reserved");

        Compilation javaGroupNotReserved = compile(
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
                  @ProtoOneof(name = "choice", value = {
                    @ProtoOneof.Case(number = 10, of = Email.class, name = "mail")
                  })
                  public Object target;
                }
                """));
        assertThat(javaGroupNotReserved).succeeded();
    }

    @Test
    void camelCaseEnumConstantFails() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            @ProtoEnum
            public enum Status {
              @ProtoEnumValue(0) Unknown,
              @ProtoEnumValue(1) ACTIVE
            }
            """));
        assertThat(compilation).hadErrorContaining("enum constant Unknown must be UPPER_SNAKE_CASE");
    }

    @Test
    void lowerCaseEnumConstantFails() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            @ProtoEnum
            public enum Status {
              @ProtoEnumValue(0) unknown
            }
            """));
        assertThat(compilation).hadErrorContaining("enum constant unknown must be UPPER_SNAKE_CASE");
    }

    @Test
    void unrecognizedSentinelNeedNotBeUpperSnake() {
        Compilation compilation = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;
            @ProtoEnum
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN,
              @ProtoUnrecognized Unrecognized
            }
            """));
        assertThat(compilation).succeeded();
    }

    @Test
    void enumPackageNameCompiles() {
        Compilation withPackage = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            @ProtoEnum(name = "Status", packageName = "example.v1")
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN,
              @ProtoEnumValue(1) ACTIVE
            }
            """));
        assertThat(withPackage).succeeded();

        Compilation missingPackage = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            @ProtoEnum
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN
            }
            """));
        assertThat(missingPackage).succeeded();
    }

    @Test
    void illegalPackageNameFails() {
        Compilation message = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage(packageName = "example..v1")
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(message).hadErrorContaining("invalid proto package name");

        Compilation enm = compile(src("demo.Status", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoEnum;
            import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
            @ProtoEnum(packageName = "1example")
            public enum Status {
              @ProtoEnumValue(0) UNKNOWN
            }
            """));
        assertThat(enm).hadErrorContaining("invalid proto package name");
    }

    @Test
    void illegalTypeNameFails() {
        Compilation compilation = compile(src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage(name = "message")
            public class User {
              @ProtoField(number = 1) public String name;
            }
            """));
        assertThat(compilation).hadErrorContaining("is a proto keyword");
    }

    @Test
    void javaKeywordMemberMustOverride() {
        Compilation compilation = compile(src("demo.Box", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class Box {
              @ProtoField(number = 1) public String message;
            }
            """));
        assertThat(compilation).hadErrorContaining("is a proto keyword");
    }

    private static JavaFileObject userField(String nameAttr) {
        return src("demo.User", """
            package demo;
            import io.github.rawvoid.protovia.annotation.ProtoField;
            import io.github.rawvoid.protovia.annotation.ProtoMessage;
            @ProtoMessage
            public class User {
              @ProtoField(number = 1, %s) public String name;
            }
            """.formatted(nameAttr));
    }

    private static Compilation compile(JavaFileObject... files) {
        return javac().withProcessors(new ProtoviaProcessor()).compile(files);
    }

    private static JavaFileObject src(String fqcn, String source) {
        return JavaFileObjects.forSourceString(fqcn, source);
    }
}
