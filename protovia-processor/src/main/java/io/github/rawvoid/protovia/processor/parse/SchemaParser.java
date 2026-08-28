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

import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Instantiation;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.Reserved;

import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;

/**
 * Validates {@code @ProtoMessage} / {@code @ProtoEnum} types and builds the models
 * consumed by {@link io.github.rawvoid.protovia.processor.gen.CodecGenerator}.
 *
 * @author Rawvoid
 */
public final class SchemaParser {

    private final TypeEnv env;
    private final Diagnostics diag;
    private final ReservedParser reserved;
    private final EnumParser enums;
    private final AdapterResolver adapters;
    private final FieldResolver fields;
    private final OneofParser oneofs;
    private final MemberScanner scanner;
    private final InheritanceWalker inheritance;
    private final ConstructionResolver construction;
    private boolean deferred;

    public SchemaParser(Types types, Elements elements, Messager messager) {
        this.env = new TypeEnv(types, elements);
        this.diag = new Diagnostics(messager);
        this.reserved = new ReservedParser(diag);
        this.enums = new EnumParser(diag, reserved);
        TypeClassifier classifier = new TypeClassifier(env, diag, enums);
        this.adapters = new AdapterResolver(env, diag, classifier);
        this.fields = new FieldResolver(env, diag, classifier, adapters);
        this.oneofs = new OneofParser(env, diag, fields, adapters);
        this.scanner = new MemberScanner(diag);
        this.inheritance = new InheritanceWalker(env, diag);
        this.construction = new ConstructionResolver(env, diag);
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
     * @return {@code true} if construction resolution was deferred for Lombok
     */
    public boolean wasDeferred() {
        return deferred;
    }

    /**
     * @param type {@code @ProtoMessage} class or record
     * @return model, or {@code null} if the message is invalid or deferred
     */
    public MessageModel parseMessage(TypeElement type) {
        return parseMessage(type, false);
    }

    /**
     * @param allowDefer when {@code true}, Lombok types missing a construction
     *                   path are skipped this round instead of failing
     */
    public MessageModel parseMessage(TypeElement type, boolean allowDefer) {
        deferred = false;
        diag.push();
        try {
            return doParseMessage(type, allowDefer);
        } finally {
            diag.popAndMerge();
        }
    }

    private MessageModel doParseMessage(TypeElement type, boolean allowDefer) {
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

        boolean record = type.getKind() == ElementKind.RECORD;

        String pkg = Names.packageName(type);
        String typeName = Names.typeName(type, pkg);
        ProtoMessage meta = type.getAnnotation(ProtoMessage.class);
        String protoPackage = Names.protoPackage(type, meta == null ? "" : meta.packageName());
        String protoMessageName = meta == null || meta.name().isBlank()
            ? type.getSimpleName().toString()
            : meta.name().trim();
        ExportNames.requirePackage(diag, type, protoPackage);
        ExportNames.require(diag, type, protoMessageName);

        List<InheritanceWalker.SuperType> supers = inheritance.collect(type);
        MessageScope scope = new MessageScope(type, pkg);
        Reserved.Builder reservedUnion = Reserved.builder();
        for (InheritanceWalker.SuperType superType : supers) {
            reservedUnion.addAll(reserved.parse(superType.element(), ReservedParser.Scope.MESSAGE));
        }
        reservedUnion.addAll(reserved.parse(type, ReservedParser.Scope.MESSAGE));
        scope.reserved = reservedUnion.build();
        adapters.enter(type);
        try {
            for (InheritanceWalker.SuperType superType : supers) {
                ScanResult scanned = scanner.scan(superType.element());
                for (Member member : scanned.members()) {
                    Member specialized = inheritance.specialize(superType.declared(), member);
                    if (specialized == null) {
                        continue;
                    }
                    dispatch(specialized, scope, scanned.methods(), superType);
                }
            }
            ScanResult scanned = scanner.scan(type);
            for (Member member : scanned.members()) {
                dispatch(member, scope, scanned.methods(), null);
            }
            if (scope.byNumber.isEmpty() && scope.oneofs.isEmpty()) {
                diag.error(type, "@ProtoMessage " + type.getSimpleName() + " has no @ProtoField or @ProtoOneof members");
            }
            if (diag.failed()) {
                return null;
            }
            Instantiation instantiation = construction.resolve(type, scope, record, allowDefer);
            if (construction.wasDeferred()) {
                deferred = true;
                return null;
            }
            if (instantiation == null) {
                return null;
            }
            return scope.toModel(
                Names.codecPackageName(type),
                protoPackage,
                protoMessageName,
                typeName,
                Names.codecSimpleName(env.elements, type),
                record,
                instantiation);
        } finally {
            adapters.exit();
        }
    }

    /**
     * Role check, then bind. A field-origin role occupies the property name
     * whether or not bind succeeds, so a getter cannot also claim it.
     */
    private void dispatch(
        Member member,
        MessageScope scope,
        Map<String, ExecutableElement> methods,
        InheritanceWalker.SuperType superType) {
        if (member.recordComponent() && !member.roles().any()) {
            scope.addComponent(member.name(), member.type(), null);
            return;
        }
        if (rejectCombinedRoles(member)) {
            return;
        }
        if (member.fromField() && member.roles().any()) {
            scope.annotatedViaField.add(member.name());
        }
        if (member.roles().unknown()) {
            bindUnknown(member, scope, methods, superType);
            return;
        }
        if (member.roles().oneof()) {
            // Record component vs accessor conflict already diagnosed; do not parse the leftover site.
            if (member.protoOneofAnn() == null) {
                return;
            }
            bindOneof(member, scope, methods, superType);
            return;
        }
        if (member.protoField() != null) {
            bindField(member, scope, methods, superType);
        }
    }

    /**
     * @return {@code true} if this site mixed proto roles and should not bind
     */
    private boolean rejectCombinedRoles(Member member) {
        Roles roles = member.roles();
        if (roles.count() <= 1) {
            return false;
        }
        if (roles.unknown() && roles.oneof()) {
            diag.error(member.origin(), "cannot combine @ProtoOneof with @ProtoUnknown");
        }
        if (roles.unknown() && roles.field()) {
            diag.error(member.origin(), "cannot combine @ProtoUnknown with @ProtoField");
        }
        if (roles.oneof() && roles.field()) {
            diag.error(member.origin(), "cannot combine @ProtoOneof with @ProtoField");
        }
        return true;
    }

    private boolean alreadyAnnotatedOnGetter(Member member, MessageScope scope) {
        if (!member.fromMethod() || member.name() == null
            || !scope.annotatedViaField.contains(member.name())) {
            return false;
        }
        diag.error(member.origin(), "field '" + member.name()
            + "' is already annotated; do not also annotate the getter");
        return true;
    }

    private void bindUnknown(
        Member member,
        MessageScope scope,
        Map<String, ExecutableElement> methods,
        InheritanceWalker.SuperType superType) {
        if (member.fromMethod() && member.name() == null) {
            diag.error(member.origin(), "@ProtoUnknown on a method must be a JavaBean getter");
            return;
        }
        if (alreadyAnnotatedOnGetter(member, scope)) {
            return;
        }
        if (!scope.checkUnknownType(member.origin(), member.type(), env, diag)) {
            return;
        }
        Access access = resolveAccess(member, scope, methods, superType);
        if (access == null) {
            return;
        }
        if (scope.bindUnknown(member.origin(), member.type(), access, member.name(), env, diag)
            && member.recordComponent()) {
            scope.addComponent(member.name(), member.type(), null);
        }
    }

    private void bindOneof(
        Member member,
        MessageScope scope,
        Map<String, ExecutableElement> methods,
        InheritanceWalker.SuperType superType) {
        if (member.fromMethod() && badMethodGetter(member, "@ProtoOneof")) {
            return;
        }
        if (alreadyAnnotatedOnGetter(member, scope)) {
            return;
        }
        Access access = resolveAccess(member, scope, methods, superType);
        if (access == null) {
            return;
        }
        FieldModel oneof = oneofs.resolve(
            member.origin(),
            member.name(),
            member.type(),
            member.protoOneofAnn(),
            access.kind(),
            access.readExpr(),
            access.setter(),
            member.name(),
            scope.pkg,
            scope.taken,
            scope.reserved);
        if (oneof != null && scope.claimJava(oneof.origin, oneof.name, diag)
            && scope.claimOneof(oneof, diag)) {
            scope.addOneof(oneof);
            if (member.recordComponent()) {
                scope.addComponent(member.name(), oneof.javaType, oneof);
            }
        }
    }

    private Access resolveAccess(
        Member member,
        MessageScope scope,
        Map<String, ExecutableElement> methods,
        InheritanceWalker.SuperType superType) {
        String inheritedOwner = superType == null
            ? null
            : env.renderType(superType.declared(), "");
        Access access = scanner.resolveAccess(member, methods, inheritedOwner);
        if (access == null) {
            return null;
        }
        if (superType != null
            && access.kind() == AccessKind.FIELD
            && !env.typeVisibleFromCodec(superType.declared(), Names.codecPackageName(scope.type))) {
            diag.error(member.origin(), "inherited field '" + member.name()
                + "' on " + superType.element().getSimpleName()
                + " is not accessible from " + Names.codecPackageName(scope.type)
                + "; make the superclass public, or provide a public getter");
            return null;
        }
        return access;
    }

    private void bindField(
        Member member,
        MessageScope scope,
        Map<String, ExecutableElement> methods,
        InheritanceWalker.SuperType superType) {
        if (member.fromMethod() && badMethodGetter(member, "@ProtoField")) {
            return;
        }
        if (alreadyAnnotatedOnGetter(member, scope)) {
            return;
        }
        Access access = resolveAccess(member, scope, methods, superType);
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
}
