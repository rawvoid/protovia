package io.github.rawvoid.protovia.processor.model;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoEnum;
import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;
import io.github.rawvoid.protovia.wire.WireType;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SchemaParser {

    private final Types types;
    private final Elements elements;
    private final Messager messager;
    private boolean errors;

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
    }

    public boolean hasErrors() {
        return errors;
    }

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
        for (VariableElement constant : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (constant.getKind() != ElementKind.ENUM_CONSTANT) {
                continue;
            }
            ProtoEnumValue value = constant.getAnnotation(ProtoEnumValue.class);
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
        return new EnumModel(type, Names.typeName(type, pkg), constants);
    }

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
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)
                && type.getKind() != ElementKind.RECORD) {
            if (type.getEnclosingElement().getKind() != ElementKind.PACKAGE
                    && !type.getModifiers().contains(Modifier.STATIC)) {
                error(type, "@ProtoMessage nested type must be static");
            }
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
        Map<Integer, FieldModel> byNumber = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();

        List<MessageModel.RecordComponentModel> recordComponents = new ArrayList<>();
        MessageModel.UnknownField[] unknown = new MessageModel.UnknownField[1];
        if (record) {
            parseRecord(type, pkg, byNumber, claimed, recordComponents, unknown);
        } else {
            parsePojo(type, pkg, byNumber, claimed, unknown);
        }

        if (byNumber.isEmpty()) {
            error(type, "@ProtoMessage " + type.getSimpleName() + " has no @ProtoField members");
        }
        if (errors) {
            return null;
        }
        List<FieldModel> fields = new ArrayList<>(byNumber.values());
        fields.sort(java.util.Comparator.comparingInt(f -> f.number));
        return new MessageModel(
                type,
                pkg,
                typeName,
                Names.codecSimpleName(elements, type),
                record,
                fields,
                recordComponents,
                unknown[0]);
    }

    private void parseRecord(
            TypeElement type,
            String pkg,
            Map<Integer, FieldModel> byNumber,
            Set<String> claimed,
            List<MessageModel.RecordComponentModel> recordComponents,
            MessageModel.UnknownField[] unknown) {
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
                            name,
                            "io.github.rawvoid.protovia.UnknownFields",
                            "io.github.rawvoid.protovia.UnknownFields.EMPTY",
                            null));
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
            String typeName = renderType(component.asType(), pkg);
            if (ann == null) {
                recordComponents.add(new MessageModel.RecordComponentModel(
                        name, typeName, defaultExpr(component.asType()), null));
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
            if (field != null && addField(byNumber, claimed, field)) {
                recordComponents.add(new MessageModel.RecordComponentModel(
                        name, typeName, defaultExpr(component.asType()), field));
            } else {
                recordComponents.add(new MessageModel.RecordComponentModel(
                        name, typeName, defaultExpr(component.asType()), null));
            }
        }
    }

    private void parsePojo(
            TypeElement type,
            String pkg,
            Map<Integer, FieldModel> byNumber,
            Set<String> claimed,
            MessageModel.UnknownField[] unknown) {
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
                addField(byNumber, claimed, model);
            }
        }

        for (ExecutableElement method : methods.values()) {
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
                addField(byNumber, claimed, model);
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

    private boolean addField(Map<Integer, FieldModel> byNumber, Set<String> claimed, FieldModel field) {
        if (!WireType.isValidFieldNumber(field.number)) {
            error(field.origin, "invalid field number " + field.number
                    + " (must be in [1, 536870911] and not in [19000, 19999])");
            return false;
        }
        if (byNumber.containsKey(field.number)) {
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
        if (isMap(effective)) {
            if (optional) {
                error(origin, "map field '" + name + "' cannot be optional");
                return null;
            }
            return resolveMap(origin, name, effective, ann, accessKind, readExpr, setter, fieldName, pkg, javaOptional);
        }
        if (isRepeatedContainer(effective)) {
            if (optional) {
                error(origin, "repeated field '" + name + "' cannot be optional");
                return null;
            }
            return resolveRepeated(origin, name, effective, ann, accessKind, readExpr, setter, fieldName, pkg, javaOptional);
        }
        return resolveSingular(
                origin, name, effective, ann.type(), optional, ann.packed(),
                accessKind, readExpr, setter, fieldName, pkg, javaOptional, type);
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
            boolean javaOptional) {
        boolean array = type.getKind() == TypeKind.ARRAY;
        TypeMirror elementType;
        if (array) {
            elementType = ((ArrayType) type).getComponentType();
            if (elementType.getKind() == TypeKind.BYTE) {
                return resolveSingular(
                        origin, name, type, protoOrAuto(ann.type(), ProtoType.BYTES),
                        ann.optional(), ann.packed(), accessKind, readExpr, setter, fieldName, pkg, javaOptional, type);
            }
        } else {
            elementType = typeArgument(type, 0, origin, "collection");
            if (elementType == null) {
                return null;
            }
        }
        FieldModel element = resolveSingular(
                origin, name + "Element", elementType, protoOrAuto(ann.type(), ProtoType.AUTO),
                false, false, accessKind, null, null, null, pkg, false, elementType);
        if (element == null) {
            return null;
        }
        String impl = array ? null : collectionImpl(type, pkg, element);
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
                .implTypeName(impl)
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
            boolean javaOptional) {
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
                false, false, accessKind, null, null, null, pkg, false, keyType);
        FieldModel value = resolveSingular(
                origin, name + "Value", valueType, protoOrAuto(ann.valueType(), ProtoType.AUTO),
                false, false, accessKind, null, null, null, pkg, false, valueType);
        if (key == null || value == null) {
            return null;
        }
        if (!isValidMapKey(key.protoType)) {
            error(origin, "map key of field '" + name + "' must be an integral type, bool, or string");
            return null;
        }
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
                .implTypeName(mapImpl(type, pkg))
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
            TypeMirror declaredJavaType) {
        if (type.getKind().isPrimitive() && optional) {
            error(origin, "optional field '" + name + "' cannot be a primitive; use a boxed type or Optional");
            return null;
        }
        Resolved resolved = classify(origin, name, type, declared);
        if (resolved == null) {
            return null;
        }
        return FieldModel.builder()
                .number(origin.getAnnotation(ProtoField.class) != null ? origin.getAnnotation(ProtoField.class).number() : 0)
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
                .codecName(resolved.codecName)
                .enumModel(resolved.enumModel)
                .messageType(resolved.messageType)
                .origin(origin)
                .build();
    }

    private Resolved classify(Element origin, String name, TypeMirror type, ProtoType declared) {
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
            return scalar(origin, name, declared, ProtoType.STRING, Set.of(ProtoType.AUTO, ProtoType.STRING, ProtoType.BYTES));
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
            String currentPkg = Names.packageName(enclosingType(origin));
            if (!codecPkg.equals(currentPkg) && !codecPkg.isEmpty()) {
                r.codecName = codecPkg + "." + r.codecName;
            }
            return r;
        }
        error(origin, "unsupported type for field '" + name + "': " + element.getQualifiedName()
                + " (annotate with @ProtoMessage / @ProtoEnum)");
        return null;
    }

    private TypeElement enclosingType(Element origin) {
        Element e = origin;
        while (e != null && e.getKind() != ElementKind.CLASS && e.getKind() != ElementKind.RECORD) {
            e = e.getEnclosingElement();
        }
        return e instanceof TypeElement te ? te : null;
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
                if (enclosed.getAnnotation(ProtoField.class) != null) {
                    error(type, "superclass " + superElement.getSimpleName() + " has @ProtoField; inheritance is not supported");
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

    private String collectionImpl(TypeMirror type, String pkg, FieldModel elementModel) {
        TypeElement element = asTypeElement(type);
        if (element != null && !element.getModifiers().contains(Modifier.ABSTRACT)
                && !element.getQualifiedName().contentEquals("java.util.List")
                && !element.getQualifiedName().contentEquals("java.util.Set")
                && !element.getQualifiedName().contentEquals("java.util.Collection")) {
            return renderType(type, pkg).replace("<?>", "").replaceAll("<.*>", "<>");
        }
        TypeMirror erased = types.erasure(type);
        if (types.isAssignable(erased, setType)) {
            return "java.util.LinkedHashSet<>";
        }
        String primitive = elementModel.primitiveListClass();
        if (primitive != null) {
            return primitive;
        }
        return "java.util.ArrayList<>";
    }

    private String mapImpl(TypeMirror type, String pkg) {
        TypeElement element = asTypeElement(type);
        if (element != null && !element.getModifiers().contains(Modifier.ABSTRACT)
                && !element.getQualifiedName().contentEquals("java.util.Map")) {
            return renderType(type, pkg).replaceAll("<.*>", "<>");
        }
        return "java.util.LinkedHashMap<>";
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

    private String defaultExpr(TypeMirror type) {
        if (isOptional(type)) {
            return "java.util.Optional.empty()";
        }
        return switch (type.getKind()) {
            case BOOLEAN -> "false";
            case BYTE, SHORT, INT, CHAR -> "0";
            case LONG -> "0L";
            case FLOAT -> "0F";
            case DOUBLE -> "0D";
            default -> "null";
        };
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

    private static final class Access {
        final AccessKind kind;
        final String readExpr;
        final String setter;

        Access(AccessKind kind, String readExpr, String setter) {
            this.kind = kind;
            this.readExpr = readExpr;
            this.setter = setter;
        }
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
}
