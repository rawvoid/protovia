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

package io.github.rawvoid.protovia.processor.parse;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoEnum;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Map;
import java.util.Set;

/**
 * Maps a Java type to a proto scalar, enum, or message. Does not apply adapters.
 *
 * @author Rawvoid
 */
final class TypeClassifier {

    static final Map<String, String> WELL_KNOWN_CODECS = Map.ofEntries(
        Map.entry("java.time.Instant", "io.github.rawvoid.protovia.wkt.TimestampCodec"),
        Map.entry("java.time.Duration", "io.github.rawvoid.protovia.wkt.DurationCodec"),
        Map.entry("io.github.rawvoid.protovia.ProtoAny", "io.github.rawvoid.protovia.wkt.AnyCodec"),
        Map.entry("io.github.rawvoid.protovia.wkt.DoubleValue", "io.github.rawvoid.protovia.wkt.DoubleValue"),
        Map.entry("io.github.rawvoid.protovia.wkt.FloatValue", "io.github.rawvoid.protovia.wkt.FloatValue"),
        Map.entry("io.github.rawvoid.protovia.wkt.Int64Value", "io.github.rawvoid.protovia.wkt.Int64Value"),
        Map.entry("io.github.rawvoid.protovia.wkt.UInt64Value", "io.github.rawvoid.protovia.wkt.UInt64Value"),
        Map.entry("io.github.rawvoid.protovia.wkt.Int32Value", "io.github.rawvoid.protovia.wkt.Int32Value"),
        Map.entry("io.github.rawvoid.protovia.wkt.UInt32Value", "io.github.rawvoid.protovia.wkt.UInt32Value"),
        Map.entry("io.github.rawvoid.protovia.wkt.BoolValue", "io.github.rawvoid.protovia.wkt.BoolValue"),
        Map.entry("io.github.rawvoid.protovia.wkt.StringValue", "io.github.rawvoid.protovia.wkt.StringValue"),
        Map.entry("io.github.rawvoid.protovia.wkt.BytesValue", "io.github.rawvoid.protovia.wkt.BytesValue"));

    private final TypeEnv env;
    private final Diagnostics diag;
    private final EnumParser enums;

    TypeClassifier(TypeEnv env, Diagnostics diag, EnumParser enums) {
        this.env = env;
        this.diag = diag;
        this.enums = enums;
    }

    Resolved classify(Element origin, String name, TypeMirror type, ProtoType declared, String currentPkg) {
        TypeKind kind = type.getKind();
        if (kind == TypeKind.INT || env.isSame(type, env.integerType)) {
            return scalar(origin, name, declared, ProtoType.INT32, intFamily());
        }
        if (kind == TypeKind.LONG || env.isSame(type, env.longType)) {
            return scalar(origin, name, declared, ProtoType.INT64, longFamily());
        }
        if (kind == TypeKind.FLOAT || env.isSame(type, env.floatType)) {
            return scalar(origin, name, declared, ProtoType.FLOAT, Set.of(ProtoType.AUTO, ProtoType.FLOAT));
        }
        if (kind == TypeKind.DOUBLE || env.isSame(type, env.doubleType)) {
            return scalar(origin, name, declared, ProtoType.DOUBLE, Set.of(ProtoType.AUTO, ProtoType.DOUBLE));
        }
        if (kind == TypeKind.BOOLEAN || env.isSame(type, env.booleanType)) {
            return scalar(origin, name, declared, ProtoType.BOOL, Set.of(ProtoType.AUTO, ProtoType.BOOL));
        }
        if (env.isSame(type, env.stringType)) {
            return scalar(origin, name, declared, ProtoType.STRING, Set.of(ProtoType.AUTO, ProtoType.STRING));
        }
        if (kind == TypeKind.ARRAY && ((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE) {
            Resolved r = scalar(origin, name, declared, ProtoType.BYTES, Set.of(ProtoType.AUTO, ProtoType.BYTES));
            if (r != null) {
                r.byteArray = true;
            }
            return r;
        }
        if (env.isAssignable(type, env.byteBufferType)) {
            Resolved r = scalar(origin, name, declared, ProtoType.BYTES, Set.of(ProtoType.AUTO, ProtoType.BYTES));
            if (r != null) {
                r.byteBuffer = true;
            }
            return r;
        }
        TypeElement element = env.asTypeElement(type);
        if (element == null) {
            diag.error(origin, "unsupported type for field '" + name + "': " + type);
            return null;
        }
        String wellKnownCodec = WELL_KNOWN_CODECS.get(element.getQualifiedName().toString());
        if (wellKnownCodec != null) {
            return wellKnown(origin, name, declared, wellKnownCodec);
        }
        if (element.getKind() == ElementKind.ENUM) {
            if (element.getAnnotation(ProtoEnum.class) == null) {
                diag.error(origin, "enum field '" + name + "' type " + element.getSimpleName()
                    + " must be annotated with @ProtoEnum");
                return null;
            }
            if (declared != ProtoType.AUTO && declared != ProtoType.ENUM) {
                diag.error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
                return null;
            }
            EnumModel enumModel = enums.parse(element);
            if (enumModel == null) {
                return null;
            }
            Resolved r = new Resolved();
            r.kind = FieldKind.ENUM;
            r.protoType = ProtoType.ENUM;
            r.enumModel = enumModel;
            return r;
        }
        if (element.getAnnotation(ProtoMessage.class) != null) {
            if (declared != ProtoType.AUTO && declared != ProtoType.MESSAGE) {
                diag.error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
                return null;
            }
            Resolved r = new Resolved();
            r.kind = FieldKind.MESSAGE;
            r.protoType = ProtoType.MESSAGE;
            r.messageType = element;
            r.codecName = Names.codecFqcn(env.elements, element);
            return r;
        }
        diag.error(origin, "unsupported type for field '" + name + "': " + element.getQualifiedName()
            + " (annotate with @ProtoMessage / @ProtoEnum)");
        return null;
    }

    boolean alreadyProtoType(TypeMirror javaJ) {
        TypeElement element = env.asTypeElement(javaJ);
        if (element == null) {
            return false;
        }
        if (element.getAnnotation(ProtoMessage.class) != null
            || element.getAnnotation(ProtoEnum.class) != null) {
            return true;
        }
        return WELL_KNOWN_CODECS.containsKey(element.getQualifiedName().toString());
    }

    String protoAdaptedOnProtoTypeMessage(TypeElement type) {
        String name = type.getSimpleName().toString();
        String kind;
        if (type.getAnnotation(ProtoMessage.class) != null) {
            kind = "@ProtoMessage type " + name;
        } else if (type.getAnnotation(ProtoEnum.class) != null) {
            kind = "@ProtoEnum type " + name;
        } else {
            kind = "well-known type " + name;
        }
        return "@ProtoAdapted cannot be applied to " + kind
            + "; override at the field or with @ProtoAdapters on the enclosing message";
    }

    Set<ProtoType> familyOf(ProtoType protoType) {
        return switch (protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> intFamily();
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> longFamily();
            case BOOL -> Set.of(ProtoType.AUTO, ProtoType.BOOL);
            case FLOAT -> Set.of(ProtoType.AUTO, ProtoType.FLOAT);
            case DOUBLE -> Set.of(ProtoType.AUTO, ProtoType.DOUBLE);
            case STRING -> Set.of(ProtoType.AUTO, ProtoType.STRING);
            case BYTES -> Set.of(ProtoType.AUTO, ProtoType.BYTES);
            default -> Set.of(ProtoType.AUTO);
        };
    }

    private Resolved wellKnown(Element origin, String name, ProtoType declared, String codec) {
        if (declared != ProtoType.AUTO && declared != ProtoType.MESSAGE) {
            diag.error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
            return null;
        }
        Resolved r = new Resolved();
        r.kind = FieldKind.MESSAGE;
        r.protoType = ProtoType.MESSAGE;
        r.codecName = codec;
        return r;
    }

    private Resolved scalar(Element origin, String name, ProtoType declared, ProtoType inferred, Set<ProtoType> allowed) {
        ProtoType actual = declared == ProtoType.AUTO ? inferred : declared;
        if (!allowed.contains(declared) && declared != ProtoType.AUTO) {
            diag.error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
            return null;
        }
        Resolved r = new Resolved();
        r.kind = FieldKind.SCALAR;
        r.protoType = actual;
        return r;
    }

    static Set<ProtoType> intFamily() {
        return Set.of(
            ProtoType.AUTO, ProtoType.INT32, ProtoType.UINT32, ProtoType.SINT32,
            ProtoType.FIXED32, ProtoType.SFIXED32);
    }

    static Set<ProtoType> longFamily() {
        return Set.of(
            ProtoType.AUTO, ProtoType.INT64, ProtoType.UINT64, ProtoType.SINT64,
            ProtoType.FIXED64, ProtoType.SFIXED64);
    }

    static boolean isValidMapKey(ProtoType type) {
        return switch (type) {
            case INT32, INT64, UINT32, UINT64, SINT32, SINT64,
                 FIXED32, FIXED64, SFIXED32, SFIXED64, BOOL, STRING -> true;
            default -> false;
        };
    }

    static ProtoType protoOrAuto(ProtoType type, ProtoType fallback) {
        return type == ProtoType.AUTO ? fallback : type;
    }
}
