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
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Resolves a {@code @ProtoField} Java type into a {@link FieldModel}.
 *
 * @author Rawvoid
 */
final class FieldResolver {

    private final TypeEnv env;
    private final Diagnostics diag;
    private final TypeClassifier classifier;
    private final AdapterResolver adapters;

    FieldResolver(TypeEnv env, Diagnostics diag, TypeClassifier classifier, AdapterResolver adapters) {
        this.env = env;
        this.diag = diag;
        this.classifier = classifier;
        this.adapters = adapters;
    }

    FieldModel resolveField(
        Element origin,
        String name,
        TypeMirror type,
        ProtoField ann,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg) {
        boolean javaOptional = env.isOptional(type);
        TypeMirror effective = type;
        if (javaOptional) {
            effective = env.typeArgument(type, 0, origin, "Optional", diag);
            if (effective == null) {
                return null;
            }
        }
        boolean optional = ann.optional() || javaOptional;
        TypeElement fieldAdapter = adapters.adapterFrom(adapters.protoFieldHost(origin), AdapterResolver.PROTO_FIELD_ANN);
        if (env.isMap(effective)) {
            if (optional) {
                diag.error(origin, "map field '" + name + "' cannot be optional");
                return null;
            }
            return resolveMap(
                origin, name, effective, ann, accessKind, readExpr, setter, fieldName, pkg, javaOptional, fieldAdapter);
        }
        if (env.isRepeatedContainer(effective)) {
            if (optional) {
                diag.error(origin, "repeated field '" + name + "' cannot be optional");
                return null;
            }
            return resolveRepeated(
                origin, name, effective, ann, accessKind, readExpr, setter, fieldName, pkg, javaOptional, fieldAdapter);
        }
        return resolveSingular(new FieldRequest(
            origin, name, effective, ann.type(), optional, ann.packed(),
            accessKind, readExpr, setter, fieldName, pkg, javaOptional, type,
            fieldAdapter, ann.number(), AdapterSite.SINGULAR));
    }

    FieldModel resolveSingular(FieldRequest req) {
        if (req.type.getKind().isPrimitive() && req.optional) {
            diag.error(req.origin, "optional field '" + req.name + "' cannot be a primitive; use a boxed type or Optional");
            return null;
        }
        AdapterApplication adapted = adapters.applyDeclaredOrDiscovered(req);
        if (adapted.done()) {
            return adapted.field();
        }
        Resolved resolved = classifier.classify(req.origin, req.name, req.type, req.declared, req.pkg);
        if (resolved == null) {
            return null;
        }
        return FieldModel.builder()
            .number(req.number)
            .name(req.name)
            .localName(Names.safeLocal(req.name))
            .optional(req.optional)
            .packed(req.packed)
            .javaOptional(req.javaOptional)
            .accessKind(req.accessKind)
            .readExpr(req.readExpr)
            .setterName(req.setter)
            .fieldName(req.fieldName)
            .origin(req.origin)
            .kind(resolved.kind)
            .protoType(resolved.protoType)
            .primitive(req.type.getKind().isPrimitive())
            .byteArray(resolved.byteArray)
            .byteBuffer(resolved.byteBuffer)
            .javaTypeName(env.renderType(req.declaredJavaType, req.pkg))
            .javaType(req.declaredJavaType)
            .codecName(resolved.codecName)
            .enumModel(resolved.enumModel)
            .messageType(resolved.messageType)
            .build();
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
                return resolveSingular(new FieldRequest(
                    origin, name, type, TypeClassifier.protoOrAuto(ann.type(), ProtoType.BYTES),
                    ann.optional(), ann.packed(), accessKind, readExpr, setter, fieldName, pkg, javaOptional, type,
                    fieldAdapter, ann.number(), AdapterSite.SINGULAR));
            }
        } else {
            elementType = env.typeArgument(type, 0, origin, "collection", diag);
            if (elementType == null) {
                return null;
            }
        }
        FieldModel element = resolveSingular(new FieldRequest(
            origin, name + "Element", elementType, TypeClassifier.protoOrAuto(ann.type(), ProtoType.AUTO),
            false, false, accessKind, null, null, null, pkg, false, elementType,
            fieldAdapter, ann.number(), AdapterSite.REPEATED));
        if (element == null) {
            return null;
        }
        TypeElement impl = array ? null : env.collectionImpl(type, element);
        boolean packed = ann.packed() && isPackable(element);
        var builder = FieldModel.builder()
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
            .javaTypeName(env.renderType(type, pkg))
            .javaType(type)
            .implTypeName(impl == null ? null : impl.getQualifiedName().toString())
            .implType(impl)
            .element(element)
            .origin(origin)
            .array(array);
        if (array) {
            builder.arrayComponentType(env.renderType(elementType, pkg));
        }
        return builder.build();
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
        TypeMirror keyType = env.typeArgument(type, 0, origin, "Map", diag);
        TypeMirror valueType = env.typeArgument(type, 1, origin, "Map", diag);
        if (keyType == null || valueType == null) {
            return null;
        }
        if (env.isMap(valueType)) {
            diag.error(origin, "map-of-map is not supported for field '" + name + "'");
            return null;
        }
        FieldModel key = resolveSingular(new FieldRequest(
            origin, name + "Key", keyType, TypeClassifier.protoOrAuto(ann.keyType(), ProtoType.AUTO),
            false, false, accessKind, null, null, null, pkg, false, keyType,
            fieldAdapter, ann.number(), AdapterSite.MAP));
        FieldModel value = resolveSingular(new FieldRequest(
            origin, name + "Value", valueType, TypeClassifier.protoOrAuto(ann.valueType(), ProtoType.AUTO),
            false, false, accessKind, null, null, null, pkg, false, valueType,
            fieldAdapter, ann.number(), AdapterSite.MAP));
        if (key == null || value == null) {
            return null;
        }
        if (fieldAdapter != null) {
            ResolvedAdapter adapter = adapters.validateAdapter(fieldAdapter, origin);
            if (adapter != null
                && !env.types.isSameType(adapter.j(), keyType)
                && !env.types.isSameType(adapter.j(), valueType)) {
                diag.error(origin, "adapter " + fieldAdapter.getSimpleName()
                    + " handles " + env.simpleTypeName(adapter.j()) + ", not " + env.simpleTypeName(type));
                return null;
            }
        }
        if (!TypeClassifier.isValidMapKey(key.protoType)) {
            diag.error(origin, "map key of field '" + name + "' must be an integral type, bool, or string");
            return null;
        }
        TypeElement impl = env.mapImpl(type);
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
            .javaTypeName(env.renderType(type, pkg))
            .javaType(type)
            .implTypeName(impl.getQualifiedName().toString())
            .implType(impl)
            .mapKey(key)
            .mapValue(value)
            .origin(origin)
            .build();
    }

    private static boolean isPackable(FieldModel element) {
        return switch (element.kind) {
            case ENUM -> true;
            case SCALAR -> element.protoType != ProtoType.STRING && element.protoType != ProtoType.BYTES;
            default -> false;
        };
    }
}
