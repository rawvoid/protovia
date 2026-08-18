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
import io.github.rawvoid.protovia.processor.model.*;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a field-level {@code @ProtoOneof} and its {@code @ProtoOneof.Case}s.
 *
 * @author Rawvoid
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
        AnnotationMirror protoOneof,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        Set<Integer> taken) {
        AnnotationMirror oneofAnn = protoOneof != null
            ? protoOneof
            : adapters.findAnnotation(origin, AdapterResolver.PROTO_ONEOF_ANN);
        AnnotationValue rawValue = oneofAnn == null ? null : adapters.annotationMember(oneofAnn, "value");
        if (oneofAnn == null || rawValue == null || !(rawValue.getValue() instanceof List<?> items)) {
            diag.error(origin, "oneof '" + name + "' has an unreadable @ProtoOneof value");
            return null;
        }

        boolean carrierOk = isNonContainerReference(type);
        if (!carrierOk) {
            diag.error(origin, "@ProtoOneof field '" + name
                + "' must be a reference type (not primitive, array, List, Set, Collection, Map, or Optional)");
        }

        if (items.isEmpty()) {
            diag.error(origin, "oneof '" + name + "' must declare at least one @ProtoOneof.Case");
        }

        List<ParsedCase> parsed = new ArrayList<>();
        for (Object item : items) {
            AnnotationMirror caseMirror = caseMirror(item);
            if (caseMirror == null) {
                diag.error(origin, "oneof '" + name + "' has an unreadable @ProtoOneof value");
                return null;
            }
            ParsedCase parsedCase = parseCase(origin, name, type, caseMirror, pkg, taken, carrierOk);
            if (parsedCase != null) {
                parsed.add(parsedCase);
            }
        }

        if (!carrierOk || parsed.isEmpty() || overlapping(origin, parsed)) {
            return null;
        }

        TypeMirror erased = env.types.erasure(type);
        List<OneofCaseModel> cases = new ArrayList<>(parsed.size());
        for (ParsedCase parsedCase : parsed) {
            cases.add(parsedCase.model);
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
            .javaTypeName(env.renderType(erased, pkg))
            .javaType(erased)
            .oneofCases(cases)
            .origin(origin)
            .build();
    }

    private ParsedCase parseCase(
        Element origin,
        String fieldName,
        TypeMirror fieldType,
        AnnotationMirror caseMirror,
        String pkg,
        Set<Integer> taken,
        boolean checkAssignability) {
        TypeMirror ofType = ofType(caseMirror, origin);
        if (ofType == null) {
            return null;
        }
        TypeKind kind = ofType.getKind();
        if (kind.isPrimitive()) {
            diag.error(origin, "oneof case of() must be a reference type (use Integer for int32)");
            return null;
        }
        if (kind == TypeKind.TYPEVAR || kind == TypeKind.WILDCARD) {
            diag.error(origin, "oneof case of() must be a class or interface type");
            return null;
        }
        if (kind == TypeKind.ARRAY) {
            if (!isByteArray(ofType)) {
                diag.error(origin, "oneof case cannot be repeated or map");
                return null;
            }
        }
        TypeElement caseType = env.asTypeElement(ofType);
        if (kind == TypeKind.DECLARED && caseType != null && !caseType.getTypeParameters().isEmpty()) {
            diag.error(origin, "oneof case " + caseType.getSimpleName() + " cannot declare type parameters");
            return null;
        }
        String codecPkg = Names.codecPackageName(pkg);
        if (caseType != null && !env.isAccessibleFromCodec(caseType, codecPkg)) {
            diag.error(origin, "oneof case " + caseType.getSimpleName() + " is not accessible from " + codecPkg);
            return null;
        }

        ProtoType declared = protoType(caseMirror);
        TypeElement fieldAdapter = adapters.adapterFrom(caseMirror, origin);
        OneofCaseModel model = shape(
            origin, fieldName, ofType, caseType, declared, fieldAdapter, pkg);
        if (model == null) {
            return null;
        }

        int number = number(caseMirror);
        if (!WireType.isValidFieldNumber(number)) {
            diag.error(origin, "invalid field number " + number);
            return null;
        }
        if (checkAssignability && !assignable(origin, ofType, caseType, fieldType)) {
            return null;
        }
        if (!taken.add(number)) {
            diag.error(origin, "duplicate field number " + number);
            return null;
        }
        return new ParsedCase(model.toBuilder().number(number).tagConstant(Names.tagConstant(number)).build(), ofType);
    }

    private OneofCaseModel shape(
        Element origin,
        String fieldName,
        TypeMirror ofType,
        TypeElement caseType,
        ProtoType declared,
        TypeElement fieldAdapter,
        String pkg) {
        if (caseType != null && caseType.getAnnotation(ProtoMessage.class) != null) {
            if (!rejectNonScalarOverrides(origin, declared, fieldAdapter)) {
                return null;
            }
            return selfMessage(caseType, pkg);
        }
        if (caseType != null && caseType.getKind() == ElementKind.RECORD) {
            return recordShape(origin, fieldName, ofType, caseType, declared, fieldAdapter, pkg);
        }
        if (env.isMap(ofType) || env.isOptional(ofType)
            || (env.isRepeatedContainer(ofType) && !isByteArray(ofType))
            || !looksLikeSingular(ofType, fieldAdapter)) {
            diag.error(origin, shapeMessage(caseType, ofType));
            return null;
        }
        FieldModel payload = resolvePayload(origin, fieldName, ofType, caseType, declared, fieldAdapter, pkg);
        if (payload == null) {
            return null;
        }
        boolean message = payload.kind == FieldKind.MESSAGE;
        return new OneofCaseModel(
            0, caseType, typeName(caseType, ofType, pkg), null, payload, null, message);
    }

    private OneofCaseModel recordShape(
        Element origin,
        String fieldName,
        TypeMirror ofType,
        TypeElement caseType,
        ProtoType declared,
        TypeElement fieldAdapter,
        String pkg) {
        List<? extends RecordComponentElement> components = caseType.getRecordComponents();
        if (components.isEmpty()) {
            if (!rejectNonScalarOverrides(origin, declared, fieldAdapter)) {
                return null;
            }
            return new OneofCaseModel(0, caseType, Names.typeName(caseType, pkg), null, null, null, false);
        }
        if (components.size() != 1) {
            diag.error(origin, "oneof case record " + caseType.getSimpleName() + " must have 0 or 1 component");
            return null;
        }
        RecordComponentElement component = components.get(0);
        TypeMirror payloadType = component.asType();
        if (payloadType.getKind() == TypeKind.TYPEVAR || payloadType.getKind() == TypeKind.WILDCARD) {
            diag.error(origin, "oneof case " + caseType.getSimpleName() + " cannot declare type parameters");
            return null;
        }
        if (env.isMap(payloadType) || env.isRepeatedContainer(payloadType) && !isByteArray(payloadType)) {
            diag.error(origin, "oneof case cannot be repeated or map");
            return null;
        }
        TypeElement payloadElement = env.asTypeElement(payloadType);
        boolean messagePayload = payloadElement != null && payloadElement.getAnnotation(ProtoMessage.class) != null;
        if (messagePayload && !rejectNonScalarOverrides(origin, declared, fieldAdapter)) {
            return null;
        }
        FieldModel payload = resolvePayload(
            origin, fieldName, payloadType, caseType, declared, fieldAdapter, pkg);
        if (payload == null) {
            return null;
        }
        return new OneofCaseModel(
            0, caseType, Names.typeName(caseType, pkg), null, payload, component.getSimpleName() + "()", false);
    }

    private FieldModel resolvePayload(
        Element origin,
        String fieldName,
        TypeMirror payloadType,
        TypeElement caseType,
        ProtoType declared,
        TypeElement fieldAdapter,
        String pkg) {
        String payloadName = (caseType != null ? caseType.getSimpleName() : fieldName) + "Payload";
        return fields.resolveSingular(new FieldRequest(
            origin,
            payloadName,
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
            0,
            AdapterSite.ONEOF));
    }

    private OneofCaseModel selfMessage(TypeElement caseType, String pkg) {
        String typeName = Names.typeName(caseType, pkg);
        String codec = Names.codecFqcn(env.elements, caseType);
        FieldModel payload = FieldModel.builder()
            .kind(FieldKind.MESSAGE)
            .protoType(ProtoType.MESSAGE)
            .codecName(codec)
            .messageType(caseType)
            .javaTypeName(typeName)
            .javaType(caseType.asType())
            .build();
        return new OneofCaseModel(0, caseType, typeName, null, payload, null, true);
    }

    private boolean rejectNonScalarOverrides(Element origin, ProtoType declared, TypeElement fieldAdapter) {
        boolean ok = true;
        if (fieldAdapter != null) {
            diag.error(origin, "@ProtoOneof.Case without a scalar payload cannot declare adapter");
            ok = false;
        }
        if (declared != ProtoType.AUTO) {
            diag.error(origin, "@ProtoOneof.Case without a scalar payload cannot declare type");
            ok = false;
        }
        return ok;
    }

    private boolean assignable(Element origin, TypeMirror ofType, TypeElement caseType, TypeMirror fieldType) {
        List<TypeMirror> bounds = new ArrayList<>();
        flatten(fieldType, bounds, new HashSet<>());
        for (TypeMirror bound : bounds) {
            if (!env.types.isAssignable(ofType, bound)) {
                diag.error(origin, "oneof case " + caseName(caseType, ofType)
                    + " is not assignable to " + env.simpleTypeName(bound));
                return false;
            }
        }
        return true;
    }

    private void flatten(TypeMirror type, List<TypeMirror> out, Set<Element> seen) {
        if (type == null) {
            return;
        }
        TypeKind kind = type.getKind();
        if (kind == TypeKind.TYPEVAR) {
            Element element = env.types.asElement(type);
            if (element != null && !seen.add(element)) {
                return;
            }
            flatten(((TypeVariable) type).getUpperBound(), out, seen);
            return;
        }
        if (kind == TypeKind.INTERSECTION) {
            for (TypeMirror bound : ((IntersectionType) type).getBounds()) {
                flatten(bound, out, seen);
            }
            return;
        }
        if (env.types.isSameType(env.types.erasure(type), env.types.erasure(env.objectType))) {
            return;
        }
        out.add(type);
    }

    private boolean overlapping(Element origin, List<ParsedCase> cases) {
        boolean overlap = false;
        for (int i = 0; i < cases.size(); i++) {
            for (int j = i + 1; j < cases.size(); j++) {
                TypeMirror a = cases.get(i).ofType;
                TypeMirror b = cases.get(j).ofType;
                String aName = caseName(cases.get(i).model.type, a);
                String bName = caseName(cases.get(j).model.type, b);
                if (env.types.isAssignable(a, b)) {
                    diag.error(origin, "oneof cases " + aName + " and " + bName
                        + " overlap: " + bName + " is assignable from " + aName);
                    overlap = true;
                } else if (env.types.isAssignable(b, a)) {
                    diag.error(origin, "oneof cases " + aName + " and " + bName
                        + " overlap: " + aName + " is assignable from " + bName);
                    overlap = true;
                }
            }
        }
        return overlap;
    }

    private boolean isNonContainerReference(TypeMirror type) {
        TypeKind kind = type.getKind();
        if (kind == TypeKind.TYPEVAR) {
            return true;
        }
        if (kind.isPrimitive() || kind == TypeKind.ARRAY) {
            return false;
        }
        return !env.isMap(type) && !env.isRepeatedContainer(type) && !env.isOptional(type);
    }

    private TypeMirror ofType(AnnotationMirror caseMirror, Element origin) {
        AnnotationValue value = adapters.annotationMember(caseMirror, "of");
        if (value == null || !(value.getValue() instanceof TypeMirror type)) {
            diag.error(origin, "oneof case type cannot be resolved");
            return null;
        }
        if (type.getKind() == TypeKind.ERROR) {
            diag.error(origin, "oneof case type " + type + " cannot be resolved");
            return null;
        }
        return type;
    }

    private ProtoType protoType(AnnotationMirror caseMirror) {
        AnnotationValue value = adapters.annotationMember(caseMirror, "type");
        if (value == null) {
            return ProtoType.AUTO;
        }
        Object raw = value.getValue();
        if (raw instanceof VariableElement ve) {
            return ProtoType.valueOf(ve.getSimpleName().toString());
        }
        if (raw instanceof ProtoType type) {
            return type;
        }
        return ProtoType.AUTO;
    }

    private int number(AnnotationMirror caseMirror) {
        AnnotationValue value = adapters.annotationMember(caseMirror, "number");
        if (value != null && value.getValue() instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private String typeName(TypeElement caseType, TypeMirror ofType, String pkg) {
        if (caseType != null) {
            return Names.typeName(caseType, pkg);
        }
        return env.renderType(ofType, pkg);
    }

    private String shapeMessage(TypeElement caseType, TypeMirror ofType) {
        return "oneof case " + caseName(caseType, ofType)
            + " must be a record, a @ProtoMessage, or a scalar/enum type";
    }

    private boolean looksLikeSingular(TypeMirror type, TypeElement fieldAdapter) {
        if (fieldAdapter != null || adapters.findDiscovered(type) != null) {
            return true;
        }
        TypeKind kind = type.getKind();
        if (kind.isPrimitive()
            || isByteArray(type)
            || env.isSame(type, env.stringType)
            || env.isSame(type, env.integerType)
            || env.isSame(type, env.longType)
            || env.isSame(type, env.floatType)
            || env.isSame(type, env.doubleType)
            || env.isSame(type, env.booleanType)
            || env.isAssignable(type, env.byteBufferType)) {
            return true;
        }
        TypeElement element = env.asTypeElement(type);
        if (element == null) {
            return false;
        }
        return element.getKind() == ElementKind.ENUM
            || TypeClassifier.WELL_KNOWN_CODECS.containsKey(element.getQualifiedName().toString())
            || adapters.findAnnotation(element, AdapterResolver.PROTO_ADAPTED_ANN) != null;
    }

    private static AnnotationMirror caseMirror(Object item) {
        if (item instanceof AnnotationMirror mirror) {
            return mirror;
        }
        if (item instanceof AnnotationValue value && value.getValue() instanceof AnnotationMirror mirror) {
            return mirror;
        }
        return null;
    }

    private static String caseName(TypeElement caseType, TypeMirror ofType) {
        if (caseType != null) {
            return caseType.getSimpleName().toString();
        }
        if (ofType.getKind() == TypeKind.ARRAY) {
            return "byte[]";
        }
        return ofType.toString();
    }

    private static boolean isByteArray(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY
            && ((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE;
    }

    private record ParsedCase(OneofCaseModel model, TypeMirror ofType) {
    }
}
