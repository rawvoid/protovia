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

import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

/**
 * Cached {@link TypeMirror}s and type-query helpers shared by the parse pipeline.
 *
 * @author Rawvoid
 */
final class TypeEnv {

    final Types types;
    final Elements elements;
    final TypeMirror objectType;
    final TypeMirror stringType;
    final TypeMirror integerType;
    final TypeMirror longType;
    final TypeMirror floatType;
    final TypeMirror doubleType;
    final TypeMirror booleanType;
    final TypeMirror byteBufferType;
    final TypeMirror listType;
    final TypeMirror setType;
    final TypeMirror collectionType;
    final TypeMirror mapType;
    final TypeMirror optionalType;
    final TypeElement protoAdapterType;

    TypeEnv(Types types, Elements elements) {
        this.types = types;
        this.elements = elements;
        this.objectType = elements.getTypeElement("java.lang.Object").asType();
        this.stringType = elements.getTypeElement("java.lang.String").asType();
        this.integerType = elements.getTypeElement("java.lang.Integer").asType();
        this.longType = elements.getTypeElement("java.lang.Long").asType();
        this.floatType = elements.getTypeElement("java.lang.Float").asType();
        this.doubleType = elements.getTypeElement("java.lang.Double").asType();
        this.booleanType = elements.getTypeElement("java.lang.Boolean").asType();
        this.byteBufferType = elements.getTypeElement("java.nio.ByteBuffer").asType();
        this.listType = erasure("java.util.List");
        this.setType = erasure("java.util.Set");
        this.collectionType = erasure("java.util.Collection");
        this.mapType = erasure("java.util.Map");
        this.optionalType = erasure("java.util.Optional");
        this.protoAdapterType = elements.getTypeElement(AdapterResolver.PROTO_ADAPTER);
    }

    TypeMirror erasure(String fqcn) {
        return types.erasure(elements.getTypeElement(fqcn).asType());
    }

    boolean isSame(TypeMirror a, TypeMirror b) {
        return types.isSameType(types.erasure(a), types.erasure(b));
    }

    boolean isAssignable(TypeMirror a, TypeMirror b) {
        return types.isAssignable(a, b);
    }

    TypeElement asTypeElement(TypeMirror type) {
        Element e = types.asElement(type);
        return e instanceof TypeElement te ? te : null;
    }

    boolean isRepeatedContainer(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }
        TypeMirror erased = types.erasure(type);
        return types.isAssignable(erased, listType)
            || types.isAssignable(erased, setType)
            || types.isAssignable(erased, collectionType);
    }

    boolean isMap(TypeMirror type) {
        return types.isAssignable(types.erasure(type), mapType);
    }

    boolean isOptional(TypeMirror type) {
        return types.isAssignable(types.erasure(type), optionalType);
    }

    TypeMirror typeArgument(TypeMirror type, int index, Element origin, String what, Diagnostics diag) {
        if (!(type instanceof DeclaredType declared)) {
            diag.error(origin, "raw " + what + " is not supported; use a parameterized type");
            return null;
        }
        List<? extends TypeMirror> args = declared.getTypeArguments();
        if (args.size() <= index) {
            diag.error(origin, "raw " + what + " is not supported; use a parameterized type");
            return null;
        }
        TypeMirror arg = args.get(index);
        if (arg.getKind() == TypeKind.WILDCARD || arg.getKind() == TypeKind.TYPEVAR) {
            diag.error(origin, "wildcard / type-variable " + what + " type arguments are not supported");
            return null;
        }
        return arg;
    }

    TypeElement collectionImpl(TypeMirror type, FieldModel elementModel) {
        TypeElement element = asTypeElement(type);
        if (element != null && !element.getModifiers().contains(Modifier.ABSTRACT)
            && !element.getQualifiedName().contentEquals("java.util.List")
            && !element.getQualifiedName().contentEquals("java.util.Set")
            && !element.getQualifiedName().contentEquals("java.util.Collection")) {
            return element;
        }
        TypeMirror erased = types.erasure(type);
        if (types.isAssignable(erased, setType)) {
            return elements.getTypeElement("java.util.LinkedHashSet");
        }
        String primitive = elementModel.primitiveListClass();
        if (primitive != null) {
            return elements.getTypeElement(primitive);
        }
        return elements.getTypeElement("java.util.ArrayList");
    }

    TypeElement mapImpl(TypeMirror type) {
        TypeElement element = asTypeElement(type);
        if (element != null && !element.getModifiers().contains(Modifier.ABSTRACT)
            && !element.getQualifiedName().contentEquals("java.util.Map")) {
            return element;
        }
        return elements.getTypeElement("java.util.LinkedHashMap");
    }

    String renderType(TypeMirror type, String pkg) {
        return switch (type.getKind()) {
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case CHAR -> "char";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case ARRAY -> renderType(((ArrayType) type).getComponentType(), pkg) + "[]";
            case DECLARED -> renderDeclared((DeclaredType) type, pkg);
            default -> type.toString();
        };
    }

    private String renderDeclared(DeclaredType type, String pkg) {
        TypeElement element = (TypeElement) type.asElement();
        String name = Names.typeName(element, pkg);
        List<? extends TypeMirror> args = type.getTypeArguments();
        if (args.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name).append('<');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(renderType(args.get(i), pkg));
        }
        return sb.append('>').toString();
    }

    String simpleTypeName(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return simpleTypeName(((ArrayType) type).getComponentType()) + "[]";
        }
        TypeElement element = asTypeElement(type);
        return element != null ? element.getSimpleName().toString() : type.toString();
    }

    boolean isPublicType(TypeElement type) {
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
            return false;
        }
        Element enclosing = type.getEnclosingElement();
        if (enclosing.getKind() == ElementKind.PACKAGE) {
            return true;
        }
        if (enclosing instanceof TypeElement parent) {
            return type.getModifiers().contains(Modifier.STATIC) && isPublicType(parent);
        }
        return false;
    }

    /**
     * Whether a sibling top-level codec in {@code parentPkg} can mention {@code type}.
     */
    boolean isAccessibleFromCodec(TypeElement type, String parentPkg) {
        Element current = type;
        while (current instanceof TypeElement te) {
            if (te.getModifiers().contains(Modifier.PRIVATE)) {
                return false;
            }
            Element enclosing = te.getEnclosingElement();
            if (enclosing instanceof TypeElement && !te.getModifiers().contains(Modifier.STATIC)) {
                return false;
            }
            if (!te.getModifiers().contains(Modifier.PUBLIC)
                && !Names.packageName(te).equals(parentPkg)) {
                return false;
            }
            current = enclosing;
        }
        return true;
    }

    boolean isResolvedType(TypeMirror type) {
        TypeKind kind = type.getKind();
        return kind != TypeKind.WILDCARD && kind != TypeKind.TYPEVAR && kind != TypeKind.ERROR;
    }
}
