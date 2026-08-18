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
 * Opt-in {@link UUID} as proto3 {@code bytes} (16 bytes in big-endian network byte order).
 * Pairs with {@link UuidString} when compact binary representation is preferred.
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.BYTES)
public final class UuidBytes implements ProtoAdapter<UUID, byte[]> {

    public static final UuidBytes INSTANCE = new UuidBytes();

    private UuidBytes() {
    }

    @Override
    public byte[] toWire(UUID value) {
        long msb = value.getMostSignificantBits();
        long lsb = value.getLeastSignificantBits();
        byte[] bytes = new byte[16];
        bytes[0] = (byte) (msb >>> 56);
        bytes[1] = (byte) (msb >>> 48);
        bytes[2] = (byte) (msb >>> 40);
        bytes[3] = (byte) (msb >>> 32);
        bytes[4] = (byte) (msb >>> 24);
        bytes[5] = (byte) (msb >>> 16);
        bytes[6] = (byte) (msb >>> 8);
        bytes[7] = (byte) msb;
        bytes[8] = (byte) (lsb >>> 56);
        bytes[9] = (byte) (lsb >>> 48);
        bytes[10] = (byte) (lsb >>> 40);
        bytes[11] = (byte) (lsb >>> 32);
        bytes[12] = (byte) (lsb >>> 24);
        bytes[13] = (byte) (lsb >>> 16);
        bytes[14] = (byte) (lsb >>> 8);
        bytes[15] = (byte) lsb;
        return bytes;
    }

    @Override
    public UUID fromWire(byte[] wire) {
        if (wire == null || wire.length != 16) {
            throw new ProtoException("invalid UUID bytes: expected exactly 16 bytes, got " + (wire == null ? 0 : wire.length));
        }
        long msb = ((long) (wire[0] & 0xFF) << 56)
                | ((long) (wire[1] & 0xFF) << 48)
                | ((long) (wire[2] & 0xFF) << 40)
                | ((long) (wire[3] & 0xFF) << 32)
                | ((long) (wire[4] & 0xFF) << 24)
                | ((long) (wire[5] & 0xFF) << 16)
                | ((long) (wire[6] & 0xFF) << 8)
                | ((long) (wire[7] & 0xFF));
        long lsb = ((long) (wire[8] & 0xFF) << 56)
                | ((long) (wire[9] & 0xFF) << 48)
                | ((long) (wire[10] & 0xFF) << 40)
                | ((long) (wire[11] & 0xFF) << 32)
                | ((long) (wire[12] & 0xFF) << 24)
                | ((long) (wire[13] & 0xFF) << 16)
                | ((long) (wire[14] & 0xFF) << 8)
                | ((long) (wire[15] & 0xFF));
        return new UUID(msb, lsb);
    }
}
