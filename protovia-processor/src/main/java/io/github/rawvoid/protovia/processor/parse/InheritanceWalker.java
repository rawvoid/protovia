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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks the class hierarchy of a {@code @ProtoMessage} leaf, validates mixin
 * superclasses, and specializes inherited member types.
 *
 * @author Rawvoid
 */
final class InheritanceWalker {

    /**
     * One specialized superclass in the flatten chain.
     *
     * @param declared the superclass type as viewed from the leaf (type args bound)
     * @param element  the superclass declaration
     */
    record SuperType(DeclaredType declared, TypeElement element) {
    }

    private final TypeEnv env;
    private final Diagnostics diag;

    InheritanceWalker(TypeEnv env, Diagnostics diag) {
        this.env = env;
        this.diag = diag;
    }

    /**
     * Superclasses from {@code Object}-nearest to the leaf, excluding the leaf
     * and {@code Object} / {@code Record} / {@code Enum}. Invalid supers are
     * diagnosed and omitted from the flatten list.
     */
    List<SuperType> collect(TypeElement leaf) {
        checkInterfaceMixins(leaf);
        List<SuperType> nearToFar = new ArrayList<>();
        TypeMirror current = leaf.asType();
        while (current.getKind() == TypeKind.DECLARED) {
            TypeElement currentEl = env.asTypeElement(current);
            if (currentEl == null || isSkippedSuper(currentEl)) {
                break;
            }
            TypeMirror superMirror = superclassOf(current);
            if (superMirror == null || superMirror.getKind() == TypeKind.NONE) {
                break;
            }
            TypeElement superEl = env.asTypeElement(superMirror);
            if (superEl == null || isSkippedSuper(superEl)) {
                break;
            }
            if (!(superMirror instanceof DeclaredType declared)) {
                break;
            }
            if (superEl.getAnnotation(ProtoMessage.class) != null) {
                diag.error(leaf, "inheritance of @ProtoMessage types is not supported");
            } else if (isRaw(declared, superEl)) {
                diag.error(leaf, "raw superclass " + superEl.getSimpleName()
                    + " is not supported; bind type parameters");
            } else if (hasUnboundArgs(declared)) {
                diag.error(leaf, "unbound type arguments on superclass "
                    + superEl.getSimpleName() + " are not supported");
            } else {
                nearToFar.add(new SuperType(declared, superEl));
            }
            current = superMirror;
        }
        Collections.reverse(nearToFar);
        return nearToFar;
    }

    /**
     * @return member with the type viewed from {@code declared}, or {@code null}
     *     if the specialized type is still unbound
     */
    Member specialize(DeclaredType declared, Member member) {
        TypeMirror type = member.type();
        try {
            TypeMirror asMember = env.types.asMemberOf(declared, member.origin());
            if (asMember instanceof ExecutableType executable) {
                type = executable.getReturnType();
            } else {
                type = asMember;
            }
        } catch (IllegalArgumentException ignored) {
            // keep the declaration type
        }
        if (!isFullyBound(type)) {
            diag.error(member.origin(), "inherited member '" + member.name()
                + "' has an unbound type; bind type parameters on the @ProtoMessage subclass");
            return null;
        }
        if (env.types.isSameType(type, member.type())) {
            return member;
        }
        return new Member(
            member.origin(),
            member.name(),
            type,
            member.recordComponent(),
            member.roles(),
            member.protoField(),
            member.protoOneofAnn());
    }

    static boolean hasProtoMembers(TypeElement type) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getAnnotation(ProtoField.class) != null
                || enclosed.getAnnotation(ProtoOneof.class) != null
                || enclosed.getAnnotation(ProtoUnknown.class) != null) {
                return true;
            }
        }
        return false;
    }

    private void checkInterfaceMixins(TypeElement leaf) {
        Set<TypeElement> seen = new HashSet<>();
        TypeElement current = leaf;
        while (current != null && !isSkippedSuper(current)) {
            for (TypeMirror iface : current.getInterfaces()) {
                walkInterface(iface, seen, leaf);
            }
            current = env.asTypeElement(current.getSuperclass());
        }
    }

    private void walkInterface(TypeMirror iface, Set<TypeElement> seen, TypeElement leaf) {
        TypeElement el = env.asTypeElement(iface);
        if (el == null || !seen.add(el)) {
            return;
        }
        if (hasProtoMembers(el)) {
            diag.error(leaf, "interface " + el.getSimpleName()
                + " has proto members; interface mixin is not supported");
        }
        for (TypeMirror parent : el.getInterfaces()) {
            walkInterface(parent, seen, leaf);
        }
    }

    private TypeMirror superclassOf(TypeMirror type) {
        for (TypeMirror direct : env.types.directSupertypes(type)) {
            TypeElement el = env.asTypeElement(direct);
            if (el != null && el.getKind() == ElementKind.CLASS) {
                return direct;
            }
        }
        return null;
    }

    private static boolean isRaw(DeclaredType declared, TypeElement element) {
        return !element.getTypeParameters().isEmpty() && declared.getTypeArguments().isEmpty();
    }

    private static boolean hasUnboundArgs(DeclaredType declared) {
        for (TypeMirror arg : declared.getTypeArguments()) {
            if (!isFullyBound(arg)) {
                return true;
            }
        }
        return false;
    }

    static boolean isFullyBound(TypeMirror type) {
        TypeKind kind = type.getKind();
        if (kind == TypeKind.TYPEVAR || kind == TypeKind.WILDCARD || kind == TypeKind.ERROR) {
            return false;
        }
        if (kind == TypeKind.ARRAY) {
            return isFullyBound(((ArrayType) type).getComponentType());
        }
        if (kind == TypeKind.DECLARED) {
            for (TypeMirror arg : ((DeclaredType) type).getTypeArguments()) {
                if (!isFullyBound(arg)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSkippedSuper(TypeElement type) {
        String qn = type.getQualifiedName().toString();
        return qn.equals("java.lang.Object")
            || qn.equals("java.lang.Record")
            || qn.equals("java.lang.Enum");
    }
}
