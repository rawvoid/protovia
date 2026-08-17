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

package io.github.rawvoid.protovia.processor.model;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.*;
import io.github.rawvoid.protovia.wire.WireType;
import lombok.AllArgsConstructor;

import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Validates {@code @ProtoMessage} / {@code @ProtoEnum} types and builds the models
 * consumed by {@link io.github.rawvoid.protovia.processor.gen.CodecGenerator}.
 *
 * @author Rawvoid
 */
public final class SchemaParser {

    private static final String PROTO_ADAPTER = "io.github.rawvoid.protovia.codec.ProtoAdapter";
    private static final String PROTO_ADAPTER_UNSET = "io.github.rawvoid.protovia.codec.ProtoAdapter.Unset";
    private static final String PROTO_FIELD_ANN = "io.github.rawvoid.protovia.annotation.ProtoField";
    private static final String PROTO_ONEOF_CASE_ANN = "io.github.rawvoid.protovia.annotation.ProtoOneofCase";
    private static final String PROTO_ADAPTERS_ANN = "io.github.rawvoid.protovia.annotation.ProtoAdapters";
    private static final String PROTO_ADAPTED_ANN = "io.github.rawvoid.protovia.annotation.ProtoAdapted";

    private static final Map<String, String> WELL_KNOWN_CODECS = Map.ofEntries(
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

    private final Types types;
    private final Elements elements;
    private final Messager messager;
    private final TypeMirror objectType;
    private final TypeMirror stringType;
    private final TypeMirror integerType;
    private final TypeMirror longType;
    private final TypeMirror floatType;
    private final TypeMirror doubleType;
    private final TypeMirror booleanType;
    private final TypeMirror byteBufferType;
    private final TypeMirror listType;
    private final TypeMirror setType;
    private final TypeMirror collectionType;
    private final TypeMirror mapType;
    private final TypeMirror optionalType;
    private final TypeElement protoAdapterType;
    private boolean errors;
    /** Parser-local; not stored on {@link MessageModel}. */
    private List<ResolvedAdapter> discovery = List.of();

    public SchemaParser(Types types, Elements elements, Messager messager) {
        this.types = types;
        this.elements = elements;
        this.messager = messager;
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
        this.protoAdapterType = elements.getTypeElement(PROTO_ADAPTER);
    }

    /**
     * @return {@code true} if any diagnostic error was reported in this parser
     */
    public boolean hasErrors() {
        return errors;
    }

    /**
     * @param type {@code @ProtoEnum} type
     * @return model, or {@code null} if the enum is invalid
     */
    public EnumModel parseEnum(TypeElement type) {
        boolean previous = errors;
        errors = false;
        EnumModel model = doParseEnum(type);
        errors = previous || errors;
        return model;
    }

    private EnumModel doParseEnum(TypeElement type) {
        if (type.getKind() != ElementKind.ENUM) {
            error(type, "@ProtoEnum is only valid on enum types");
            return null;
        }
        List<EnumModel.Constant> constants = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        boolean hasZero = false;
        String unrecognized = null;
        for (VariableElement constant : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (constant.getKind() != ElementKind.ENUM_CONSTANT) {
                continue;
            }
            boolean sentinel = constant.getAnnotation(ProtoUnrecognized.class) != null;
            ProtoEnumValue value = constant.getAnnotation(ProtoEnumValue.class);
            if (sentinel) {
                if (value != null) {
                    error(constant, "@ProtoUnrecognized cannot be combined with @ProtoEnumValue");
                    continue;
                }
                if (unrecognized != null) {
                    error(constant, "at most one @ProtoUnrecognized per enum");
                    continue;
                }
                unrecognized = constant.getSimpleName().toString();
                continue;
            }
            if (value == null) {
                error(constant, "enum constant " + constant.getSimpleName() + " must have @ProtoEnumValue");
                continue;
            }
            int number = value.value();
            if (!numbers.add(number)) {
                error(constant, "duplicate enum number " + number);
            }
            if (number == 0) {
                hasZero = true;
            }
            constants.add(new EnumModel.Constant(constant.getSimpleName().toString(), number));
        }
        if (!hasZero) {
            error(type, "proto3 enum " + type.getSimpleName() + " must have a constant with number 0");
        }
        if (errors) {
            return null;
        }
        String pkg = Names.packageName(type);
        return new EnumModel(type, Names.typeName(type, pkg), constants, unrecognized);
    }

    /**
     * @param type {@code @ProtoMessage} class or record
     * @return model, or {@code null} if the message is invalid
     */
    public MessageModel parseMessage(TypeElement type) {
        boolean previous = errors;
        errors = false;
        MessageModel model = doParseMessage(type);
        errors = previous || errors;
        return model;
    }

    private MessageModel doParseMessage(TypeElement type) {
        if (type.getKind() != ElementKind.CLASS && type.getKind() != ElementKind.RECORD) {
            error(type, "@ProtoMessage is only valid on classes and records");
            return null;
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            error(type, "@ProtoMessage type cannot be abstract");
        }
        if (type.getEnclosingElement().getKind() != ElementKind.PACKAGE
            && type.getKind() == ElementKind.CLASS
            && !type.getModifiers().contains(Modifier.STATIC)) {
            error(type, "non-static inner @ProtoMessage is not supported");
        }
        checkInheritance(type);

        boolean record = type.getKind() == ElementKind.RECORD;
        if (!record) {
            checkNoArgConstructor(type);
        }

        String pkg = Names.packageName(type);
        String typeName = Names.typeName(type, pkg);
        ProtoMessage meta = type.getAnnotation(ProtoMessage.class);
        String protoPackage = meta == null || meta.packageName().isBlank() ? "" : meta.packageName().trim();
        String protoMessageName = meta == null || meta.name().isBlank()
            ? type.getSimpleName().toString()
            : meta.name().trim();
        Map<Integer, FieldModel> byNumber = new LinkedHashMap<>();
        Set<Integer> taken = new HashSet<>();
        Set<String> claimed = new HashSet<>();
        List<FieldModel> oneofs = new ArrayList<>();

        List<MessageModel.RecordComponentModel> recordComponents = new ArrayList<>();
        MessageModel.UnknownField[] unknown = new MessageModel.UnknownField[1];
        List<ResolvedAdapter> previousDiscovery = discovery;
        // Package is getPackageOf(the message), never a oneof case type.
        discovery = buildDiscovery(type);
        try {
            if (record) {
                parseRecord(type, pkg, byNumber, taken, claimed, recordComponents, unknown, oneofs);
            } else {
                parsePojo(type, pkg, byNumber, taken, claimed, unknown, oneofs);
            }

            if (byNumber.isEmpty() && oneofs.isEmpty()) {
                error(type, "@ProtoMessage " + type.getSimpleName() + " has no @ProtoField or @ProtoOneof members");
            }
            if (errors) {
                return null;
            }
            List<FieldModel> fields = new ArrayList<>(byNumber.values());
            fields.sort(java.util.Comparator.comparingInt(f -> f.number));
            fields.addAll(oneofs);
            return new MessageModel(
                type,
                pkg,
                protoPackage,
                protoMessageName,
                typeName,
                Names.codecSimpleName(elements, type),
                record,
                fields,
                recordComponents,
                unknown[0]);
        } finally {
            discovery = previousDiscovery;
        }
    }

    private void parseRecord(
        TypeElement type,
        String pkg,
        Map<Integer, FieldModel> byNumber,
        Set<Integer> taken,
        Set<String> claimed,
        List<MessageModel.RecordComponentModel> recordComponents,
        MessageModel.UnknownField[] unknown,
        List<FieldModel> oneofs) {
        for (RecordComponentElement component : type.getRecordComponents()) {
            if (component.getAnnotation(ProtoUnknown.class) != null
                || (component.getAccessor() != null
                && component.getAccessor().getAnnotation(ProtoUnknown.class) != null)) {
                if (component.getAnnotation(ProtoField.class) != null) {
                    error(component, "cannot combine @ProtoUnknown with @ProtoField");
                    continue;
                }
                String name = component.getSimpleName().toString();
                if (bindUnknown(component, component.asType(), AccessKind.RECORD,
                    "value." + name + "()", null, name, unknown)) {
                    recordComponents.add(new MessageModel.RecordComponentModel(
                        name, component.asType(), null));
                }
                continue;
            }
            if (component.getAnnotation(ProtoOneof.class) != null
                || (component.getAccessor() != null
                && component.getAccessor().getAnnotation(ProtoOneof.class) != null)) {
                if (component.getAnnotation(ProtoField.class) != null) {
                    error(component, "cannot combine @ProtoOneof with @ProtoField");
                    continue;
                }
                String name = component.getSimpleName().toString();
                FieldModel oneof = resolveOneof(
                    component, name, component.asType(), AccessKind.RECORD,
                    "value." + name + "()", null, name, pkg, taken);
                if (oneof != null && claimed.add(name)) {
                    oneofs.add(oneof);
                    recordComponents.add(new MessageModel.RecordComponentModel(
                        name, component.asType(), oneof));
                }
                continue;
            }
            ProtoField ann = component.getAnnotation(ProtoField.class);
            if (ann == null) {
                ExecutableElement accessor = component.getAccessor();
                if (accessor != null) {
                    ann = accessor.getAnnotation(ProtoField.class);
                }
            }
            String name = component.getSimpleName().toString();
            if (ann == null) {
                recordComponents.add(new MessageModel.RecordComponentModel(
                    name, component.asType(), null));
                continue;
            }
            FieldModel field = resolveField(
                component,
                name,
                component.asType(),
                ann,
                AccessKind.RECORD,
                "value." + name + "()",
                null,
                name,
                pkg);
            if (field != null && addField(byNumber, taken, claimed, field)) {
                recordComponents.add(new MessageModel.RecordComponentModel(
                    name, component.asType(), field));
            } else {
                recordComponents.add(new MessageModel.RecordComponentModel(
                    name, component.asType(), null));
            }
        }
    }

    private void parsePojo(
        TypeElement type,
        String pkg,
        Map<Integer, FieldModel> byNumber,
        Set<Integer> taken,
        Set<String> claimed,
        MessageModel.UnknownField[] unknown,
        List<FieldModel> oneofs) {
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

        Set<String> annotatedViaField = new HashSet<>();
        for (VariableElement field : fields.values()) {
            if (field.getAnnotation(ProtoOneof.class) != null) {
                if (field.getAnnotation(ProtoField.class) != null) {
                    error(field, "cannot combine @ProtoOneof with @ProtoField");
                    continue;
                }
                String name = field.getSimpleName().toString();
                Access access = resolvePojoAccess(type, field, name, field.asType(), methods);
                if (access == null) {
                    continue;
                }
                FieldModel oneof = resolveOneof(
                    field, name, field.asType(), access.kind, access.readExpr, access.setter, name, pkg, taken);
                if (oneof != null && claimed.add(name)) {
                    annotatedViaField.add(name);
                    oneofs.add(oneof);
                }
                continue;
            }
            if (field.getAnnotation(ProtoUnknown.class) != null) {
                if (field.getAnnotation(ProtoField.class) != null) {
                    error(field, "cannot combine @ProtoUnknown with @ProtoField");
                    continue;
                }
                String name = field.getSimpleName().toString();
                Access access = resolvePojoAccess(type, field, name, field.asType(), methods);
                if (access != null) {
                    bindUnknown(field, field.asType(), access.kind, access.readExpr, access.setter, name, unknown);
                }
                continue;
            }
            ProtoField ann = field.getAnnotation(ProtoField.class);
            if (ann == null) {
                continue;
            }
            String name = field.getSimpleName().toString();
            annotatedViaField.add(name);
            Access access = resolvePojoAccess(type, field, name, field.asType(), methods);
            if (access == null) {
                continue;
            }
            FieldModel model = resolveField(
                field, name, field.asType(), ann, access.kind, access.readExpr, access.setter, name, pkg);
            if (model != null) {
                addField(byNumber, taken, claimed, model);
            }
        }

        for (ExecutableElement method : methods.values()) {
            if (method.getAnnotation(ProtoOneof.class) != null) {
                if (method.getAnnotation(ProtoField.class) != null) {
                    error(method, "cannot combine @ProtoOneof with @ProtoField");
                    continue;
                }
                String property = Names.propertyFromGetter(method.getSimpleName().toString());
                if (property == null || !method.getParameters().isEmpty()
                    || method.getReturnType().getKind() == TypeKind.VOID) {
                    error(method, "@ProtoOneof on a method must be a JavaBean getter");
                    continue;
                }
                if (annotatedViaField.contains(property)) {
                    error(method, "field '" + property + "' is already annotated; do not also annotate the getter");
                    continue;
                }
                String setter = Names.setterName(property);
                ExecutableElement set = methods.get(setter);
                if (set == null || set.getParameters().size() != 1) {
                    error(method, "annotated getter '" + method.getSimpleName() + "' has no matching setter " + setter);
                    continue;
                }
                FieldModel oneof = resolveOneof(
                    method,
                    property,
                    method.getReturnType(),
                    AccessKind.GETTER_SETTER,
                    "value." + method.getSimpleName() + "()",
                    setter,
                    property,
                    pkg,
                    taken);
                if (oneof != null && claimed.add(property)) {
                    annotatedViaField.add(property);
                    oneofs.add(oneof);
                }
                continue;
            }
            if (method.getAnnotation(ProtoUnknown.class) != null) {
                if (method.getAnnotation(ProtoField.class) != null) {
                    error(method, "cannot combine @ProtoUnknown with @ProtoField");
                    continue;
                }
                String property = Names.propertyFromGetter(method.getSimpleName().toString());
                if (property == null) {
                    error(method, "@ProtoUnknown on a method must be a JavaBean getter");
                    continue;
                }
                String setter = Names.setterName(property);
                ExecutableElement set = methods.get(setter);
                if (set == null || set.getParameters().size() != 1) {
                    error(method, "annotated getter '" + method.getSimpleName() + "' has no matching setter " + setter);
                    continue;
                }
                bindUnknown(
                    method,
                    method.getReturnType(),
                    AccessKind.GETTER_SETTER,
                    "value." + method.getSimpleName() + "()",
                    setter,
                    property,
                    unknown);
                continue;
            }
            ProtoField ann = method.getAnnotation(ProtoField.class);
            if (ann == null) {
                continue;
            }
            String property = Names.propertyFromGetter(method.getSimpleName().toString());
            if (property == null || !method.getParameters().isEmpty() || method.getReturnType().getKind() == TypeKind.VOID) {
                error(method, "@ProtoField on a method must be a JavaBean getter");
                continue;
            }
            if (annotatedViaField.contains(property)) {
                error(method, "field '" + property + "' is already annotated; do not also annotate the getter");
                continue;
            }
            String setter = Names.setterName(property);
            ExecutableElement set = methods.get(setter);
            if (set == null || set.getParameters().size() != 1) {
                error(method, "annotated getter '" + method.getSimpleName() + "' has no matching setter " + setter);
                continue;
            }
            FieldModel model = resolveField(
                method,
                property,
                method.getReturnType(),
                ann,
                AccessKind.GETTER_SETTER,
                "value." + method.getSimpleName() + "()",
                setter,
                property,
                pkg);
            if (model != null) {
                addField(byNumber, taken, claimed, model);
            }
        }
    }

    private boolean bindUnknown(
        Element origin,
        TypeMirror type,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String name,
        MessageModel.UnknownField[] unknown) {
        TypeElement expected = elements.getTypeElement("io.github.rawvoid.protovia.UnknownFields");
        if (expected == null || !types.isSameType(types.erasure(type), expected.asType())) {
            error(origin, "@ProtoUnknown must be of type UnknownFields");
            return false;
        }
        if (unknown[0] != null) {
            error(origin, "at most one @ProtoUnknown per message");
            return false;
        }
        unknown[0] = new MessageModel.UnknownField(
            accessKind,
            name,
            Names.safeLocal(name),
            readExpr,
            setter,
            name);
        return true;
    }

    private Access resolvePojoAccess(
        TypeElement type,
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
        boolean getterOk = getter != null && getter.getParameters().isEmpty() && !getter.getModifiers().contains(Modifier.PRIVATE);
        boolean setterOk = setter != null && setter.getParameters().size() == 1 && !setter.getModifiers().contains(Modifier.PRIVATE);
        if (getterOk && setterOk) {
            return new Access(AccessKind.GETTER_SETTER, "value." + getter.getSimpleName() + "()", setter.getSimpleName().toString());
        }
        if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            return new Access(AccessKind.FIELD, "value." + name, null);
        }
        error(field, "private field '" + name + "' needs a JavaBean getter and setter, or must not be private");
        return null;
    }

    private boolean addField(
        Map<Integer, FieldModel> byNumber, Set<Integer> taken, Set<String> claimed, FieldModel field) {
        if (!WireType.isValidFieldNumber(field.number)) {
            error(field.origin, "invalid field number " + field.number
                + " (must be in [1, 536870911] and not in [19000, 19999])");
            return false;
        }
        if (!taken.add(field.number)) {
            error(field.origin, "duplicate field number " + field.number);
            return false;
        }
        if (!claimed.add(field.name)) {
            error(field.origin, "duplicate proto field name '" + field.name + "'");
            return false;
        }
        byNumber.put(field.number, field);
        return true;
    }

    private FieldModel resolveOneof(
        Element origin,
        String name,
        TypeMirror type,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        Set<Integer> taken) {
        TypeElement sealed = asTypeElement(type);
        if (sealed == null || !sealed.getModifiers().contains(Modifier.SEALED)) {
            error(origin, "@ProtoOneof field '" + name + "' must be a sealed interface or class");
            return null;
        }
        List<? extends TypeMirror> permitted = sealed.getPermittedSubclasses();
        if (permitted.size() < 2) {
            error(origin, "oneof '" + name + "' must have at least two @ProtoOneofCase types");
            return null;
        }
        List<OneofCaseModel> cases = new ArrayList<>();
        for (TypeMirror permittedType : permitted) {
            TypeElement caseType = asTypeElement(permittedType);
            if (caseType == null) {
                error(origin, "oneof '" + name + "' has an unresolved permitted type");
                continue;
            }
            ProtoOneofCase caseAnn = caseType.getAnnotation(ProtoOneofCase.class);
            if (caseAnn == null) {
                error(caseType, caseType.getSimpleName() + " must be annotated with @ProtoOneofCase");
                continue;
            }
            int number = caseAnn.value();
            if (!WireType.isValidFieldNumber(number)) {
                error(caseType, "invalid field number " + number);
                continue;
            }
            if (!taken.add(number)) {
                error(caseType, "duplicate field number " + number);
                continue;
            }
            OneofCaseModel parsed = parseOneofCase(caseType, number, name, pkg);
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
            .javaTypeName(renderType(type, pkg))
            .javaType(type)
            .oneofCases(cases)
            .origin(origin)
            .build();
    }

    private OneofCaseModel parseOneofCase(TypeElement caseType, int number, String oneofName, String pkg) {
        String typeName = Names.typeName(caseType, pkg);
        String tag = Names.tagConstant(number);
        TypeElement fieldAdapter = adapterFrom(caseType, PROTO_ONEOF_CASE_ANN);
        if (caseType.getAnnotation(ProtoMessage.class) != null) {
            rejectOneofAdapter(caseType, fieldAdapter);
            String codec = Names.codecSimpleName(elements, caseType);
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
            error(caseType, "@ProtoOneofCase " + caseType.getSimpleName()
                + " must be a record with 0 or 1 component, or a @ProtoMessage");
            return null;
        }
        List<? extends RecordComponentElement> components = caseType.getRecordComponents();
        if (components.isEmpty()) {
            rejectOneofAdapter(caseType, fieldAdapter);
            return new OneofCaseModel(number, caseType, typeName, tag, null, null, false);
        }
        if (components.size() != 1) {
            error(caseType, "@ProtoOneofCase record " + caseType.getSimpleName()
                + " must have 0 or 1 component");
            return null;
        }
        RecordComponentElement component = components.get(0);
        TypeMirror payloadType = component.asType();
        if (isMap(payloadType) || isRepeatedContainer(payloadType)
            && !(payloadType.getKind() == TypeKind.ARRAY
            && ((ArrayType) payloadType).getComponentType().getKind() == TypeKind.BYTE)) {
            error(caseType, "oneof case cannot be repeated or map");
            return null;
        }
        ProtoOneofCase caseAnn = caseType.getAnnotation(ProtoOneofCase.class);
        ProtoType declared = caseAnn == null ? ProtoType.AUTO : caseAnn.type();
        FieldModel payload = resolveSingular(
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
            AdapterSite.ONEOF);
        if (payload == null) {
            return null;
        }
        return new OneofCaseModel(
            number, caseType, typeName, tag, payload, component.getSimpleName() + "()", false);
    }

    private FieldModel resolveField(
        Element origin,
        String name,
        TypeMirror type,
        ProtoField ann,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg) {
        boolean javaOptional = isOptional(type);
        TypeMirror effective = type;
        if (javaOptional) {
            effective = typeArgument(type, 0, origin, "Optional");
            if (effective == null) {
                return null;
            }
        }
        boolean optional = ann.optional() || javaOptional;
        TypeElement fieldAdapter = adapterFrom(protoFieldHost(origin), PROTO_FIELD_ANN);
        if (isMap(effective)) {
            if (optional) {
                error(origin, "map field '" + name + "' cannot be optional");
                return null;
            }
            return resolveMap(
                origin, name, effective, ann, accessKind, readExpr, setter, fieldName, pkg, javaOptional, fieldAdapter);
        }
        if (isRepeatedContainer(effective)) {
            if (optional) {
                error(origin, "repeated field '" + name + "' cannot be optional");
                return null;
            }
            return resolveRepeated(
                origin, name, effective, ann, accessKind, readExpr, setter, fieldName, pkg, javaOptional, fieldAdapter);
        }
        return resolveSingular(
            origin, name, effective, ann.type(), optional, ann.packed(),
            accessKind, readExpr, setter, fieldName, pkg, javaOptional, type,
            fieldAdapter, ann.number(), AdapterSite.SINGULAR);
    }

    private FieldModel resolveRepeated(
        Element origin,
        String name,
        TypeMirror type,
        ProtoField ann,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        TypeElement fieldAdapter) {
        boolean array = type.getKind() == TypeKind.ARRAY;
        TypeMirror elementType;
        if (array) {
            elementType = ((ArrayType) type).getComponentType();
            if (elementType.getKind() == TypeKind.BYTE) {
                return resolveSingular(
                    origin, name, type, protoOrAuto(ann.type(), ProtoType.BYTES),
                    ann.optional(), ann.packed(), accessKind, readExpr, setter, fieldName, pkg, javaOptional, type,
                    fieldAdapter, ann.number(), AdapterSite.SINGULAR);
            }
        } else {
            elementType = typeArgument(type, 0, origin, "collection");
            if (elementType == null) {
                return null;
            }
        }
        FieldModel element = resolveSingular(
            origin, name + "Element", elementType, protoOrAuto(ann.type(), ProtoType.AUTO),
            false, false, accessKind, null, null, null, pkg, false, elementType,
            fieldAdapter, ann.number(), AdapterSite.REPEATED);
        if (element == null) {
            return null;
        }
        TypeElement impl = array ? null : collectionImpl(type, element);
        boolean packed = ann.packed() && isPackable(element);
        FieldModel.Builder b = FieldModel.builder()
            .number(ann.number())
            .name(name)
            .localName(Names.safeLocal(name))
            .kind(FieldKind.REPEATED)
            .protoType(element.protoType)
            .optional(false)
            .packed(packed)
            .javaOptional(javaOptional)
            .accessKind(accessKind)
            .readExpr(readExpr)
            .setterName(setter)
            .fieldName(fieldName)
            .javaTypeName(renderType(type, pkg))
            .javaType(type)
            .implTypeName(impl == null ? null : impl.getQualifiedName().toString())
            .implType(impl)
            .element(element)
            .origin(origin)
            .array(array);
        if (array) {
            b.arrayComponentType(renderType(elementType, pkg));
        }
        return b.build();
    }

    private FieldModel resolveMap(
        Element origin,
        String name,
        TypeMirror type,
        ProtoField ann,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        TypeElement fieldAdapter) {
        TypeMirror keyType = typeArgument(type, 0, origin, "Map");
        TypeMirror valueType = typeArgument(type, 1, origin, "Map");
        if (keyType == null || valueType == null) {
            return null;
        }
        if (isMap(valueType)) {
            error(origin, "map-of-map is not supported for field '" + name + "'");
            return null;
        }
        FieldModel key = resolveSingular(
            origin, name + "Key", keyType, protoOrAuto(ann.keyType(), ProtoType.AUTO),
            false, false, accessKind, null, null, null, pkg, false, keyType,
            fieldAdapter, ann.number(), AdapterSite.MAP);
        FieldModel value = resolveSingular(
            origin, name + "Value", valueType, protoOrAuto(ann.valueType(), ProtoType.AUTO),
            false, false, accessKind, null, null, null, pkg, false, valueType,
            fieldAdapter, ann.number(), AdapterSite.MAP);
        if (key == null || value == null) {
            return null;
        }
        if (fieldAdapter != null) {
            ResolvedAdapter adapter = validateAdapter(fieldAdapter, origin);
            if (adapter != null
                && !types.isSameType(adapter.j, keyType)
                && !types.isSameType(adapter.j, valueType)) {
                error(origin, "adapter " + fieldAdapter.getSimpleName()
                    + " handles " + simpleTypeName(adapter.j) + ", not " + simpleTypeName(type));
                return null;
            }
        }
        if (!isValidMapKey(key.protoType)) {
            error(origin, "map key of field '" + name + "' must be an integral type, bool, or string");
            return null;
        }
        TypeElement impl = mapImpl(type);
        return FieldModel.builder()
            .number(ann.number())
            .name(name)
            .localName(Names.safeLocal(name))
            .kind(FieldKind.MAP)
            .optional(false)
            .accessKind(accessKind)
            .readExpr(readExpr)
            .setterName(setter)
            .fieldName(fieldName)
            .javaTypeName(renderType(type, pkg))
            .javaType(type)
            .implTypeName(impl.getQualifiedName().toString())
            .implType(impl)
            .mapKey(key)
            .mapValue(value)
            .origin(origin)
            .build();
    }

    private FieldModel resolveSingular(
        Element origin,
        String name,
        TypeMirror type,
        ProtoType declared,
        boolean optional,
        boolean packed,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        TypeMirror declaredJavaType,
        TypeElement fieldAdapter,
        int number,
        AdapterSite site) {
        if (type.getKind().isPrimitive() && optional) {
            error(origin, "optional field '" + name + "' cannot be a primitive; use a boxed type or Optional");
            return null;
        }
        ResolvedAdapter discovered = findDiscovered(type);
        if (type.getKind().isPrimitive() && (fieldAdapter != null || discovered != null)) {
            error(origin, "adapter cannot be applied to primitive field '" + name + "'");
            return null;
        }
        if (fieldAdapter != null) {
            ResolvedAdapter adapter = validateAdapter(fieldAdapter, origin);
            if (adapter == null) {
                return null;
            }
            if (!types.isSameType(adapter.j, type)) {
                if (site != AdapterSite.MAP) {
                    error(origin, "adapter " + fieldAdapter.getSimpleName()
                        + " handles " + simpleTypeName(adapter.j) + ", not " + simpleTypeName(type));
                    return null;
                }
            } else {
                return applyAdapter(
                    adapter, origin, name, declaredJavaType, declared, optional, packed,
                    accessKind, readExpr, setter, fieldName, pkg, javaOptional, number);
            }
        }
        if (discovered != null) {
            return applyAdapter(
                discovered, origin, name, declaredJavaType, declared, optional, packed,
                accessKind, readExpr, setter, fieldName, pkg, javaOptional, number);
        }
        TypeElement javaType = asTypeElement(type);
        if (javaType != null && findAnnotation(javaType, PROTO_ADAPTED_ANN) != null) {
            return resolveProtoAdapted(
                javaType, origin, name, type, declaredJavaType, declared, optional, packed,
                accessKind, readExpr, setter, fieldName, pkg, javaOptional, number);
        }
        Resolved resolved = classify(origin, name, type, declared, pkg);
        if (resolved == null) {
            return null;
        }
        return FieldModel.builder()
            .number(number)
            .name(name)
            .localName(Names.safeLocal(name))
            .kind(resolved.kind)
            .protoType(resolved.protoType)
            .optional(optional)
            .packed(packed)
            .primitive(type.getKind().isPrimitive())
            .javaOptional(javaOptional)
            .byteArray(resolved.byteArray)
            .byteBuffer(resolved.byteBuffer)
            .accessKind(accessKind)
            .readExpr(readExpr)
            .setterName(setter)
            .fieldName(fieldName)
            .javaTypeName(renderType(declaredJavaType, pkg))
            .javaType(declaredJavaType)
            .codecName(resolved.codecName)
            .enumModel(resolved.enumModel)
            .messageType(resolved.messageType)
            .origin(origin)
            .build();
    }

    private FieldModel adaptedSingular(
        Element origin,
        String name,
        TypeMirror declaredJavaType,
        ProtoType protoType,
        ResolvedAdapter adapter,
        boolean optional,
        boolean packed,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        int number) {
        return FieldModel.builder()
            .number(number)
            .name(name)
            .localName(Names.safeLocal(name))
            .kind(FieldKind.SCALAR)
            .protoType(protoType)
            .optional(optional)
            .packed(packed)
            .primitive(false)
            .javaOptional(javaOptional)
            .accessKind(accessKind)
            .readExpr(readExpr)
            .setterName(setter)
            .fieldName(fieldName)
            .javaTypeName(renderType(declaredJavaType, pkg))
            .javaType(declaredJavaType)
            .adapterType(adapter.adapterType)
            .wireJavaType(adapter.w)
            .origin(origin)
            .build();
    }

    private FieldModel applyAdapter(
        ResolvedAdapter adapter,
        Element origin,
        String name,
        TypeMirror declaredJavaType,
        ProtoType declared,
        boolean optional,
        boolean packed,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        int number) {
        ProtoType protoType = bindAdapterProtoType(adapter, declared, origin, name);
        if (protoType == null) {
            return null;
        }
        return adaptedSingular(
            origin, name, declaredJavaType, protoType, adapter, optional, packed,
            accessKind, readExpr, setter, fieldName, pkg, javaOptional, number);
    }

    private FieldModel resolveProtoAdapted(
        TypeElement javaType,
        Element origin,
        String name,
        TypeMirror type,
        TypeMirror declaredJavaType,
        ProtoType declared,
        boolean optional,
        boolean packed,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        int number) {
        TypeElement adaptedType = adaptedFrom(javaType);
        if (adaptedType == null) {
            return null;
        }
        ResolvedAdapter adapter = javaType.equals(origin)
            ? validateAdapter(adaptedType, origin)
            : validateAdapter(adaptedType, javaType, origin);
        if (adapter == null) {
            return null;
        }
        if (alreadyProtoType(type)) {
            String message = protoAdaptedOnProtoTypeMessage(javaType);
            error(javaType, message);
            if (!javaType.equals(origin)) {
                error(origin, message);
            }
            return null;
        }
        if (!types.isSameType(adapter.j, type)) {
            String message = "adapter " + adaptedType.getSimpleName()
                + " handles " + simpleTypeName(adapter.j) + ", not " + simpleTypeName(type);
            error(javaType, message);
            if (!javaType.equals(origin)) {
                error(origin, message);
            }
            return null;
        }
        return applyAdapter(
            adapter, origin, name, declaredJavaType, declared, optional, packed,
            accessKind, readExpr, setter, fieldName, pkg, javaOptional, number);
    }

    private void rejectOneofAdapter(Element origin, TypeElement fieldAdapter) {
        if (fieldAdapter != null) {
            error(origin, "@ProtoOneofCase without a scalar payload cannot declare adapter");
        }
    }

    private List<ResolvedAdapter> buildDiscovery(TypeElement messageType) {
        List<ResolvedAdapter> list = new ArrayList<>();
        PackageElement pkg = elements.getPackageOf(messageType);
        addAdapters(list, adaptersFrom(pkg), pkg);
        addAdapters(list, adaptersFrom(messageType), messageType);
        return list;
    }

    private void addAdapters(List<ResolvedAdapter> discovery, List<TypeElement> adapters, Element origin) {
        List<ResolvedAdapter> fromThis = new ArrayList<>();
        for (TypeElement adapter : adapters) {
            ResolvedAdapter resolved = validateAdapter(adapter, origin);
            if (resolved == null) {
                continue;
            }
            boolean duplicate = false;
            for (ResolvedAdapter existing : fromThis) {
                if (types.isSameType(existing.j, resolved.j)) {
                    error(origin, "duplicate adapter for " + simpleTypeName(resolved.j));
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                fromThis.add(resolved);
            }
        }
        for (ResolvedAdapter next : fromThis) {
            discovery.removeIf(existing -> types.isSameType(existing.j, next.j));
            discovery.add(next);
        }
    }

    private ResolvedAdapter findDiscovered(TypeMirror javaJ) {
        for (ResolvedAdapter adapter : discovery) {
            if (types.isSameType(adapter.j, javaJ)) {
                return adapter;
            }
        }
        return null;
    }

    private boolean alreadyProtoType(TypeMirror javaJ) {
        TypeElement element = asTypeElement(javaJ);
        if (element == null) {
            return false;
        }
        if (element.getAnnotation(ProtoMessage.class) != null
            || element.getAnnotation(ProtoEnum.class) != null) {
            return true;
        }
        return WELL_KNOWN_CODECS.containsKey(element.getQualifiedName().toString());
    }

    private String protoAdaptedOnProtoTypeMessage(TypeElement type) {
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

    private List<TypeElement> adaptersFrom(Element typeOrPackage) {
        AnnotationMirror mirror = findAnnotation(typeOrPackage, PROTO_ADAPTERS_ANN);
        if (mirror == null) {
            return List.of();
        }
        AnnotationValue value = annotationMember(mirror, "value");
        if (value == null || !(value.getValue() instanceof List<?> items)) {
            return List.of();
        }
        List<TypeElement> adapters = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof AnnotationValue av)) {
                continue;
            }
            TypeElement adapter = typeElementFrom(av, typeOrPackage, "adapter");
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        return adapters;
    }

    private TypeElement adaptedFrom(TypeElement javaType) {
        AnnotationMirror mirror = findAnnotation(javaType, PROTO_ADAPTED_ANN);
        if (mirror == null) {
            return null;
        }
        return typeElementFrom(annotationMember(mirror, "value"), javaType, "adapter");
    }

    private TypeElement adapterFrom(Element origin, String annotationName) {
        AnnotationMirror mirror = findAnnotation(origin, annotationName);
        if (mirror == null) {
            return null;
        }
        TypeElement adapter = typeElementFrom(annotationMember(mirror, "adapter"), origin, "adapter");
        if (adapter == null) {
            return null;
        }
        if (adapter.getQualifiedName().contentEquals(PROTO_ADAPTER_UNSET)) {
            return null;
        }
        return adapter;
    }

    private TypeElement typeElementFrom(AnnotationValue value, Element origin, String what) {
        if (value == null || !(value.getValue() instanceof TypeMirror type)) {
            return null;
        }
        if (type.getKind() == TypeKind.ERROR) {
            error(origin, what + " " + type + " cannot be resolved");
            return null;
        }
        TypeElement element = asTypeElement(type);
        if (element == null) {
            error(origin, what + " " + type + " cannot be resolved");
            return null;
        }
        return element;
    }

    private AnnotationValue annotationMember(AnnotationMirror mirror, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
            : elements.getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Element protoFieldHost(Element origin) {
        if (findAnnotation(origin, PROTO_FIELD_ANN) != null) {
            return origin;
        }
        if (origin.getKind() == ElementKind.RECORD_COMPONENT) {
            RecordComponentElement component = (RecordComponentElement) origin;
            if (component.getAccessor() != null
                && findAnnotation(component.getAccessor(), PROTO_FIELD_ANN) != null) {
                return component.getAccessor();
            }
        }
        return origin;
    }

    private AnnotationMirror findAnnotation(Element origin, String annotationName) {
        for (AnnotationMirror mirror : origin.getAnnotationMirrors()) {
            Element annotation = mirror.getAnnotationType().asElement();
            if (annotation instanceof TypeElement type
                && type.getQualifiedName().contentEquals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    private ResolvedAdapter validateAdapter(TypeElement adapter, Element... origins) {
        String adapterName = adapter.getSimpleName().toString();
        if (adapter.getKind() != ElementKind.CLASS
            || adapter.getQualifiedName().contentEquals(PROTO_ADAPTER)) {
            error("adapter must be a concrete type, not ProtoAdapter", origins);
            return null;
        }
        if (!isPublicType(adapter)) {
            error("adapter " + adapterName + " must be a public type", origins);
            return null;
        }
        DeclaredType iface = findProtoAdapterIface(adapter);
        if (iface == null) {
            error("adapter " + adapterName + " must implement ProtoAdapter", origins);
            return null;
        }
        ProtoScalar scalar = adapter.getAnnotation(ProtoScalar.class);
        if (scalar == null) {
            error("adapter " + adapterName + " must be annotated with @ProtoScalar", origins);
            return null;
        }
        ProtoType protoType = scalar.value();
        if (protoType == ProtoType.AUTO || protoType == ProtoType.ENUM || protoType == ProtoType.MESSAGE) {
            error("@ProtoScalar must name a scalar ProtoType", origins);
            return null;
        }
        if (!hasInstance(adapter)) {
            error("adapter " + adapterName + " must declare public static final INSTANCE", origins);
            return null;
        }
        List<? extends TypeMirror> args = iface.getTypeArguments();
        if (args.size() != 2) {
            error("adapter " + adapterName + " must bind ProtoAdapter type parameters", origins);
            return null;
        }
        TypeMirror j = args.get(0);
        TypeMirror w = args.get(1);
        if (!isResolvedType(j) || !isResolvedType(w)) {
            error("adapter " + adapterName + " must bind ProtoAdapter type parameters", origins);
            return null;
        }
        if (j instanceof DeclaredType declaredJ && !declaredJ.getTypeArguments().isEmpty()) {
            error("adapter J must be a non-parameterized class", origins);
            return null;
        }
        if (!isRequiredWireType(w, protoType)) {
            error("adapter " + adapterName + " ProtoType." + protoType
                + " requires " + requiredWireName(protoType) + ", not " + simpleTypeName(w), origins);
            return null;
        }
        return new ResolvedAdapter(adapter, j, w, protoType);
    }

    private void error(String message, Element... origins) {
        for (Element origin : origins) {
            error(origin, message);
        }
    }

    private ProtoType bindAdapterProtoType(ResolvedAdapter adapter, ProtoType declared, Element origin, String name) {
        if (declared == ProtoType.AUTO) {
            return adapter.p;
        }
        if (!familyOf(adapter.p).contains(declared)) {
            error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
            return null;
        }
        return declared;
    }

    private DeclaredType findProtoAdapterIface(TypeElement adapter) {
        if (protoAdapterType == null) {
            return null;
        }
        TypeMirror target = types.erasure(protoAdapterType.asType());
        Deque<TypeMirror> queue = new ArrayDeque<>();
        queue.add(adapter.asType());
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            TypeMirror current = queue.removeFirst();
            if (!(current instanceof DeclaredType declared)) {
                continue;
            }
            TypeElement element = asTypeElement(declared);
            if (element == null || !seen.add(element.getQualifiedName().toString())) {
                continue;
            }
            if (types.isSameType(types.erasure(declared), target)) {
                return declared;
            }
            queue.addAll(types.directSupertypes(declared));
        }
        return null;
    }

    private boolean isPublicType(TypeElement type) {
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

    private boolean hasInstance(TypeElement adapter) {
        for (VariableElement field : ElementFilter.fieldsIn(adapter.getEnclosedElements())) {
            if (!field.getSimpleName().contentEquals("INSTANCE")) {
                continue;
            }
            Set<Modifier> mods = field.getModifiers();
            if (mods.contains(Modifier.PUBLIC)
                && mods.contains(Modifier.STATIC)
                && mods.contains(Modifier.FINAL)
                && types.isAssignable(field.asType(), adapter.asType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isResolvedType(TypeMirror type) {
        TypeKind kind = type.getKind();
        return kind != TypeKind.WILDCARD && kind != TypeKind.TYPEVAR && kind != TypeKind.ERROR;
    }

    private boolean isRequiredWireType(TypeMirror w, ProtoType protoType) {
        return switch (protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> isSame(w, integerType);
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> isSame(w, longType);
            case BOOL -> isSame(w, booleanType);
            case FLOAT -> isSame(w, floatType);
            case DOUBLE -> isSame(w, doubleType);
            case STRING -> isSame(w, stringType);
            case BYTES -> w.getKind() == TypeKind.ARRAY
                && ((ArrayType) w).getComponentType().getKind() == TypeKind.BYTE;
            default -> false;
        };
    }

    private String requiredWireName(ProtoType protoType) {
        return switch (protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "Integer";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "Long";
            case BOOL -> "Boolean";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case STRING -> "String";
            case BYTES -> "byte[]";
            default -> protoType.name();
        };
    }

    private Set<ProtoType> familyOf(ProtoType protoType) {
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

    private String simpleTypeName(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return simpleTypeName(((ArrayType) type).getComponentType()) + "[]";
        }
        TypeElement element = asTypeElement(type);
        return element != null ? element.getSimpleName().toString() : type.toString();
    }

    private Resolved classify(Element origin, String name, TypeMirror type, ProtoType declared, String currentPkg) {
        TypeKind kind = type.getKind();
        if (kind == TypeKind.INT || isSame(type, integerType)) {
            return scalar(origin, name, declared, ProtoType.INT32, intFamily());
        }
        if (kind == TypeKind.LONG || isSame(type, longType)) {
            return scalar(origin, name, declared, ProtoType.INT64, longFamily());
        }
        if (kind == TypeKind.FLOAT || isSame(type, floatType)) {
            return scalar(origin, name, declared, ProtoType.FLOAT, Set.of(ProtoType.AUTO, ProtoType.FLOAT));
        }
        if (kind == TypeKind.DOUBLE || isSame(type, doubleType)) {
            return scalar(origin, name, declared, ProtoType.DOUBLE, Set.of(ProtoType.AUTO, ProtoType.DOUBLE));
        }
        if (kind == TypeKind.BOOLEAN || isSame(type, booleanType)) {
            return scalar(origin, name, declared, ProtoType.BOOL, Set.of(ProtoType.AUTO, ProtoType.BOOL));
        }
        if (isSame(type, stringType)) {
            return scalar(origin, name, declared, ProtoType.STRING, Set.of(ProtoType.AUTO, ProtoType.STRING));
        }
        if (kind == TypeKind.ARRAY && ((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE) {
            Resolved r = scalar(origin, name, declared, ProtoType.BYTES, Set.of(ProtoType.AUTO, ProtoType.BYTES));
            if (r != null) {
                r.byteArray = true;
            }
            return r;
        }
        if (isAssignable(type, byteBufferType)) {
            Resolved r = scalar(origin, name, declared, ProtoType.BYTES, Set.of(ProtoType.AUTO, ProtoType.BYTES));
            if (r != null) {
                r.byteBuffer = true;
            }
            return r;
        }
        TypeElement element = asTypeElement(type);
        if (element == null) {
            error(origin, "unsupported type for field '" + name + "': " + type);
            return null;
        }
        String wellKnownCodec = WELL_KNOWN_CODECS.get(element.getQualifiedName().toString());
        if (wellKnownCodec != null) {
            return wellKnown(origin, name, declared, wellKnownCodec);
        }
        if (element.getKind() == ElementKind.ENUM) {
            if (element.getAnnotation(ProtoEnum.class) == null) {
                error(origin, "enum field '" + name + "' type " + element.getSimpleName() + " must be annotated with @ProtoEnum");
                return null;
            }
            if (declared != ProtoType.AUTO && declared != ProtoType.ENUM) {
                error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
                return null;
            }
            EnumModel enumModel = parseEnum(element);
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
                error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
                return null;
            }
            Resolved r = new Resolved();
            r.kind = FieldKind.MESSAGE;
            r.protoType = ProtoType.MESSAGE;
            r.messageType = element;
            r.codecName = Names.codecSimpleName(elements, element);
            String codecPkg = Names.packageName(element);
            if (!codecPkg.equals(currentPkg) && !codecPkg.isEmpty()) {
                r.codecName = codecPkg + "." + r.codecName;
            }
            return r;
        }
        error(origin, "unsupported type for field '" + name + "': " + element.getQualifiedName()
            + " (annotate with @ProtoMessage / @ProtoEnum)");
        return null;
    }

    private Resolved wellKnown(Element origin, String name, ProtoType declared, String codec) {
        if (declared != ProtoType.AUTO && declared != ProtoType.MESSAGE) {
            error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
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
            error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
            return null;
        }
        Resolved r = new Resolved();
        r.kind = FieldKind.SCALAR;
        r.protoType = actual;
        return r;
    }

    private void checkInheritance(TypeElement type) {
        TypeMirror superType = type.getSuperclass();
        while (superType != null && superType.getKind() != TypeKind.NONE && !types.isSameType(superType, objectType)) {
            TypeElement superElement = asTypeElement(superType);
            if (superElement == null) {
                break;
            }
            if (superElement.getAnnotation(ProtoMessage.class) != null) {
                error(type, "inheritance of @ProtoMessage types is not supported");
                return;
            }
            for (Element enclosed : superElement.getEnclosedElements()) {
                if (enclosed.getAnnotation(ProtoField.class) != null
                    || enclosed.getAnnotation(ProtoOneof.class) != null
                    || enclosed.getAnnotation(ProtoUnknown.class) != null) {
                    error(type, "superclass " + superElement.getSimpleName()
                        + " has proto members; inheritance is not supported");
                    return;
                }
            }
            superType = superElement.getSuperclass();
        }
    }

    private void checkNoArgConstructor(TypeElement type) {
        List<ExecutableElement> ctors = ElementFilter.constructorsIn(type.getEnclosedElements());
        for (ExecutableElement ctor : ctors) {
            if (ctor.getParameters().isEmpty() && !ctor.getModifiers().contains(Modifier.PRIVATE)) {
                return;
            }
        }
        error(type, "@ProtoMessage class " + type.getSimpleName() + " needs a non-private no-arg constructor");
    }

    private boolean isPackable(FieldModel element) {
        return switch (element.kind) {
            case ENUM -> true;
            case SCALAR -> element.protoType != ProtoType.STRING && element.protoType != ProtoType.BYTES;
            default -> false;
        };
    }

    private boolean isRepeatedContainer(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }
        TypeMirror erased = types.erasure(type);
        return types.isAssignable(erased, listType)
            || types.isAssignable(erased, setType)
            || types.isAssignable(erased, collectionType);
    }

    private boolean isMap(TypeMirror type) {
        return types.isAssignable(types.erasure(type), mapType);
    }

    private boolean isOptional(TypeMirror type) {
        return types.isAssignable(types.erasure(type), optionalType);
    }

    private TypeElement collectionImpl(TypeMirror type, FieldModel elementModel) {
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

    private TypeElement mapImpl(TypeMirror type) {
        TypeElement element = asTypeElement(type);
        if (element != null && !element.getModifiers().contains(Modifier.ABSTRACT)
            && !element.getQualifiedName().contentEquals("java.util.Map")) {
            return element;
        }
        return elements.getTypeElement("java.util.LinkedHashMap");
    }

    private TypeMirror typeArgument(TypeMirror type, int index, Element origin, String what) {
        if (!(type instanceof DeclaredType declared)) {
            error(origin, "raw " + what + " is not supported; use a parameterized type");
            return null;
        }
        List<? extends TypeMirror> args = declared.getTypeArguments();
        if (args.size() <= index) {
            error(origin, "raw " + what + " is not supported; use a parameterized type");
            return null;
        }
        TypeMirror arg = args.get(index);
        if (arg.getKind() == TypeKind.WILDCARD || arg.getKind() == TypeKind.TYPEVAR) {
            error(origin, "wildcard / type-variable " + what + " type arguments are not supported");
            return null;
        }
        return arg;
    }

    private String renderType(TypeMirror type, String pkg) {
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

    private TypeMirror erasure(String fqcn) {
        return types.erasure(elements.getTypeElement(fqcn).asType());
    }

    private boolean isSame(TypeMirror a, TypeMirror b) {
        return types.isSameType(types.erasure(a), types.erasure(b));
    }

    private boolean isAssignable(TypeMirror a, TypeMirror b) {
        return types.isAssignable(a, b);
    }

    private TypeElement asTypeElement(TypeMirror type) {
        Element e = types.asElement(type);
        return e instanceof TypeElement te ? te : null;
    }

    private void error(Element element, String message) {
        errors = true;
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private static Set<ProtoType> intFamily() {
        return Set.of(
            ProtoType.AUTO, ProtoType.INT32, ProtoType.UINT32, ProtoType.SINT32,
            ProtoType.FIXED32, ProtoType.SFIXED32);
    }

    private static Set<ProtoType> longFamily() {
        return Set.of(
            ProtoType.AUTO, ProtoType.INT64, ProtoType.UINT64, ProtoType.SINT64,
            ProtoType.FIXED64, ProtoType.SFIXED64);
    }

    private static boolean isValidMapKey(ProtoType type) {
        return switch (type) {
            case INT32, INT64, UINT32, UINT64, SINT32, SINT64,
                 FIXED32, FIXED64, SFIXED32, SFIXED64, BOOL, STRING -> true;
            default -> false;
        };
    }

    private static ProtoType protoOrAuto(ProtoType type, ProtoType fallback) {
        return type == ProtoType.AUTO ? fallback : type;
    }

    enum AdapterSite {
        SINGULAR,
        REPEATED,
        MAP,
        ONEOF
    }

    @AllArgsConstructor
    private static final class Access {
        final AccessKind kind;
        final String readExpr;
        final String setter;
    }

    private static final class Resolved {
        FieldKind kind;
        ProtoType protoType;
        boolean byteArray;
        boolean byteBuffer;
        EnumModel enumModel;
        TypeElement messageType;
        String codecName;
    }

    @AllArgsConstructor
    private static final class ResolvedAdapter {
        final TypeElement adapterType;
        final TypeMirror j;
        final TypeMirror w;
        final ProtoType p;
    }
}
