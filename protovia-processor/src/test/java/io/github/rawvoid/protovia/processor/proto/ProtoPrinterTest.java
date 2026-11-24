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

package io.github.rawvoid.protovia.processor.proto;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Rawvoid
 */
class ProtoPrinterTest {

    @Test
    void qualifyKeepsSimpleNameForSamePackageAndPrefixesLeadingDotOtherwise() {
        assertEquals("Address", ProtoNames.qualify("demo.Address", "demo"));
        assertEquals("Address", ProtoNames.qualify("Address", ""));
        assertEquals("Address", ProtoNames.qualify("Address", null));

        assertEquals(".other.pkg.Foo", ProtoNames.qualify("other.pkg.Foo", "org.acme.google.api"));
        assertEquals(".other.pkg.Foo", ProtoNames.qualify("other.pkg.Foo", ""));
        assertEquals(".other.pkg.Foo", ProtoNames.qualify("other.pkg.Foo", null));
        assertEquals(".RootMsg", ProtoNames.qualify("RootMsg", "demo"));

        assertEquals(".google.protobuf.Timestamp",
            ProtoNames.qualify("google.protobuf.Timestamp", "org.acme.google.api"));
        assertEquals(".google.protobuf.Timestamp",
            ProtoNames.qualify("google.protobuf.Timestamp", "google.protobuf"));
        assertEquals(".google.protobuf.Duration",
            ProtoNames.qualify("google.protobuf.Duration", ""));
    }

    @Test
    void printEnumEmitsZeroConstantFirstAndPreservesOtherOrder() {
        EnumModel reordered = EnumModel.builder()
            .protoPackage("demo")
            .protoEnumName("Status")
            .constant(new EnumModel.Constant("ACTIVE", 1))
            .constant(new EnumModel.Constant("PENDING", 2))
            .constant(new EnumModel.Constant("UNKNOWN", 0))
            .constant(new EnumModel.Constant("CLOSED", 3))
            .build();
        assertEquals("""
            syntax = "proto3";

            package demo;

            enum Status {
              STATUS_UNKNOWN = 0;
              STATUS_ACTIVE = 1;
              STATUS_PENDING = 2;
              STATUS_CLOSED = 3;
            }
            """, ProtoPrinter.print(reordered));

        EnumModel alreadyFirst = EnumModel.builder()
            .protoPackage("demo")
            .protoEnumName("Status")
            .constant(new EnumModel.Constant("UNKNOWN", 0))
            .constant(new EnumModel.Constant("ACTIVE", 1))
            .constant(new EnumModel.Constant("CLOSED", 2))
            .build();
        assertEquals("""
            syntax = "proto3";

            package demo;

            enum Status {
              STATUS_UNKNOWN = 0;
              STATUS_ACTIVE = 1;
              STATUS_CLOSED = 2;
            }
            """, ProtoPrinter.print(alreadyFirst));
    }

    @Test
    void printMessageOmitsOptionalOnJavaOptionalMessageFieldsOnly() {
        EnumModel statusEnum = EnumModel.builder()
            .protoPackage("demo")
            .protoEnumName("Status")
            .constant(new EnumModel.Constant("UNKNOWN", 0))
            .build();
        MessageModel model = MessageModel.builder()
            .protoPackage("demo")
            .protoMessageName("Holder")
            .field(FieldModel.builder()
                .number(1)
                .name("javaOptMsg")
                .kind(FieldKind.MESSAGE)
                .protoType(ProtoType.MESSAGE)
                .codecName("io.github.rawvoid.protovia.wkt.TimestampCodec")
                .javaOptional(true)
                .optional(false)
                .build())
            .field(FieldModel.builder()
                .number(2)
                .name("bothOptMsg")
                .kind(FieldKind.MESSAGE)
                .protoType(ProtoType.MESSAGE)
                .codecName("io.github.rawvoid.protovia.wkt.DurationCodec")
                .javaOptional(true)
                .optional(true)
                .build())
            .field(FieldModel.builder()
                .number(3)
                .name("javaOptEnum")
                .kind(FieldKind.ENUM)
                .protoType(ProtoType.ENUM)
                .enumModel(statusEnum)
                .javaOptional(true)
                .optional(true)
                .build())
            .field(FieldModel.builder()
                .number(4)
                .name("javaOptScalar")
                .kind(FieldKind.SCALAR)
                .protoType(ProtoType.INT32)
                .javaOptional(true)
                .optional(true)
                .build())
            .build();
        assertEquals("""
            syntax = "proto3";

            package demo;

            import "demo/status.proto";
            import "google/protobuf/duration.proto";
            import "google/protobuf/timestamp.proto";

            message Holder {
              .google.protobuf.Timestamp javaOptMsg = 1;
              .google.protobuf.Duration bothOptMsg = 2;
              optional Status javaOptEnum = 3;
              optional int32 javaOptScalar = 4;
            }
            """, ProtoPrinter.print(model));
    }
}
