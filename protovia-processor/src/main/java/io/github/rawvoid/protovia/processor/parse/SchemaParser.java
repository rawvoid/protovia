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
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.annotation.ProtoOneof;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Validates {@code @ProtoMessage} / {@code @ProtoEnum} types and builds the models
 * consumed by {@link io.github.rawvoid.protovia.processor.gen.CodecGenerator}.
 *
 * @author Rawvoid
 */
public final class SchemaParser {

    private final TypeEnv env;
    private final Diagnostics diag;
    private final EnumParser enums;
    private final AdapterResolver adapters;
    private final FieldResolver fields;
    private final OneofParser oneofs;
    private final MemberScanner scanner;

    public SchemaParser(Types types, Elements elements, Messager messager) {
        this.env = new TypeEnv(types, elements);
        this.diag = new Diagnostics(messager);
        this.enums = new EnumParser(diag);
        TypeClassifier classifier = new TypeClassifier(env, diag, enums);
        this.adapters = new AdapterResolver(env, diag, classifier);
        this.fields = new FieldResolver(env, diag, classifier, adapters);
        this.oneofs = new OneofParser(env, diag, fields, adapters);
        this.scanner = new MemberScanner(diag);
    }

    /**
     * @return {@code true} if any diagnostic error was reported in this parser
     */
    public boolean hasErrors() {
        return diag.hasErrors();
    }

    /**
     * @param type {@code @ProtoEnum} type
     * @return model, or {@code null} if the enum is invalid
     */
    public EnumModel parseEnum(TypeElement type) {
        return enums.parse(type);
    }

    /**
     * @param type {@code @ProtoMessage} class or record
     * @return model, or {@code null} if the message is invalid
     */
    public MessageModel parseMessage(TypeElement type) {
        diag.push();
        MessageModel model = doParseMessage(type);
        diag.popAndMerge();
        return model;
    }

    private MessageModel doParseMessage(TypeElement type) {
        if (type.getKind() != ElementKind.CLASS && type.getKind() != ElementKind.RECORD) {
            diag.error(type, "@ProtoMessage is only valid on classes and records");
            return null;
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            diag.error(type, "@ProtoMessage type cannot be abstract");
        }
        if (type.getEnclosingElement().getKind() != ElementKind.PACKAGE
            && type.getKind() == ElementKind.CLASS
            && !type.getModifiers().contains(Modifier.STATIC)) {
            diag.error(type, "non-static inner @ProtoMessage is not supported");
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

        MessageScope scope = new MessageScope(type, pkg);
        adapters.enter(type);
        try {
            for (Member member : scanner.scan(type)) {
                dispatch(member, scope);
            }
            if (scope.byNumber.isEmpty() && scope.oneofs.isEmpty()) {
                diag.error(type, "@ProtoMessage " + type.getSimpleName() + " has no @ProtoField or @ProtoOneof members");
            }
            if (diag.failed()) {
                return null;
            }
            return scope.toModel(
                protoPackage,
                protoMessageName,
                typeName,
                Names.codecSimpleName(env.elements, type),
                record);
        } finally {
            adapters.exit();
        }
    }

    /**
     * Single annotation dispatch for record components, POJO fields, and getters.
     * Error order matches the previous three copies of this logic.
     */
    private void dispatch(Member member, MessageScope scope) {
        if (member.protoUnknown()) {
            bindUnknown(member, scope);
            return;
        }
        if (member.protoOneof()) {
            bindOneof(member, scope);
            return;
        }
        if (member.protoField() != null) {
            bindField(member, scope);
            return;
        }
        if (member.recordComponent()) {
            scope.addComponent(member.name(), member.type(), null);
        }
    }

    private void bindUnknown(Member member, MessageScope scope) {
        if (member.fieldOnSameElement()) {
            diag.error(member.origin(), "cannot combine @ProtoUnknown with @ProtoField");
            return;
        }
        if (member.fromMethod() && member.name() == null) {
            diag.error(member.origin(), "@ProtoUnknown on a method must be a JavaBean getter");
            return;
        }
        Access access = scanner.completeAccess(member);
        if (access == null) {
            return;
        }
        if (scope.bindUnknown(member.origin(), member.type(), access, member.name(), env, diag)
            && member.recordComponent()) {
            scope.addComponent(member.name(), member.type(), null);
        }
    }

    private void bindOneof(Member member, MessageScope scope) {
        if (member.fieldOnSameElement()) {
            diag.error(member.origin(), "cannot combine @ProtoOneof with @ProtoField");
            return;
        }
        if (member.fromMethod() && badMethodGetter(member, "@ProtoOneof")) {
            return;
        }
        if (member.fromMethod() && scope.annotatedViaField.contains(member.name())) {
            diag.error(member.origin(), "field '" + member.name()
                + "' is already annotated; do not also annotate the getter");
            return;
        }
        Access access = scanner.completeAccess(member);
        if (access == null) {
            return;
        }
        FieldModel oneof = oneofs.resolve(
            member.origin(),
            member.name(),
            member.type(),
            access.kind(),
            access.readExpr(),
            access.setter(),
            member.name(),
            scope.pkg,
            scope.taken);
        if (oneof != null && scope.claimed.add(member.name())) {
            if (member.fromField()) {
                scope.annotatedViaField.add(member.name());
            }
            scope.oneofs.add(oneof);
            if (member.recordComponent()) {
                scope.addComponent(member.name(), oneof.javaType, oneof);
            }
        }
    }

    private void bindField(Member member, MessageScope scope) {
        if (member.fromField()) {
            scope.annotatedViaField.add(member.name());
        }
        if (member.fromMethod()) {
            if (badMethodGetter(member, "@ProtoField")) {
                return;
            }
            if (scope.annotatedViaField.contains(member.name())) {
                diag.error(member.origin(), "field '" + member.name()
                    + "' is already annotated; do not also annotate the getter");
                return;
            }
        }
        Access access = scanner.completeAccess(member);
        if (access == null) {
            return;
        }
        FieldModel field = fields.resolveField(
            member.origin(),
            member.name(),
            member.type(),
            member.protoField(),
            access.kind(),
            access.readExpr(),
            access.setter(),
            member.name(),
            scope.pkg);
        if (field != null && scope.addField(field, diag)) {
            if (member.recordComponent()) {
                scope.addComponent(member.name(), member.type(), field);
            }
        } else if (member.recordComponent()) {
            scope.addComponent(member.name(), member.type(), null);
        }
    }

    private boolean badMethodGetter(Member member, String annotation) {
        if (member.name() == null) {
            diag.error(member.origin(), annotation + " on a method must be a JavaBean getter");
            return true;
        }
        ExecutableElement method = (ExecutableElement) member.origin();
        if (!method.getParameters().isEmpty() || method.getReturnType().getKind() == TypeKind.VOID) {
            diag.error(member.origin(), annotation + " on a method must be a JavaBean getter");
            return true;
        }
        return false;
    }

    private void checkInheritance(TypeElement type) {
        TypeMirror superType = type.getSuperclass();
        while (superType != null && superType.getKind() != TypeKind.NONE
            && !env.types.isSameType(superType, env.objectType)) {
            TypeElement superElement = env.asTypeElement(superType);
            if (superElement == null) {
                break;
            }
            if (superElement.getAnnotation(ProtoMessage.class) != null) {
                diag.error(type, "inheritance of @ProtoMessage types is not supported");
                return;
            }
            for (Element enclosed : superElement.getEnclosedElements()) {
                if (enclosed.getAnnotation(ProtoField.class) != null
                    || enclosed.getAnnotation(ProtoOneof.class) != null
                    || enclosed.getAnnotation(ProtoUnknown.class) != null) {
                    diag.error(type, "superclass " + superElement.getSimpleName()
                        + " has proto members; inheritance is not supported");
                    return;
                }
            }
            superType = superElement.getSuperclass();
        }
    }

    private void checkNoArgConstructor(TypeElement type) {
        for (ExecutableElement ctor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
            if (ctor.getParameters().isEmpty() && !ctor.getModifiers().contains(Modifier.PRIVATE)) {
                return;
            }
        }
        diag.error(type, "@ProtoMessage class " + type.getSimpleName() + " needs a non-private no-arg constructor");
    }
}
