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

import io.github.rawvoid.protovia.processor.model.CollectionKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    boolean isByteArray(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY
            && ((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE;
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

    CollectionKind collectionKind(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return CollectionKind.NONE;
        }
        TypeMirror erased = types.erasure(type);
        if (types.isAssignable(erased, setType)) {
            return CollectionKind.SET;
        }
        if (types.isAssignable(erased, listType)) {
            return CollectionKind.LIST;
        }
        if (types.isAssignable(erased, collectionType)) {
            return CollectionKind.COLLECTION;
        }
        return CollectionKind.NONE;
    }

    boolean isMap(TypeMirror type) {
        return types.isAssignable(types.erasure(type), mapType);
    }

    boolean isOptional(TypeMirror type) {
        return types.isAssignable(types.erasure(type), optionalType);
    }

    TypeMirror typeArgument(
        TypeMirror type,
        TypeMirror targetErasure,
        int index,
        Element origin,
        String what,
        Diagnostics diag) {
        DeclaredType target = findSupertype(type, targetErasure);
        if (target == null) {
            diag.error(origin, "raw " + what + " is not supported; use a parameterized type");
            return null;
        }
        List<? extends TypeMirror> args = target.getTypeArguments();
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

    private DeclaredType findSupertype(TypeMirror start, TypeMirror targetErasure) {
        if (!(start instanceof DeclaredType)) {
            return null;
        }
        Deque<TypeMirror> queue = new ArrayDeque<>();
        Set<TypeElement> visited = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            TypeMirror current = queue.poll();
            if (!(current instanceof DeclaredType declared)) {
                continue;
            }
            if (types.isSameType(types.erasure(declared), targetErasure)) {
                return declared;
            }
            TypeElement element = asTypeElement(declared);
            if (element != null && !visited.add(element)) {
                continue;
            }
            for (TypeMirror sup : types.directSupertypes(declared)) {
                queue.add(sup);
            }
        }
        return null;
    }

    TypeElement collectionImpl(
        TypeMirror type,
        FieldModel elementModel,
        Element origin,
        String codecPkg,
        Diagnostics diag) {
        TypeElement element = asTypeElement(type);
        if (element == null) {
            diag.error(origin, "unsupported collection type " + type
                + "; use List, Set, SortedSet, Collection, or a concrete mutable collection with a public no-arg constructor");
            return null;
        }
        String qn = element.getQualifiedName().toString();
        if (qn.equals("java.util.List") || qn.equals("java.util.Collection")) {
            String primitive = elementModel.primitiveListClass();
            return elements.getTypeElement(primitive != null ? primitive : "java.util.ArrayList");
        }
        if (qn.equals("java.util.Set")) {
            return elements.getTypeElement("java.util.LinkedHashSet");
        }
        if (qn.equals("java.util.SortedSet") || qn.equals("java.util.NavigableSet")) {
            return elements.getTypeElement("java.util.TreeSet");
        }
        if (element.getKind() == ElementKind.CLASS && !element.getModifiers().contains(Modifier.ABSTRACT)) {
            if (!isAccessibleFromCodec(element, codecPkg)) {
                diag.error(origin, "collection type " + element.getSimpleName()
                    + " is not accessible from " + codecPkg);
                return null;
            }
            if (!hasPublicNoArgConstructor(element)) {
                diag.error(origin, "collection type " + element.getSimpleName()
                    + " must have a public no-arg constructor");
                return null;
            }
            return element;
        }
        diag.error(origin, "unsupported collection type " + element.getSimpleName()
            + "; use List, Set, SortedSet, Collection, or a concrete mutable collection with a public no-arg constructor");
        return null;
    }

    TypeElement mapImpl(
        TypeMirror type,
        Element origin,
        String codecPkg,
        Diagnostics diag) {
        TypeElement element = asTypeElement(type);
        if (element == null) {
            diag.error(origin, "unsupported map type " + type
                + "; use Map, SortedMap, NavigableMap, ConcurrentMap, or a concrete mutable map with a public no-arg constructor");
            return null;
        }
        String qn = element.getQualifiedName().toString();
        if (qn.equals("java.util.Map")) {
            return elements.getTypeElement("java.util.LinkedHashMap");
        }
        if (qn.equals("java.util.SortedMap") || qn.equals("java.util.NavigableMap")) {
            return elements.getTypeElement("java.util.TreeMap");
        }
        if (qn.equals("java.util.concurrent.ConcurrentMap")) {
            return elements.getTypeElement("java.util.concurrent.ConcurrentHashMap");
        }
        if (qn.equals("java.util.concurrent.ConcurrentNavigableMap")) {
            return elements.getTypeElement("java.util.concurrent.ConcurrentSkipListMap");
        }
        if (element.getKind() == ElementKind.CLASS && !element.getModifiers().contains(Modifier.ABSTRACT)) {
            if (!isAccessibleFromCodec(element, codecPkg)) {
                diag.error(origin, "map type " + element.getSimpleName()
                    + " is not accessible from " + codecPkg);
                return null;
            }
            if (!hasPublicNoArgConstructor(element)) {
                diag.error(origin, "map type " + element.getSimpleName()
                    + " must have a public no-arg constructor");
                return null;
            }
            return element;
        }
        diag.error(origin, "unsupported map type " + element.getSimpleName()
            + "; use Map, SortedMap, NavigableMap, ConcurrentMap, or a concrete mutable map with a public no-arg constructor");
        return null;
    }

    static boolean hasPublicNoArgConstructor(TypeElement type) {
        boolean sawCtor = false;
        for (ExecutableElement ctor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            sawCtor = true;
            if (ctor.getParameters().isEmpty() && ctor.getModifiers().contains(Modifier.PUBLIC)) {
                return true;
            }
        }
        return !sawCtor && type.getModifiers().contains(Modifier.PUBLIC);
    }

    boolean hasPublicCopyConstructor(TypeElement impl, TypeMirror declaredType) {
        if (impl == null || !(declaredType instanceof DeclaredType declared)) {
            return false;
        }
        boolean sameRaw = types.isSameType(types.erasure(declaredType), types.erasure(impl.asType()));
        TypeMirror targetErasure = isMap(declaredType) ? mapType : collectionType;
        for (ExecutableElement ctor : ElementFilter.constructorsIn(impl.getEnclosedElements())) {
            if (!ctor.getModifiers().contains(Modifier.PUBLIC) || ctor.getParameters().size() != 1) {
                continue;
            }
            if (sameRaw) {
                TypeMirror paramType = ctor.getParameters().getFirst().asType();
                try {
                    if (types.asMemberOf(declared, ctor) instanceof ExecutableType exec
                        && !exec.getParameterTypes().isEmpty()) {
                        paramType = exec.getParameterTypes().getFirst();
                    }
                } catch (IllegalArgumentException ignored) {
                    // fall back to declared parameter type
                }
                if (types.isAssignable(types.erasure(paramType), targetErasure)
                    && types.isAssignable(declaredType, paramType)) {
                    return true;
                }
            } else if (impl.getTypeParameters().isEmpty()) {
                TypeMirror paramType = ctor.getParameters().getFirst().asType();
                if (types.isAssignable(types.erasure(paramType), targetErasure)
                    && types.isAssignable(declaredType, paramType)) {
                    return true;
                }
            } else {
                TypeMirror paramErased = types.erasure(ctor.getParameters().getFirst().asType());
                if (types.isAssignable(paramErased, targetErasure)
                    && types.isAssignable(types.erasure(declaredType), paramErased)) {
                    return true;
                }
            }
        }
        return false;
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

    /**
     * Whether generated code in {@code codecPkg} can mention {@code type},
     * including type arguments of a {@code DeclaredType}.
     */
    boolean typeVisibleFromCodec(TypeMirror type, String codecPkg) {
        if (type.getKind() == TypeKind.ARRAY) {
            return typeVisibleFromCodec(((ArrayType) type).getComponentType(), codecPkg);
        }
        if (!(type instanceof DeclaredType declared)) {
            return true;
        }
        TypeElement element = asTypeElement(declared);
        if (element != null && !isAccessibleFromCodec(element, codecPkg)) {
            return false;
        }
        for (TypeMirror arg : declared.getTypeArguments()) {
            if (!typeVisibleFromCodec(arg, codecPkg)) {
                return false;
            }
        }
        return true;
    }

    boolean isResolvedType(TypeMirror type) {
        TypeKind kind = type.getKind();
        return kind != TypeKind.WILDCARD && kind != TypeKind.TYPEVAR && kind != TypeKind.ERROR;
    }
}
