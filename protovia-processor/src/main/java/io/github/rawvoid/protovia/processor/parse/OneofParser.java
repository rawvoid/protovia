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
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoOneofCase;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a {@code @ProtoOneof} sealed type and its {@code @ProtoOneofCase}s.
 */
final class OneofParser {

    private final TypeEnv env;
    private final Diagnostics diag;
    private final FieldResolver fields;
    private final AdapterResolver adapters;

    OneofParser(TypeEnv env, Diagnostics diag, FieldResolver fields, AdapterResolver adapters) {
        this.env = env;
        this.diag = diag;
        this.fields = fields;
        this.adapters = adapters;
    }

    FieldModel resolve(
        Element origin,
        String name,
        TypeMirror type,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        Set<Integer> taken) {
        TypeMirror oneofType = resolveSealedType(origin, name, type);
        if (oneofType == null) {
            return null;
        }
        TypeElement sealed = env.asTypeElement(oneofType);
        if (sealed == null) {
            diag.error(origin, "@ProtoOneof field '" + name + "' must be a sealed interface or class");
            return null;
        }
        List<? extends TypeMirror> permitted = sealed.getPermittedSubclasses();
        if (permitted.size() < 2) {
            diag.error(origin, "oneof '" + name + "' must have at least two @ProtoOneofCase types");
            return null;
        }
        List<OneofCaseModel> cases = new ArrayList<>();
        for (TypeMirror permittedType : permitted) {
            TypeElement caseType = env.asTypeElement(permittedType);
            if (caseType == null) {
                diag.error(origin, "oneof '" + name + "' has an unresolved permitted type");
                continue;
            }
            ProtoOneofCase caseAnn = caseType.getAnnotation(ProtoOneofCase.class);
            if (caseAnn == null) {
                diag.error(caseType, caseType.getSimpleName() + " must be annotated with @ProtoOneofCase");
                continue;
            }
            int number = caseAnn.value();
            if (!WireType.isValidFieldNumber(number)) {
                diag.error(caseType, "invalid field number " + number);
                continue;
            }
            if (!taken.add(number)) {
                diag.error(caseType, "duplicate field number " + number);
                continue;
            }
            OneofCaseModel parsed = parseCase(caseType, number, pkg);
            if (parsed != null) {
                cases.add(parsed);
            }
        }
        if (cases.size() < 2) {
            return null;
        }
        return FieldModel.builder()
            .number(0)
            .name(name)
            .localName(Names.safeLocal(name))
            .kind(FieldKind.ONEOF)
            .accessKind(accessKind)
            .readExpr(readExpr)
            .setterName(setter)
            .fieldName(fieldName)
            .javaTypeName(env.renderType(oneofType, pkg))
            .javaType(oneofType)
            .oneofCases(cases)
            .origin(origin)
            .build();
    }

    /**
     * Resolves a {@code @ProtoOneof} Java type to its sealed bound.
     * A type variable is treated as that bound so generic wrappers can reuse
     * the permitted {@code @ProtoOneofCase} types.
     */
    private TypeMirror resolveSealedType(Element origin, String name, TypeMirror type) {
        List<TypeMirror> sealed = new ArrayList<>();
        collectSealedBounds(type, sealed, new HashSet<>());
        if (sealed.size() > 1) {
            diag.error(origin, "@ProtoOneof field '" + name + "' type variable has more than one sealed bound");
            return null;
        }
        if (sealed.isEmpty()) {
            if (type.getKind() == TypeKind.TYPEVAR) {
                diag.error(origin, "@ProtoOneof field '" + name
                    + "' type variable must be bounded by a sealed interface or class");
            } else {
                diag.error(origin, "@ProtoOneof field '" + name + "' must be a sealed interface or class");
            }
            return null;
        }
        return sealed.get(0);
    }

    private void collectSealedBounds(TypeMirror type, List<TypeMirror> sealed, Set<Element> seen) {
        if (type == null) {
            return;
        }
        TypeKind kind = type.getKind();
        if (kind == TypeKind.TYPEVAR) {
            Element element = env.types.asElement(type);
            if (element != null && !seen.add(element)) {
                return;
            }
            collectSealedBounds(((TypeVariable) type).getUpperBound(), sealed, seen);
            return;
        }
        if (kind == TypeKind.INTERSECTION) {
            for (TypeMirror bound : ((IntersectionType) type).getBounds()) {
                collectSealedBounds(bound, sealed, seen);
            }
            return;
        }
        TypeElement declared = env.asTypeElement(type);
        if (declared != null && declared.getModifiers().contains(Modifier.SEALED)) {
            sealed.add(type);
        }
    }

    private OneofCaseModel parseCase(TypeElement caseType, int number, String pkg) {
        String typeName = Names.typeName(caseType, pkg);
        String tag = Names.tagConstant(number);
        TypeElement fieldAdapter = adapters.adapterFrom(caseType, AdapterResolver.PROTO_ONEOF_CASE_ANN);
        if (caseType.getAnnotation(ProtoMessage.class) != null) {
            adapters.rejectOneofAdapter(caseType, fieldAdapter);
            String codec = Names.codecSimpleName(env.elements, caseType);
            String codecPkg = Names.packageName(caseType);
            if (!codecPkg.equals(pkg) && !codecPkg.isEmpty()) {
                codec = codecPkg + "." + codec;
            }
            FieldModel payload = FieldModel.builder()
                .kind(FieldKind.MESSAGE)
                .protoType(ProtoType.MESSAGE)
                .codecName(codec)
                .messageType(caseType)
                .javaTypeName(typeName)
                .javaType(caseType.asType())
                .build();
            return new OneofCaseModel(number, caseType, typeName, tag, payload, null, true);
        }
        if (caseType.getKind() != ElementKind.RECORD) {
            diag.error(caseType, "@ProtoOneofCase " + caseType.getSimpleName()
                + " must be a record with 0 or 1 component, or a @ProtoMessage");
            return null;
        }
        List<? extends RecordComponentElement> components = caseType.getRecordComponents();
        if (components.isEmpty()) {
            adapters.rejectOneofAdapter(caseType, fieldAdapter);
            return new OneofCaseModel(number, caseType, typeName, tag, null, null, false);
        }
        if (components.size() != 1) {
            diag.error(caseType, "@ProtoOneofCase record " + caseType.getSimpleName()
                + " must have 0 or 1 component");
            return null;
        }
        RecordComponentElement component = components.get(0);
        TypeMirror payloadType = component.asType();
        if (env.isMap(payloadType) || env.isRepeatedContainer(payloadType)
            && !(payloadType.getKind() == TypeKind.ARRAY
            && ((ArrayType) payloadType).getComponentType().getKind() == TypeKind.BYTE)) {
            diag.error(caseType, "oneof case cannot be repeated or map");
            return null;
        }
        ProtoOneofCase caseAnn = caseType.getAnnotation(ProtoOneofCase.class);
        ProtoType declared = caseAnn == null ? ProtoType.AUTO : caseAnn.type();
        FieldModel payload = fields.resolveSingular(new FieldRequest(
            caseType,
            caseType.getSimpleName() + "Payload",
            payloadType,
            declared,
            false,
            false,
            AccessKind.RECORD,
            null,
            null,
            null,
            pkg,
            false,
            payloadType,
            fieldAdapter,
            number,
            AdapterSite.ONEOF));
        if (payload == null) {
            return null;
        }
        return new OneofCaseModel(
            number, caseType, typeName, tag, payload, component.getSimpleName() + "()", false);
    }
}
