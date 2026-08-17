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
import io.github.rawvoid.protovia.annotation.ProtoOneof;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a {@code @ProtoMessage} type and yields a unified {@link Member} list.
 * Record components, POJO fields, then POJO getters — same order as the old
 * {@code parseRecord} / {@code parsePojo} loops.
 */
final class MemberScanner {

    private final Diagnostics diag;

    MemberScanner(Diagnostics diag) {
        this.diag = diag;
    }

    private Map<String, ExecutableElement> lastMethods = Map.of();

    List<Member> scan(TypeElement type) {
        lastMethods = Map.of();
        if (type.getKind() == ElementKind.RECORD) {
            return scanRecord(type);
        }
        return scanPojo(type);
    }

    /**
     * Resolves getter+setter for a method-annotated member after
     * duplicate-annotation checks have run.
     */
    Access completeAccess(Member member) {
        if (member.access() != null) {
            return member.access();
        }
        if (!member.fromMethod()) {
            return null;
        }
        return getterSetter((ExecutableElement) member.origin(), member.name(), lastMethods);
    }

    private List<Member> scanRecord(TypeElement type) {
        List<Member> members = new ArrayList<>();
        for (RecordComponentElement component : type.getRecordComponents()) {
            ExecutableElement accessor = component.getAccessor();
            boolean unknown = component.getAnnotation(ProtoUnknown.class) != null
                || (accessor != null && accessor.getAnnotation(ProtoUnknown.class) != null);
            boolean oneof = component.getAnnotation(ProtoOneof.class) != null
                || (accessor != null && accessor.getAnnotation(ProtoOneof.class) != null);
            ProtoField fieldOnComponent = component.getAnnotation(ProtoField.class);
            ProtoField field = fieldOnComponent;
            if (field == null && accessor != null) {
                field = accessor.getAnnotation(ProtoField.class);
            }
            String name = component.getSimpleName().toString();
            Access access = new Access(AccessKind.RECORD, "value." + name + "()", null);
            members.add(new Member(
                component,
                name,
                component.asType(),
                access,
                true,
                unknown,
                oneof,
                fieldOnComponent != null,
                field));
        }
        return members;
    }

    private List<Member> scanPojo(TypeElement type) {
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
        lastMethods = methods;

        List<Member> members = new ArrayList<>();
        for (VariableElement field : fields.values()) {
            boolean unknown = field.getAnnotation(ProtoUnknown.class) != null;
            boolean oneof = field.getAnnotation(ProtoOneof.class) != null;
            ProtoField protoField = field.getAnnotation(ProtoField.class);
            if (!unknown && !oneof && protoField == null) {
                continue;
            }
            String name = field.getSimpleName().toString();
            Access access = resolvePojoAccess(field, name, field.asType(), methods);
            members.add(new Member(
                field, name, field.asType(), access, false,
                unknown, oneof, protoField != null, protoField));
        }
        for (ExecutableElement method : methods.values()) {
            boolean unknown = method.getAnnotation(ProtoUnknown.class) != null;
            boolean oneof = method.getAnnotation(ProtoOneof.class) != null;
            ProtoField protoField = method.getAnnotation(ProtoField.class);
            if (!unknown && !oneof && protoField == null) {
                continue;
            }
            Member member = pojoMethodMember(method, unknown, oneof, protoField);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    /**
     * Shape checks (getter / setter / already-annotated) run in
     * {@link SchemaParser} so error order matches the old method loop.
     */
    private static Member pojoMethodMember(
        ExecutableElement method,
        boolean unknown,
        boolean oneof,
        ProtoField protoField) {
        String property = Names.propertyFromGetter(method.getSimpleName().toString());
        return new Member(
            method, property, method.getReturnType(), null, false,
            unknown, oneof, protoField != null, protoField);
    }

    Access getterSetter(ExecutableElement method, String property, Map<String, ExecutableElement> methods) {
        String setter = Names.setterName(property);
        ExecutableElement set = methods.get(setter);
        if (set == null || set.getParameters().size() != 1) {
            diag.error(method, "annotated getter '" + method.getSimpleName() + "' has no matching setter " + setter);
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
            && !getter.getModifiers().contains(Modifier.PRIVATE);
        boolean setterOk = setter != null && setter.getParameters().size() == 1
            && !setter.getModifiers().contains(Modifier.PRIVATE);
        if (getterOk && setterOk) {
            return new Access(
                AccessKind.GETTER_SETTER,
                "value." + getter.getSimpleName() + "()",
                setter.getSimpleName().toString());
        }
        if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            return new Access(AccessKind.FIELD, "value." + name, null);
        }
        diag.error(field, "private field '" + name + "' needs a JavaBean getter and setter, or must not be private");
        return null;
    }
}
