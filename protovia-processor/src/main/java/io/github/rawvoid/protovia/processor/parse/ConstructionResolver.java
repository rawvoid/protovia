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

import io.github.rawvoid.protovia.annotation.ProtoBuilder;
import io.github.rawvoid.protovia.annotation.ProtoCreator;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Instantiation;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chooses one {@link Instantiation} for a {@code @ProtoMessage}. Explicit
 * annotations win; otherwise mutable JavaBean, then builder, then all-args
 * constructor / factory. Never mixes strategies.
 *
 * @author Rawvoid
 */
final class ConstructionResolver {

    private static final Set<String> LOMBOK_CONSTRUCTION = Set.of(
        "lombok.Builder",
        "lombok.experimental.SuperBuilder",
        "lombok.Value",
        "lombok.Data",
        "lombok.AllArgsConstructor",
        "lombok.NoArgsConstructor",
        "lombok.RequiredArgsConstructor",
        "lombok.Getter");

    private final TypeEnv env;
    private final Diagnostics diag;
    private boolean deferred;

    ConstructionResolver(TypeEnv env, Diagnostics diag) {
        this.env = env;
        this.diag = diag;
    }

    boolean wasDeferred() {
        return deferred;
    }

    /**
     * @param allowDefer when {@code true} and Lombok may still generate members,
     *                   skip diagnostics so the processor can retry next round
     */
    Instantiation resolve(TypeElement type, MessageScope scope, boolean record, boolean allowDefer) {
        deferred = false;
        List<ProtoMember> members = members(scope, record);
        if (record) {
            if (hasProtoCreator(type)) {
                return explicitCreator(type, members);
            }
            return recordConstructor(scope);
        }

        List<String> explicitErrors = new ArrayList<>();
        Instantiation creator = explicitCreator(type, members, explicitErrors);
        if (creator != null) {
            return creator;
        }
        if (!explicitErrors.isEmpty()) {
            for (String error : explicitErrors) {
                diag.error(type, error);
            }
            return null;
        }

        Instantiation annotatedBuilder = explicitBuilder(type, members, explicitErrors);
        if (annotatedBuilder != null) {
            return annotatedBuilder;
        }
        if (!explicitErrors.isEmpty()) {
            for (String error : explicitErrors) {
                diag.error(type, error);
            }
            return null;
        }

        if (mutable(type, members)) {
            return Instantiation.MUTABLE;
        }

        Instantiation builder = detectBuilder(type, members, type.getAnnotation(ProtoBuilder.class));
        if (builder != null) {
            return builder;
        }

        Instantiation ctor = uniqueMatchingConstructor(type, members);
        if (ctor != null) {
            return ctor;
        }
        Instantiation factory = uniqueMatchingFactory(type, members);
        if (factory != null) {
            return factory;
        }

        if (allowDefer && hasLombokConstruction(type)) {
            deferred = true;
            return null;
        }
        diag.error(type, "@ProtoMessage class " + type.getSimpleName()
            + " cannot be instantiated: need a public no-arg constructor and setters, "
            + "a public all-args constructor or @ProtoCreator factory matching proto members, "
            + "or a public builder()");
        return null;
    }

    private static boolean hasProtoCreator(TypeElement type) {
        for (ExecutableElement ctor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (ctor.getAnnotation(ProtoCreator.class) != null) {
                return true;
            }
        }
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (method.getAnnotation(ProtoCreator.class) != null) {
                return true;
            }
        }
        return false;
    }

    private Instantiation explicitCreator(TypeElement type, List<ProtoMember> members) {
        List<String> errors = new ArrayList<>();
        Instantiation found = explicitCreator(type, members, errors);
        if (!errors.isEmpty()) {
            for (String error : errors) {
                diag.error(type, error);
            }
            return null;
        }
        return found;
    }

    private Instantiation explicitCreator(
        TypeElement type,
        List<ProtoMember> members,
        List<String> errors) {
        List<ExecutableElement> marked = new ArrayList<>();
        for (ExecutableElement ctor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (ctor.getAnnotation(ProtoCreator.class) != null) {
                marked.add(ctor);
            }
        }
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (method.getAnnotation(ProtoCreator.class) != null) {
                marked.add(method);
            }
        }
        if (marked.isEmpty()) {
            return null;
        }
        if (marked.size() > 1) {
            errors.add("at most one @ProtoCreator per type");
            return null;
        }
        ExecutableElement exec = marked.getFirst();
        if (!exec.getModifiers().contains(Modifier.PUBLIC)) {
            errors.add("@ProtoCreator " + exec.getSimpleName() + " must be public");
            return null;
        }
        if (exec.getKind() == ElementKind.METHOD) {
            if (!exec.getModifiers().contains(Modifier.STATIC)) {
                errors.add("@ProtoCreator factory '" + exec.getSimpleName() + "' must be static");
                return null;
            }
            if (!returnsMessage(exec, type)) {
                errors.add("@ProtoCreator factory '" + exec.getSimpleName()
                    + "' must return " + type.getSimpleName());
                return null;
            }
        }
        List<Instantiation.Slot> slots = bindParameters(exec, members, errors);
        if (slots == null) {
            return null;
        }
        if (exec.getKind() == ElementKind.CONSTRUCTOR) {
            return new Instantiation.Constructor(slots);
        }
        return new Instantiation.Factory(exec.getSimpleName().toString(), slots);
    }

    private Instantiation explicitBuilder(
        TypeElement type,
        List<ProtoMember> members,
        List<String> errors) {
        ProtoBuilder ann = type.getAnnotation(ProtoBuilder.class);
        if (ann == null) {
            return null;
        }
        Instantiation builder = detectBuilder(type, members, ann);
        if (builder == null) {
            errors.add("@ProtoBuilder on " + type.getSimpleName()
                + " did not match a public builder factory/class with setters for every proto member");
        }
        return builder;
    }

    private Instantiation recordConstructor(MessageScope scope) {
        List<Instantiation.Slot> slots = new ArrayList<>();
        for (MessageModel.RecordComponentModel component : scope.recordComponents) {
            boolean unknown = scope.unknown != null && component.name().equals(scope.unknown.name());
            slots.add(new Instantiation.Slot(
                Names.safeLocal(component.name()),
                component.field(),
                unknown));
        }
        return new Instantiation.Constructor(List.copyOf(slots));
    }

    private boolean mutable(TypeElement type, List<ProtoMember> members) {
        if (!hasPublicNoArg(type)) {
            return false;
        }
        for (ProtoMember member : members) {
            if (!writable(member)) {
                return false;
            }
        }
        return true;
    }

    private Instantiation uniqueMatchingConstructor(TypeElement type, List<ProtoMember> members) {
        List<ExecutableElement> matches = new ArrayList<>();
        for (ExecutableElement ctor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (!ctor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (ctor.getParameters().isEmpty()) {
                continue;
            }
            List<String> errors = new ArrayList<>();
            if (bindParameters(ctor, members, errors) != null) {
                matches.add(ctor);
            }
        }
        if (matches.size() != 1) {
            return null;
        }
        return new Instantiation.Constructor(bindParameters(matches.getFirst(), members, new ArrayList<>()));
    }

    private Instantiation uniqueMatchingFactory(TypeElement type, List<ProtoMember> members) {
        List<ExecutableElement> matches = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (!method.getModifiers().contains(Modifier.PUBLIC)
                || !method.getModifiers().contains(Modifier.STATIC)
                || method.getParameters().isEmpty()
                || !returnsMessage(method, type)
                || method.getAnnotation(ProtoCreator.class) != null) {
                continue;
            }
            String name = method.getSimpleName().toString();
            if (name.equals("builder") || name.equals("toBuilder")) {
                continue;
            }
            List<String> errors = new ArrayList<>();
            if (bindParameters(method, members, errors) != null) {
                matches.add(method);
            }
        }
        if (matches.size() != 1) {
            return null;
        }
        ExecutableElement method = matches.getFirst();
        return new Instantiation.Factory(
            method.getSimpleName().toString(),
            bindParameters(method, members, new ArrayList<>()));
    }

    private Instantiation detectBuilder(TypeElement type, List<ProtoMember> members, ProtoBuilder ann) {
        String factoryMethod = ann == null ? "builder" : ann.builderMethod();
        String nestedClass = ann == null ? "Builder" : ann.builderClass();
        String buildMethod = ann == null ? "build" : ann.buildMethod();
        String setterPrefix = ann == null ? "" : ann.setterPrefix();

        TypeMirror builderType = null;
        String factory = null;
        String nested = null;

        if (factoryMethod != null && !factoryMethod.isEmpty()) {
            ExecutableElement start = findPublicStatic(type, factoryMethod, 0);
            if (start != null) {
                builderType = start.getReturnType();
                factory = factoryMethod;
            }
        }
        if (builderType == null && nestedClass != null && !nestedClass.isEmpty()) {
            TypeElement nestedType = findNested(type, nestedClass);
            if (nestedType != null
                && nestedType.getModifiers().contains(Modifier.PUBLIC)
                && hasPublicNoArg(nestedType)) {
                builderType = nestedType.asType();
                nested = nestedClass;
            }
        }
        if (builderType == null) {
            return null;
        }
        TypeElement builderElement = env.asTypeElement(env.types.erasure(builderType));
        if (builderElement == null) {
            return null;
        }
        if (!hasBuildMethod(builderElement, buildMethod, type)) {
            return null;
        }
        List<Instantiation.BuilderBinding> bindings = new ArrayList<>();
        for (ProtoMember member : members) {
            String setter = findBuilderSetter(builderElement, member, setterPrefix);
            if (setter == null) {
                return null;
            }
            bindings.add(new Instantiation.BuilderBinding(
                setter, member.localName, member.field, member.unknown != null));
        }
        return new Instantiation.Builder(factory, nested, buildMethod, List.copyOf(bindings));
    }

    private List<Instantiation.Slot> bindParameters(
        ExecutableElement exec,
        List<ProtoMember> members,
        List<String> errors) {
        List<? extends VariableElement> params = exec.getParameters();
        String execName = exec.getKind() == ElementKind.CONSTRUCTOR
            ? "constructor"
            : exec.getSimpleName().toString();
        if (params.size() != members.size()) {
            errors.add(execName + " has " + params.size()
                + " parameters but the message has " + members.size() + " proto members");
            return null;
        }
        Map<String, ProtoMember> byName = new LinkedHashMap<>();
        for (ProtoMember member : members) {
            byName.put(member.name, member);
        }
        boolean namesUsable = true;
        List<ProtoMember> named = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (VariableElement param : params) {
            String name = param.getSimpleName().toString();
            if (isSyntheticName(name) || !byName.containsKey(name) || !seen.add(name)) {
                namesUsable = false;
                break;
            }
            ProtoMember member = byName.get(name);
            if (!env.types.isAssignable(member.type, param.asType())) {
                namesUsable = false;
                break;
            }
            named.add(member);
        }
        if (namesUsable && named.size() == members.size()) {
            return toSlots(named);
        }
        List<ProtoMember> positional = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            ProtoMember member = members.get(i);
            if (!env.types.isAssignable(member.type, params.get(i).asType())) {
                errors.add("cannot bind " + execName + " parameter "
                    + params.get(i).getSimpleName() + " to proto member '" + member.name + "'");
                return null;
            }
            positional.add(member);
        }
        return toSlots(positional);
    }

    private static List<Instantiation.Slot> toSlots(List<ProtoMember> members) {
        List<Instantiation.Slot> slots = new ArrayList<>(members.size());
        for (ProtoMember member : members) {
            slots.add(new Instantiation.Slot(member.localName, member.field, member.unknown != null));
        }
        return List.copyOf(slots);
    }

    private String findBuilderSetter(TypeElement builder, ProtoMember member, String prefix) {
        List<String> names = setterNames(member.name, prefix);
        for (String name : names) {
            ExecutableElement match = findSetterOnHierarchy(builder, name, member.type);
            if (match != null) {
                return match.getSimpleName().toString();
            }
        }
        return null;
    }

    private ExecutableElement findSetterOnHierarchy(TypeElement builder, String name, TypeMirror propertyType) {
        TypeElement current = builder;
        while (current != null) {
            for (ExecutableElement method : ElementFilter.methodsIn(current.getEnclosedElements())) {
                if (method.getModifiers().contains(Modifier.STATIC)
                    || method.getModifiers().contains(Modifier.PRIVATE)
                    || !method.getSimpleName().contentEquals(name)
                    || method.getParameters().size() != 1) {
                    continue;
                }
                if (env.types.isAssignable(propertyType, method.getParameters().getFirst().asType())) {
                    return method;
                }
            }
            current = superclass(current);
        }
        return null;
    }

    private boolean hasBuildMethod(TypeElement builder, String buildMethod, TypeElement message) {
        TypeElement current = builder;
        while (current != null) {
            for (ExecutableElement method : ElementFilter.methodsIn(current.getEnclosedElements())) {
                if (method.getModifiers().contains(Modifier.STATIC)
                    || method.getModifiers().contains(Modifier.PRIVATE)
                    || !method.getSimpleName().contentEquals(buildMethod)
                    || !method.getParameters().isEmpty()) {
                    continue;
                }
                if (returnsMessage(method, message)) {
                    return true;
                }
            }
            current = superclass(current);
        }
        return false;
    }

    private TypeElement superclass(TypeElement type) {
        TypeMirror parent = type.getSuperclass();
        if (parent.getKind() == TypeKind.NONE) {
            return null;
        }
        TypeElement element = env.asTypeElement(parent);
        if (element == null || element.getQualifiedName().contentEquals("java.lang.Object")) {
            return null;
        }
        return element;
    }

    private ExecutableElement findPublicStatic(TypeElement type, String name, int arity) {
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.PUBLIC)
                && method.getModifiers().contains(Modifier.STATIC)
                && method.getSimpleName().contentEquals(name)
                && method.getParameters().size() == arity) {
                return method;
            }
        }
        return null;
    }

    private TypeElement findNested(TypeElement type, String simpleName) {
        for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
            if (nested.getSimpleName().contentEquals(simpleName)) {
                return nested;
            }
        }
        return null;
    }

    private boolean returnsMessage(ExecutableElement method, TypeElement message) {
        return assignableToMessage(method.getReturnType(), message);
    }

    /**
     * SuperBuilder {@code build()} returns a type variable {@code C extends Leaf}.
     */
    private boolean assignableToMessage(TypeMirror returned, TypeElement message) {
        TypeMirror expected = env.types.erasure(message.asType());
        if (returned.getKind() == TypeKind.TYPEVAR) {
            TypeMirror bound = ((TypeVariable) returned).getUpperBound();
            return assignableToMessage(bound, message);
        }
        if (returned.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror erased = env.types.erasure(returned);
        return env.types.isSameType(erased, expected)
            || env.types.isAssignable(erased, expected)
            || env.types.isSubtype(erased, expected);
    }

    private static boolean hasPublicNoArg(TypeElement type) {
        boolean sawCtor = false;
        for (ExecutableElement ctor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            sawCtor = true;
            if (ctor.getParameters().isEmpty() && ctor.getModifiers().contains(Modifier.PUBLIC)) {
                return true;
            }
        }
        return !sawCtor;
    }

    private static boolean writable(ProtoMember member) {
        if (member.field != null) {
            return writable(member.field);
        }
        MessageModel.UnknownField unknown = member.unknown;
        if (unknown == null) {
            return false;
        }
        if (unknown.accessKind() == AccessKind.GETTER_SETTER && unknown.setterName() != null) {
            return true;
        }
        return unknown.accessKind() == AccessKind.FIELD;
    }

    private static boolean writable(FieldModel field) {
        if (field.accessKind == AccessKind.GETTER_SETTER && field.setterName != null) {
            return true;
        }
        if (field.accessKind == AccessKind.FIELD && field.origin instanceof VariableElement variable) {
            return !variable.getModifiers().contains(Modifier.FINAL);
        }
        return false;
    }

    private List<ProtoMember> members(MessageScope scope, boolean record) {
        List<ProtoMember> members = new ArrayList<>();
        if (record) {
            for (MessageModel.RecordComponentModel component : scope.recordComponents) {
                boolean unknown = scope.unknown != null && component.name().equals(scope.unknown.name());
                members.add(new ProtoMember(
                    component.name(),
                    Names.safeLocal(component.name()),
                    component.type(),
                    component.field(),
                    unknown ? scope.unknown : null));
            }
            return members;
        }
        Set<String> seen = new HashSet<>();
        for (FieldModel field : scope.bindOrder) {
            members.add(new ProtoMember(field.name, field.localName, field.javaType, field, null));
            seen.add(field.name);
        }
        if (scope.unknown != null && seen.add(scope.unknown.name())) {
            members.add(new ProtoMember(
                scope.unknown.name(),
                scope.unknown.localName(),
                env.elements.getTypeElement("io.github.rawvoid.protovia.UnknownFields").asType(),
                null,
                scope.unknown));
        }
        return members;
    }

    private static List<String> setterNames(String property, String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            return List.of(prefix + Names.capitalize(property));
        }
        return List.of(property, Names.setterName(property), Names.witherName(property));
    }

    private static boolean isSyntheticName(String name) {
        return name.startsWith("arg") && name.length() > 3 && Character.isDigit(name.charAt(3));
    }

    static boolean hasLombokConstruction(TypeElement type) {
        TypeElement current = type;
        while (current != null) {
            for (var mirror : current.getAnnotationMirrors()) {
                Element annotation = mirror.getAnnotationType().asElement();
                if (annotation instanceof TypeElement te
                    && LOMBOK_CONSTRUCTION.contains(te.getQualifiedName().toString())) {
                    return true;
                }
            }
            TypeMirror parent = current.getSuperclass();
            if (parent instanceof DeclaredType declared
                && declared.asElement() instanceof TypeElement te
                && !te.getQualifiedName().contentEquals("java.lang.Object")) {
                current = te;
            } else {
                current = null;
            }
        }
        return false;
    }

    private record ProtoMember(
        String name,
        String localName,
        TypeMirror type,
        FieldModel field,
        MessageModel.UnknownField unknown) {
    }
}
