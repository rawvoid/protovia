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

import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a {@code @ProtoMessage} or mixin superclass and collects members.
 * Does not resolve access or report bind errors — that happens in
 * {@link SchemaParser} after role checks.
 *
 * @author Rawvoid
 */
final class MemberScanner {

    private final TypeEnv env;
    private final Diagnostics diag;

    MemberScanner(TypeEnv env, Diagnostics diag) {
        this.env = env;
        this.diag = diag;
    }

    ScanResult scan(TypeElement type) {
        if (type.getKind() == ElementKind.RECORD) {
            return new ScanResult(scanRecord(type), Map.of());
        }
        return scanPojo(type);
    }

    /**
     * @param owner          specialized declaring type when flattening; {@code null} for the leaf
     * @param inheritedOwner rendered declaring type when flattening (may include type
     *                       arguments); {@code null} for the leaf
     */
    Access resolveAccess(
        Member member,
        Map<String, List<ExecutableElement>> methods,
        DeclaredType owner,
        String inheritedOwner) {
        Access access = resolveAccess0(member, methods, owner);
        if (access == null || inheritedOwner == null || access.kind() != AccessKind.FIELD) {
            return access;
        }
        return new Access(
            AccessKind.FIELD,
            "((" + inheritedOwner + ") value)." + member.name(),
            null);
    }

    private Access resolveAccess0(
        Member member,
        Map<String, List<ExecutableElement>> methods,
        DeclaredType owner) {
        if (member.recordComponent()) {
            return new Access(AccessKind.RECORD, "value." + member.name() + "()", null);
        }
        if (member.fromField()) {
            return resolvePojoAccess(
                (VariableElement) member.origin(), member.name(), member.type(), methods, owner);
        }
        if (member.fromMethod()) {
            if (member.name() == null) {
                return null;
            }
            return getterSetter(
                (ExecutableElement) member.origin(), member.name(), member.type(), methods, owner);
        }
        return null;
    }

    private List<Member> scanRecord(TypeElement type) {
        List<Member> members = new ArrayList<>();
        for (RecordComponentElement component : type.getRecordComponents()) {
            ExecutableElement accessor = component.getAccessor();
            Roles roles = Roles.of(component).union(Roles.of(accessor));
            AnnotationMirror protoOneofAnn = resolveRecordOneof(component, accessor);
            ProtoField field = component.getAnnotation(ProtoField.class);
            if (field == null && accessor != null) {
                field = accessor.getAnnotation(ProtoField.class);
            }
            members.add(new Member(
                component,
                component.getSimpleName().toString(),
                component.asType(),
                true,
                roles,
                field,
                protoOneofAnn));
        }
        return members;
    }

    private AnnotationMirror resolveRecordOneof(
        RecordComponentElement component, ExecutableElement accessor) {
        AnnotationMirror onComponent = protoOneofMirror(component);
        AnnotationMirror onAccessor = protoOneofMirror(accessor);
        if (onComponent != null && onAccessor != null
            && !onComponent.toString().equals(onAccessor.toString())) {
            diag.error(component,
                "do not annotate both the record component and its accessor with @ProtoOneof");
            return null;
        }
        return onComponent != null ? onComponent : onAccessor;
    }

    private ScanResult scanPojo(TypeElement type) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        Map<String, List<ExecutableElement>> methods = new LinkedHashMap<>();
        List<ExecutableElement> instanceMethods = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            fields.put(field.getSimpleName().toString(), field);
        }
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            instanceMethods.add(method);
            methods.computeIfAbsent(method.getSimpleName().toString(), k -> new ArrayList<>()).add(method);
        }

        List<Member> members = new ArrayList<>();
        for (VariableElement field : fields.values()) {
            Roles roles = Roles.of(field);
            if (!roles.any()) {
                continue;
            }
            members.add(new Member(
                field,
                field.getSimpleName().toString(),
                field.asType(),
                false,
                roles,
                field.getAnnotation(ProtoField.class),
                protoOneofMirror(field)));
        }
        for (ExecutableElement method : instanceMethods) {
            Roles roles = Roles.of(method);
            if (!roles.any()) {
                continue;
            }
            String property = Names.propertyFromGetter(method.getSimpleName().toString());
            members.add(new Member(
                method,
                property,
                method.getReturnType(),
                false,
                roles,
                method.getAnnotation(ProtoField.class),
                protoOneofMirror(method)));
        }
        return new ScanResult(members, methods);
    }

    private Access getterSetter(
        ExecutableElement method,
        String property,
        TypeMirror propertyType,
        Map<String, List<ExecutableElement>> methods,
        DeclaredType owner) {
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            diag.error(method, "annotated getter '" + method.getSimpleName() + "' must be public");
            return null;
        }
        String setterName = Names.setterName(property);
        ExecutableElement set = findSetter(methods, setterName, propertyType, owner);
        if (set != null) {
            return new Access(
                AccessKind.GETTER_SETTER,
                "value." + method.getSimpleName() + "()",
                setterName);
        }
        return new Access(
            AccessKind.GETTER,
            "value." + method.getSimpleName() + "()",
            null);
    }

    private Access resolvePojoAccess(
        VariableElement field,
        String name,
        TypeMirror fieldType,
        Map<String, List<ExecutableElement>> methods,
        DeclaredType owner) {
        boolean primitiveBoolean = fieldType.getKind() == TypeKind.BOOLEAN;
        ExecutableElement getter = findGetter(methods, Names.getterName(name, primitiveBoolean), fieldType, owner);
        if (getter == null) {
            getter = findGetter(methods, Names.getterName(name, !primitiveBoolean), fieldType, owner);
        }
        ExecutableElement setter = findSetter(methods, Names.setterName(name), fieldType, owner);
        if (getter != null && setter != null) {
            return new Access(
                AccessKind.GETTER_SETTER,
                "value." + getter.getSimpleName() + "()",
                setter.getSimpleName().toString());
        }
        if (getter != null) {
            return new Access(
                AccessKind.GETTER,
                "value." + getter.getSimpleName() + "()",
                null);
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            return new Access(AccessKind.FIELD, "value." + name, null);
        }
        diag.error(field, "field '" + name + "' needs a public JavaBean getter, or must be public");
        return null;
    }

    private ExecutableElement findGetter(
        Map<String, List<ExecutableElement>> methods,
        String name,
        TypeMirror fieldType,
        DeclaredType owner) {
        List<ExecutableElement> candidates = methods.get(name);
        if (candidates == null) {
            return null;
        }
        for (ExecutableElement method : candidates) {
            if (!method.getParameters().isEmpty() || !method.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            TypeMirror returnType = returnTypeOf(method, owner);
            if (returnType.getKind() != TypeKind.VOID && env.types.isAssignable(returnType, fieldType)) {
                return method;
            }
        }
        return null;
    }

    private ExecutableElement findSetter(
        Map<String, List<ExecutableElement>> methods,
        String name,
        TypeMirror fieldType,
        DeclaredType owner) {
        List<ExecutableElement> candidates = methods.get(name);
        if (candidates == null) {
            return null;
        }
        for (ExecutableElement method : candidates) {
            if (method.getParameters().size() != 1 || !method.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            TypeMirror paramType = firstParamTypeOf(method, owner);
            if (env.types.isAssignable(fieldType, paramType)) {
                return method;
            }
        }
        return null;
    }

    private TypeMirror returnTypeOf(ExecutableElement method, DeclaredType owner) {
        if (owner != null) {
            try {
                if (env.types.asMemberOf(owner, method) instanceof ExecutableType exec) {
                    return exec.getReturnType();
                }
            } catch (IllegalArgumentException ignored) {
                // fall back to declared return type
            }
        }
        return method.getReturnType();
    }

    private TypeMirror firstParamTypeOf(ExecutableElement method, DeclaredType owner) {
        if (owner != null) {
            try {
                if (env.types.asMemberOf(owner, method) instanceof ExecutableType exec
                    && !exec.getParameterTypes().isEmpty()) {
                    return exec.getParameterTypes().getFirst();
                }
            } catch (IllegalArgumentException ignored) {
                // fall back to declared parameter type
            }
        }
        return method.getParameters().getFirst().asType();
    }

    private static AnnotationMirror protoOneofMirror(Element element) {
        if (element == null) {
            return null;
        }
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotation = mirror.getAnnotationType().asElement();
            if (annotation instanceof TypeElement type
                && type.getQualifiedName().contentEquals(AdapterResolver.PROTO_ONEOF_ANN)) {
                return mirror;
            }
        }
        return null;
    }
}
