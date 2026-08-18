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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a {@code @ProtoMessage} and collects members. Does not resolve access
 * or report bind errors — that happens in {@link SchemaParser} after role checks.
 *
 * @author Rawvoid
 */
final class MemberScanner {

    private final Diagnostics diag;

    MemberScanner(Diagnostics diag) {
        this.diag = diag;
    }

    ScanResult scan(TypeElement type) {
        if (type.getKind() == ElementKind.RECORD) {
            return new ScanResult(scanRecord(type), Map.of());
        }
        return scanPojo(type);
    }

    Access resolveAccess(Member member, Map<String, ExecutableElement> methods) {
        if (member.recordComponent()) {
            return new Access(AccessKind.RECORD, "value." + member.name() + "()", null);
        }
        if (member.fromField()) {
            return resolvePojoAccess(
                (VariableElement) member.origin(), member.name(), member.type(), methods);
        }
        if (member.fromMethod()) {
            if (member.name() == null) {
                return null;
            }
            return getterSetter((ExecutableElement) member.origin(), member.name(), methods);
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
        Map<String, VariableElement> fields = new HashMap<>();
        Map<String, ExecutableElement> methods = new HashMap<>();
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
            methods.put(method.getSimpleName().toString(), method);
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
        for (ExecutableElement method : methods.values()) {
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
        Map<String, ExecutableElement> methods) {
        String setter = Names.setterName(property);
        ExecutableElement set = methods.get(setter);
        if (set == null || set.getParameters().size() != 1 || !set.getModifiers().contains(Modifier.PUBLIC)
            || !method.getModifiers().contains(Modifier.PUBLIC)) {
            diag.error(method, "annotated getter '" + method.getSimpleName() + "' has no matching public setter " + setter);
            return null;
        }
        return new Access(
            AccessKind.GETTER_SETTER,
            "value." + method.getSimpleName() + "()",
            setter);
    }

    private Access resolvePojoAccess(
        VariableElement field,
        String name,
        TypeMirror fieldType,
        Map<String, ExecutableElement> methods) {
        boolean primitiveBoolean = fieldType.getKind() == TypeKind.BOOLEAN;
        ExecutableElement getter = methods.get(Names.getterName(name, primitiveBoolean));
        if (getter == null) {
            getter = methods.get(Names.getterName(name, !primitiveBoolean));
        }
        ExecutableElement setter = methods.get(Names.setterName(name));
        boolean getterOk = getter != null && getter.getParameters().isEmpty()
            && getter.getModifiers().contains(Modifier.PUBLIC);
        boolean setterOk = setter != null && setter.getParameters().size() == 1
            && setter.getModifiers().contains(Modifier.PUBLIC);
        if (getterOk && setterOk) {
            return new Access(
                AccessKind.GETTER_SETTER,
                "value." + getter.getSimpleName() + "()",
                setter.getSimpleName().toString());
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            return new Access(AccessKind.FIELD, "value." + name, null);
        }
        diag.error(field, "field '" + name + "' needs a public JavaBean getter and setter, or must be public");
        return null;
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
