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

package io.github.rawvoid.protovia.adapter;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.util.UUID;

/**
 * Opt-in {@link UUID} as proto3 {@code string} (canonical 36-char form). Unused
 * unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.STRING)
public final class UuidString implements ProtoAdapter<UUID, String> {

    public static final UuidString INSTANCE = new UuidString();

    private UuidString() {
    }

    @Override
    public String toWire(UUID value) {
        return value.toString();
    }

    @Override
    public UUID fromWire(String wire) {
        try {
            return UUID.fromString(wire);
        } catch (IllegalArgumentException e) {
            throw new ProtoException("invalid UUID: " + wire, e);
        }
    }
}
