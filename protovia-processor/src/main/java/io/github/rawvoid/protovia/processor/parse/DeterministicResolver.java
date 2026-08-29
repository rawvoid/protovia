/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.processor.parse;

import io.github.rawvoid.protovia.annotation.ProtoDeterministic;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Resolves {@link ProtoDeterministic}: field → leaf type → mixin chain
 * (near to far) → leaf package-info → {@code false}.
 *
 * @author Rawvoid
 */
final class DeterministicResolver {

    static final String ONLY_MAPS =
        "@ProtoDeterministic is only valid on Map fields, @ProtoMessage types, mixin superclasses, or package-info";

    private final TypeEnv env;
    private final Diagnostics diag;
    private TypeElement leaf;

    DeterministicResolver(TypeEnv env, Diagnostics diag) {
        this.env = env;
        this.diag = diag;
    }

    void enter(TypeElement leaf) {
        this.leaf = leaf;
    }

    void exit() {
        this.leaf = null;
    }

    /**
     * @param origin field, getter, or record component
     * @return resolved flag for a map member
     */
    boolean resolve(Element origin) {
        Boolean field = declaredOn(origin);
        if (field != null) {
            return field;
        }
        Boolean onLeaf = read(leaf);
        if (onLeaf != null) {
            return onLeaf;
        }
        TypeMirror superType = leaf.getSuperclass();
        while (superType.getKind() != TypeKind.NONE) {
            TypeElement el = env.asTypeElement(superType);
            if (el == null) {
                break;
            }
            String qn = el.getQualifiedName().toString();
            if (qn.equals("java.lang.Object") || qn.equals("java.lang.Record") || qn.equals("java.lang.Enum")) {
                break;
            }
            Boolean onSuper = read(el);
            if (onSuper != null) {
                return onSuper;
            }
            superType = el.getSuperclass();
        }
        Boolean pkg = read(env.elements.getPackageOf(leaf));
        return pkg != null && pkg;
    }

    /**
     * Errors when {@code origin} (or a record accessor) carries the annotation.
     */
    void reject(Element origin) {
        if (declaredOn(origin) != null) {
            diag.error(origin, ONLY_MAPS);
        }
    }

    /**
     * Field-level annotation, including a record component's accessor.
     */
    Boolean declaredOn(Element origin) {
        Boolean direct = read(origin);
        if (direct != null) {
            return direct;
        }
        if (origin != null && origin.getKind() == ElementKind.RECORD_COMPONENT) {
            return read(((RecordComponentElement) origin).getAccessor());
        }
        return null;
    }

    static Boolean read(Element element) {
        if (element == null) {
            return null;
        }
        ProtoDeterministic ann = element.getAnnotation(ProtoDeterministic.class);
        return ann == null ? null : ann.value();
    }
}
