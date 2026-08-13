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

package io.github.rawvoid.protovia.processor.gen;

import com.palantir.javapoet.CodeBlock;
import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;
import io.github.rawvoid.protovia.wire.WireType;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.enumConstant;

/**
 * Wire-format metadata and presence predicates for generated codecs.
 *
 * @author Rawvoid
 */
final class WireTypes {

    private WireTypes() {
    }

    static int oneofWire(OneofCaseModel c) {
        if (c.empty() || c.selfMessage) {
            return WireType.LEN;
        }
        return unpackedWire(c.payload);
    }

    static int unpackedWire(FieldModel field) {
        if (field.kind == FieldKind.MAP || field.kind == FieldKind.MESSAGE) {
            return WireType.LEN;
        }
        FieldModel target = field.kind == FieldKind.REPEATED ? field.element : field;
        if (target == null || target.kind == FieldKind.MESSAGE || target.protoType == null
            || target.protoType == ProtoType.STRING || target.protoType == ProtoType.BYTES) {
            return WireType.LEN;
        }
        return switch (target.protoType) {
            case FIXED32, SFIXED32, FLOAT -> WireType.I32;
            case FIXED64, SFIXED64, DOUBLE -> WireType.I64;
            default -> WireType.VARINT;
        };
    }

    static int packedFixedWidth(FieldModel element) {
        if (element.kind != FieldKind.SCALAR || element.protoType == null) {
            return 0;
        }
        return switch (element.protoType) {
            case BOOL -> 1;
            case FIXED32, SFIXED32, FLOAT -> 4;
            case FIXED64, SFIXED64, DOUBLE -> 8;
            default -> 0;
        };
    }

    static CodeBlock presentCondition(FieldModel field, String var, boolean optional, boolean javaOptional) {
        if (javaOptional) {
            return CodeBlock.of("$L != null && $L.isPresent()", var, var);
        }
        if (optional) {
            return CodeBlock.of("$L != null", var);
        }
        if (field.primitive) {
            return switch (field.protoType) {
                case BOOL -> CodeBlock.of("$L", var);
                case FLOAT -> CodeBlock.of("Float.floatToRawIntBits($L) != 0", var);
                case DOUBLE -> CodeBlock.of("Double.doubleToRawLongBits($L) != 0L", var);
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> CodeBlock.of("$L != 0L", var);
                default -> CodeBlock.of("$L != 0", var);
            };
        }
        if (field.byteArray) {
            return CodeBlock.of("$L != null && $L.length != 0", var, var);
        }
        if (field.byteBuffer) {
            return CodeBlock.of("$L != null && $L.remaining() != 0", var, var);
        }
        return switch (field.protoType) {
            case STRING -> CodeBlock.of("$L != null && !$L.isEmpty()", var, var);
            case BOOL -> CodeBlock.of("$L != null && $L", var, var);
            case FLOAT -> CodeBlock.of("$L != null && Float.floatToRawIntBits($L) != 0", var, var);
            case DOUBLE -> CodeBlock.of("$L != null && Double.doubleToRawLongBits($L) != 0L", var, var);
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> CodeBlock.of("$L != null && $L != 0L", var, var);
            default -> CodeBlock.of("$L != null && $L != 0", var, var);
        };
    }

    static CodeBlock presentRepeated(FieldModel field) {
        if (field.array) {
            return CodeBlock.of("$L != null && $L.length != 0", field.localName, field.localName);
        }
        return CodeBlock.of("$L != null && !$L.isEmpty()", field.localName, field.localName);
    }

    static CodeBlock mapDefaultSkip(FieldModel part, String var) {
        if (part.byteArray) {
            return CodeBlock.of("$L.length != 0", var);
        }
        if (part.byteBuffer) {
            return CodeBlock.of("$L.remaining() != 0", var);
        }
        return switch (part.protoType) {
            case BOOL -> CodeBlock.of("$L", var);
            case STRING -> CodeBlock.of("!$L.isEmpty()", var);
            case FLOAT -> CodeBlock.of("Float.floatToRawIntBits($L) != 0", var);
            case DOUBLE -> CodeBlock.of("Double.doubleToRawLongBits($L) != 0L", var);
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> CodeBlock.of("$L != 0L", var);
            default -> CodeBlock.of("$L != 0", var);
        };
    }

    static CodeBlock enumPresent(FieldModel field, String var) {
        if (field.enumModel.unrecognized == null) {
            return CodeBlock.of("$L != null", var);
        }
        return CodeBlock.of("$L != null && $L != $L", var, var, enumConstant(field.enumModel, field.enumModel.unrecognized));
    }
}
