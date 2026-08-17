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
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers and validates {@code ProtoAdapter}s. Priority: field / oneof case
 * → message {@code @ProtoAdapters} → package-info → {@code @ProtoAdapted} on {@code J}.
 */
final class AdapterResolver {

    static final String PROTO_ADAPTER = "io.github.rawvoid.protovia.codec.ProtoAdapter";
    static final String PROTO_ADAPTER_UNSET = "io.github.rawvoid.protovia.codec.ProtoAdapter.Unset";
    static final String PROTO_FIELD_ANN = "io.github.rawvoid.protovia.annotation.ProtoField";
    static final String PROTO_ONEOF_CASE_ANN = "io.github.rawvoid.protovia.annotation.ProtoOneofCase";
    static final String PROTO_ADAPTERS_ANN = "io.github.rawvoid.protovia.annotation.ProtoAdapters";
    static final String PROTO_ADAPTED_ANN = "io.github.rawvoid.protovia.annotation.ProtoAdapted";

    private final TypeEnv env;
    private final Diagnostics diag;
    private final TypeClassifier classifier;
    private List<ResolvedAdapter> discovery = List.of();
    private List<ResolvedAdapter> previousDiscovery = List.of();

    AdapterResolver(TypeEnv env, Diagnostics diag, TypeClassifier classifier) {
        this.env = env;
        this.diag = diag;
        this.classifier = classifier;
    }

    void enter(TypeElement messageType) {
        previousDiscovery = discovery;
        discovery = buildDiscovery(messageType);
    }

    void exit() {
        discovery = previousDiscovery;
        previousDiscovery = List.of();
    }

    ResolvedAdapter findDiscovered(TypeMirror javaJ) {
        for (ResolvedAdapter adapter : discovery) {
            if (env.types.isSameType(adapter.j(), javaJ)) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * Tries field / discovered / {@code @ProtoAdapted} adapters in documented order.
     * {@link AdapterApplication#done()} means the caller must not classify.
     */
    AdapterApplication applyDeclaredOrDiscovered(FieldRequest req) {
        TypeMirror type = req.type;
        ResolvedAdapter discovered = findDiscovered(type);
        if (type.getKind().isPrimitive() && (req.fieldAdapter != null || discovered != null)) {
            diag.error(req.origin, "adapter cannot be applied to primitive field '" + req.name + "'");
            return AdapterApplication.rejected();
        }
        if (req.fieldAdapter != null) {
            ResolvedAdapter adapter = validateAdapter(req.fieldAdapter, req.origin);
            if (adapter == null) {
                return AdapterApplication.rejected();
            }
            if (!env.types.isSameType(adapter.j(), type)) {
                if (req.site != AdapterSite.MAP) {
                    diag.error(req.origin, "adapter " + req.fieldAdapter.getSimpleName()
                        + " handles " + env.simpleTypeName(adapter.j()) + ", not " + env.simpleTypeName(type));
                    return AdapterApplication.rejected();
                }
            } else {
                FieldModel applied = applyAdapter(adapter, req);
                return applied == null ? AdapterApplication.rejected() : AdapterApplication.applied(applied);
            }
        }
        if (discovered != null) {
            FieldModel applied = applyAdapter(discovered, req);
            return applied == null ? AdapterApplication.rejected() : AdapterApplication.applied(applied);
        }
        TypeElement javaType = env.asTypeElement(type);
        if (javaType != null && findAnnotation(javaType, PROTO_ADAPTED_ANN) != null) {
            FieldModel applied = resolveProtoAdapted(javaType, req);
            return applied == null ? AdapterApplication.rejected() : AdapterApplication.applied(applied);
        }
        return AdapterApplication.skip();
    }

    FieldModel applyAdapter(ResolvedAdapter adapter, FieldRequest req) {
        ProtoType protoType = bindAdapterProtoType(adapter, req.declared, req.origin, req.name);
        if (protoType == null) {
            return null;
        }
        return req.baseBuilder()
            .kind(FieldKind.SCALAR)
            .protoType(protoType)
            .primitive(false)
            .javaTypeName(env.renderType(req.declaredJavaType, req.pkg))
            .javaType(req.declaredJavaType)
            .adapterType(adapter.adapterType())
            .wireJavaType(adapter.w())
            .build();
    }

    void rejectOneofAdapter(Element origin, TypeElement fieldAdapter) {
        if (fieldAdapter != null) {
            diag.error(origin, "@ProtoOneofCase without a scalar payload cannot declare adapter");
        }
    }

    TypeElement adapterFrom(Element origin, String annotationName) {
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

    Element protoFieldHost(Element origin) {
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

    AnnotationMirror findAnnotation(Element origin, String annotationName) {
        for (AnnotationMirror mirror : origin.getAnnotationMirrors()) {
            Element annotation = mirror.getAnnotationType().asElement();
            if (annotation instanceof TypeElement type
                && type.getQualifiedName().contentEquals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    ResolvedAdapter validateAdapter(TypeElement adapter, Element... origins) {
        String adapterName = adapter.getSimpleName().toString();
        if (adapter.getKind() != ElementKind.CLASS
            || adapter.getQualifiedName().contentEquals(PROTO_ADAPTER)) {
            diag.error("adapter must be a concrete type, not ProtoAdapter", origins);
            return null;
        }
        if (!env.isPublicType(adapter)) {
            diag.error("adapter " + adapterName + " must be a public type", origins);
            return null;
        }
        DeclaredType iface = findProtoAdapterIface(adapter);
        if (iface == null) {
            diag.error("adapter " + adapterName + " must implement ProtoAdapter", origins);
            return null;
        }
        ProtoScalar scalar = adapter.getAnnotation(ProtoScalar.class);
        if (scalar == null) {
            diag.error("adapter " + adapterName + " must be annotated with @ProtoScalar", origins);
            return null;
        }
        ProtoType protoType = scalar.value();
        if (protoType == ProtoType.AUTO || protoType == ProtoType.ENUM || protoType == ProtoType.MESSAGE) {
            diag.error("@ProtoScalar must name a scalar ProtoType", origins);
            return null;
        }
        if (!hasInstance(adapter)) {
            diag.error("adapter " + adapterName + " must declare public static final INSTANCE", origins);
            return null;
        }
        List<? extends TypeMirror> args = iface.getTypeArguments();
        if (args.size() != 2) {
            diag.error("adapter " + adapterName + " must bind ProtoAdapter type parameters", origins);
            return null;
        }
        TypeMirror j = args.get(0);
        TypeMirror w = args.get(1);
        if (!env.isResolvedType(j) || !env.isResolvedType(w)) {
            diag.error("adapter " + adapterName + " must bind ProtoAdapter type parameters", origins);
            return null;
        }
        if (j instanceof DeclaredType declaredJ && !declaredJ.getTypeArguments().isEmpty()) {
            diag.error("adapter J must be a non-parameterized class", origins);
            return null;
        }
        if (!isRequiredWireType(w, protoType)) {
            diag.error("adapter " + adapterName + " ProtoType." + protoType
                + " requires " + requiredWireName(protoType) + ", not " + env.simpleTypeName(w), origins);
            return null;
        }
        return new ResolvedAdapter(adapter, j, w, protoType);
    }

    private FieldModel resolveProtoAdapted(TypeElement javaType, FieldRequest req) {
        TypeElement adaptedType = adaptedFrom(javaType);
        if (adaptedType == null) {
            return null;
        }
        ResolvedAdapter adapter = javaType.equals(req.origin)
            ? validateAdapter(adaptedType, req.origin)
            : validateAdapter(adaptedType, javaType, req.origin);
        if (adapter == null) {
            return null;
        }
        if (classifier.alreadyProtoType(req.type)) {
            String message = classifier.protoAdaptedOnProtoTypeMessage(javaType);
            diag.error(javaType, message);
            if (!javaType.equals(req.origin)) {
                diag.error(req.origin, message);
            }
            return null;
        }
        if (!env.types.isSameType(adapter.j(), req.type)) {
            String message = "adapter " + adaptedType.getSimpleName()
                + " handles " + env.simpleTypeName(adapter.j()) + ", not " + env.simpleTypeName(req.type);
            diag.error(javaType, message);
            if (!javaType.equals(req.origin)) {
                diag.error(req.origin, message);
            }
            return null;
        }
        return applyAdapter(adapter, req);
    }

    private List<ResolvedAdapter> buildDiscovery(TypeElement messageType) {
        List<ResolvedAdapter> list = new ArrayList<>();
        PackageElement pkg = env.elements.getPackageOf(messageType);
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
                if (env.types.isSameType(existing.j(), resolved.j())) {
                    diag.error(origin, "duplicate adapter for " + env.simpleTypeName(resolved.j()));
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                fromThis.add(resolved);
            }
        }
        for (ResolvedAdapter next : fromThis) {
            discovery.removeIf(existing -> env.types.isSameType(existing.j(), next.j()));
            discovery.add(next);
        }
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

    private TypeElement typeElementFrom(AnnotationValue value, Element origin, String what) {
        if (value == null || !(value.getValue() instanceof TypeMirror type)) {
            return null;
        }
        if (type.getKind() == TypeKind.ERROR) {
            diag.error(origin, what + " " + type + " cannot be resolved");
            return null;
        }
        TypeElement element = env.asTypeElement(type);
        if (element == null) {
            diag.error(origin, what + " " + type + " cannot be resolved");
            return null;
        }
        return element;
    }

    private AnnotationValue annotationMember(AnnotationMirror mirror, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
            : env.elements.getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private ProtoType bindAdapterProtoType(ResolvedAdapter adapter, ProtoType declared, Element origin, String name) {
        if (declared == ProtoType.AUTO) {
            return adapter.p();
        }
        if (!classifier.familyOf(adapter.p()).contains(declared)) {
            diag.error(origin, "field '" + name + "' Java type cannot use ProtoType." + declared);
            return null;
        }
        return declared;
    }

    private DeclaredType findProtoAdapterIface(TypeElement adapter) {
        if (env.protoAdapterType == null) {
            return null;
        }
        TypeMirror target = env.types.erasure(env.protoAdapterType.asType());
        Deque<TypeMirror> queue = new ArrayDeque<>();
        queue.add(adapter.asType());
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            TypeMirror current = queue.removeFirst();
            if (!(current instanceof DeclaredType declared)) {
                continue;
            }
            TypeElement element = env.asTypeElement(declared);
            if (element == null || !seen.add(element.getQualifiedName().toString())) {
                continue;
            }
            if (env.types.isSameType(env.types.erasure(declared), target)) {
                return declared;
            }
            queue.addAll(env.types.directSupertypes(declared));
        }
        return null;
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
                && env.types.isAssignable(field.asType(), adapter.asType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRequiredWireType(TypeMirror w, ProtoType protoType) {
        return switch (protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> env.isSame(w, env.integerType);
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> env.isSame(w, env.longType);
            case BOOL -> env.isSame(w, env.booleanType);
            case FLOAT -> env.isSame(w, env.floatType);
            case DOUBLE -> env.isSame(w, env.doubleType);
            case STRING -> env.isSame(w, env.stringType);
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
}
