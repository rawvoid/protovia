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

package io.github.rawvoid.protovia;

import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

import java.util.Arrays;

/**
 * Opaque, ordered capture of unrecognized protobuf fields (tag + payload).
 * Immutable. {@link #EMPTY} is the unset slot.
 * Unknown enum numbers on map values, and unknown enums when the message has
 * no {@code @ProtoUnknown} slot, are not captured.
 *
 * @author Rawvoid
 */
public final class UnknownFields {

    public static final UnknownFields EMPTY = new UnknownFields(new byte[0]);

    private final byte[] bytes;

    private UnknownFields(byte[] bytes) {
        this.bytes = bytes;
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public int serializedSize() {
        return bytes.length;
    }

    public void writeTo(ProtoWriter writer) {
        if (bytes.length != 0) {
            writer.writeRawBytes(bytes, 0, bytes.length);
        }
    }

    UnknownFields append(byte[] chunk) {
        if (chunk.length == 0) {
            return this;
        }
        if (bytes.length == 0) {
            return new UnknownFields(chunk);
        }
        byte[] next = Arrays.copyOf(bytes, bytes.length + chunk.length);
        System.arraycopy(chunk, 0, next, bytes.length, chunk.length);
        return new UnknownFields(next);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UnknownFields other && Arrays.equals(bytes, other.bytes);
    }

    /**
     * Captures the field at the reader's current tag and appends it.
     *
     * @param existing previous capture; {@code null} is treated as {@link #EMPTY}
     * @param reader   positioned on the unknown tag
     * @return a new instance if anything was appended
     */
    public static UnknownFields merge(UnknownFields existing, ProtoReader reader) {
        UnknownFields base = existing == null ? EMPTY : existing;
        return base.append(reader.captureField());
    }

    /**
     * Appends an already-consumed varint field (used when an enum number is unrecognized).
     * {@code number} is encoded as proto {@code int32} (10 bytes when negative).
     *
     * @param existing previous capture; {@code null} is treated as {@link #EMPTY}
     * @param tag      wire tag (unpacked varint)
     * @param number   enum number
     * @return a new instance if anything was appended
     */
    public static UnknownFields mergeVarint(UnknownFields existing, int tag, int number) {
        UnknownFields base = existing == null ? EMPTY : existing;
        return base.append(encodeTagAndVarint(tag, number));
    }

    private static byte[] encodeTagAndVarint(int tag, int number) {
        int tagSize = io.github.rawvoid.protovia.wire.CodedSize.uint32(tag);
        int numSize = io.github.rawvoid.protovia.wire.CodedSize.int32(number);
        byte[] out = new byte[tagSize + numSize];
        int i = writeVarint32(out, 0, tag);
        if (number >= 0) {
            writeVarint32(out, i, number);
        } else {
            writeVarint64(out, i, number);
        }
        return out;
    }

    private static int writeVarint32(byte[] out, int i, int value) {
        while ((value & ~0x7F) != 0) {
            out[i++] = (byte) (value | 0x80);
            value >>>= 7;
        }
        out[i++] = (byte) value;
        return i;
    }

    private static int writeVarint64(byte[] out, int i, long value) {
        while ((value & ~0x7FL) != 0L) {
            out[i++] = (byte) ((int) value | 0x80);
            value >>>= 7;
        }
        out[i++] = (byte) value;
        return i;
    }
}
