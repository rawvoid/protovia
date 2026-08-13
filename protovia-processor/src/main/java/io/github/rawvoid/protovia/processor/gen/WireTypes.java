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

    static PrimitiveListSpec primitiveListSpec(FieldModel field) {
        String type = field.primitiveListType();
        if (type == null) {
            return null;
        }
        for (PrimitiveListSpec spec : PrimitiveListSpec.values()) {
            if (type.endsWith(spec.simpleName())) {
                return spec;
            }
        }
        return null;
    }

    static String presentCondition(FieldModel field, String var, boolean optional, boolean javaOptional) {
        if (javaOptional) {
            return var + " != null && " + var + ".isPresent()";
        }
        if (optional) {
            return var + " != null";
        }
        if (field.primitive) {
            return switch (field.protoType) {
                case BOOL -> var;
                case FLOAT -> "Float.floatToRawIntBits(" + var + ") != 0";
                case DOUBLE -> "Double.doubleToRawLongBits(" + var + ") != 0L";
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> var + " != 0L";
                default -> var + " != 0";
            };
        }
        if (field.byteArray) {
            return var + " != null && " + var + ".length != 0";
        }
        if (field.byteBuffer) {
            return var + " != null && " + var + ".remaining() != 0";
        }
        return switch (field.protoType) {
            case STRING -> var + " != null && !" + var + ".isEmpty()";
            case BOOL -> var + " != null && " + var;
            case FLOAT -> var + " != null && Float.floatToRawIntBits(" + var + ") != 0";
            case DOUBLE -> var + " != null && Double.doubleToRawLongBits(" + var + ") != 0L";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> var + " != null && " + var + " != 0L";
            default -> var + " != null && " + var + " != 0";
        };
    }

    static String presentRepeated(FieldModel field) {
        if (field.array) {
            return field.localName + " != null && " + field.localName + ".length != 0";
        }
        return field.localName + " != null && !" + field.localName + ".isEmpty()";
    }

    static String mapDefaultSkip(FieldModel part, String var) {
        if (part.byteArray) {
            return var + ".length != 0";
        }
        if (part.byteBuffer) {
            return var + ".remaining() != 0";
        }
        return switch (part.protoType) {
            case BOOL -> var;
            case STRING -> "!" + var + ".isEmpty()";
            case FLOAT -> "Float.floatToRawIntBits(" + var + ") != 0";
            case DOUBLE -> "Double.doubleToRawLongBits(" + var + ") != 0L";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> var + " != 0L";
            default -> var + " != 0";
        };
    }

    static CodeBlock enumPresent(FieldModel field, String var) {
        if (field.enumModel.unrecognized == null) {
            return CodeBlock.of("$L != null", var);
        }
        return CodeBlock.of("$L != null && $L != $L", var, var, enumConstant(field.enumModel, field.enumModel.unrecognized));
    }
}
