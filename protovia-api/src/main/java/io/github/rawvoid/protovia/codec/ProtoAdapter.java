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

package io.github.rawvoid.protovia.codec;

import io.github.rawvoid.protovia.ProtoException;

/**
 * Stateless, thread-safe conversion between a Java value and a proto scalar.
 * Implementations expose {@code public static final INSTANCE} (same convention as codecs).
 * {@link #toWire} and {@link #fromWire} never return {@code null}; throw
 * {@link ProtoException} for domain failures.
 *
 * @param <J> Java field type (must be a reference type)
 * @param <W> Java representation of the proto scalar (boxed)
 * @author Rawvoid
 */
public interface ProtoAdapter<J, W> {

    /**
     * @param value Java value; never {@code null} on the generated singular / repeated path
     * @return wire representation; never {@code null}
     */
    W toWire(J value);

    /**
     * @param wire proto scalar; never {@code null}
     * @return Java value; never {@code null}
     */
    J fromWire(W wire);

    /**
     * Annotation default for {@code @ProtoField.adapter} / {@code @ProtoOneof.Case.adapter}.
     * Not a real adapter.
     */
    interface Unset extends ProtoAdapter<Void, Void> {}
}
