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

import io.github.rawvoid.protovia.wire.CodedSize;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.WireType;

import java.util.Arrays;
import java.util.Objects;

/**
 * Opaque, ordered capture of unrecognized protobuf fields (tag + payload).
 * Immutable. {@link #EMPTY} is the unset slot.
 * Use {@link Builder} for O(N) accumulation of multiple unknown fields during decoding.
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

    /**
     * Returns the serialized byte length excluding any fields whose field number appears in
     * {@code excludedFieldNumbers}.
     *
     * @param excludedFieldNumbers field numbers to exclude
     * @return filtered byte length
     */
    public int serializedSizeExcluding(int... excludedFieldNumbers) {
        if (excludedFieldNumbers == null || excludedFieldNumbers.length == 0) {
            return bytes.length;
        }
        return serializedSizeExcluding(excludedFieldNumbers, excludedFieldNumbers.length);
    }

    /**
     * Returns the serialized byte length excluding any fields whose field number appears in
     * {@code excludedFieldNumbers[0 .. count)}.
     *
     * @param excludedFieldNumbers array containing field numbers to exclude
     * @param count                number of active entries in {@code excludedFieldNumbers}
     * @return filtered byte length
     */
    public int serializedSizeExcluding(int[] excludedFieldNumbers, int count) {
        validateExcluded(excludedFieldNumbers, count);
        if (bytes.length == 0 || count == 0) {
            return bytes.length;
        }
        long queryMask = buildFieldMask(excludedFieldNumbers, count);
        if (queryMask == 0L) {
            return bytes.length;
        }
        ProtoReader r = internalReader(bytes);
        int total = 0;
        while (true) {
            int start = r.position();
            int tag = r.readTag();
            if (tag == 0) {
                break;
            }
            r.skipField(tag);
            int end = r.position();
            int fieldNumber = WireType.fieldNumber(tag);
            if ((queryMask & fieldBit(fieldNumber)) == 0L
                || !containsField(excludedFieldNumbers, count, fieldNumber)) {
                total += end - start;
            }
        }
        return total;
    }

    /**
     * Writes all captured fields except those whose field number appears in
     * {@code excludedFieldNumbers}.
     *
     * @param writer               destination writer
     * @param excludedFieldNumbers field numbers to exclude
     */
    public void writeToExcluding(ProtoWriter writer, int... excludedFieldNumbers) {
        Objects.requireNonNull(writer, "writer");
        if (excludedFieldNumbers == null || excludedFieldNumbers.length == 0) {
            writeTo(writer);
            return;
        }
        writeToExcluding(writer, excludedFieldNumbers, excludedFieldNumbers.length);
    }

    /**
     * Writes all captured fields except those whose field number appears in
     * {@code excludedFieldNumbers[0 .. count)}.
     *
     * @param writer               destination writer
     * @param excludedFieldNumbers array containing field numbers to exclude
     * @param count                number of active entries in {@code excludedFieldNumbers}
     */
    public void writeToExcluding(ProtoWriter writer, int[] excludedFieldNumbers, int count) {
        Objects.requireNonNull(writer, "writer");
        validateExcluded(excludedFieldNumbers, count);
        if (bytes.length == 0) {
            return;
        }
        if (count == 0) {
            writeTo(writer);
            return;
        }
        long queryMask = buildFieldMask(excludedFieldNumbers, count);
        if (queryMask == 0L) {
            writeTo(writer);
            return;
        }
        ProtoReader r = internalReader(bytes);
        int runStart = 0;
        while (true) {
            int start = r.position();
            int tag = r.readTag();
            if (tag == 0) {
                break;
            }
            r.skipField(tag);
            int end = r.position();
            int fieldNumber = WireType.fieldNumber(tag);
            if ((queryMask & fieldBit(fieldNumber)) != 0L
                && containsField(excludedFieldNumbers, count, fieldNumber)) {
                if (start > runStart) {
                    writer.writeRawBytes(bytes, runStart, start - runStart);
                }
                runStart = end;
            }
        }
        if (bytes.length > runStart) {
            writer.writeRawBytes(bytes, runStart, bytes.length - runStart);
        }
    }

    /**
     * Creates a new empty {@link Builder}.
     */
    public static Builder newBuilder() {
        return new Builder(EMPTY);
    }

    /**
     * Creates a {@link Builder} seeded with {@code existing}.
     * If no mutations are made, {@link Builder#build()} returns {@code existing} directly without copying.
     *
     * @param existing initial unknown fields; {@code null} is treated as {@link #EMPTY}
     */
    public static Builder newBuilder(UnknownFields existing) {
        return new Builder(existing == null ? EMPTY : existing);
    }

    /**
     * Creates a {@link Builder} seeded with this instance.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    UnknownFields append(byte[] chunk) {
        if (chunk.length == 0) {
            return this;
        }
        if (bytes.length == 0) {
            return new UnknownFields(chunk);
        }
        if (chunk.length > Integer.MAX_VALUE - bytes.length) {
            throw new ProtoException("unknown fields size overflow");
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
        validateVarintTag(tag);
        UnknownFields base = existing == null ? EMPTY : existing;
        return base.append(encodeTagAndVarint(tag, number));
    }

    private static ProtoReader internalReader(byte[] data) {
        return new ProtoReader(data, 0, data.length, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static void validateFieldTag(int tag) {
        int wireType = WireType.getWireType(tag);
        if (WireType.fieldNumber(tag) == 0
            || wireType == WireType.END_GROUP
            || wireType > WireType.I32) {
            throw ProtoException.invalidTag(tag);
        }
    }

    private static void validateVarintTag(int tag) {
        if (WireType.fieldNumber(tag) == 0 || WireType.getWireType(tag) != WireType.VARINT) {
            throw ProtoException.invalidTag(tag);
        }
    }

    private static void validateExcluded(int[] excludedFieldNumbers, int count) {
        if (count < 0 || (count > 0 && (excludedFieldNumbers == null || count > excludedFieldNumbers.length))) {
            throw new IllegalArgumentException("invalid excluded count: " + count);
        }
    }

    private static long fieldBit(int fieldNumber) {
        return 1L << (fieldNumber & 63);
    }

    private static long buildFieldMask(int[] fieldNumbers, int count) {
        long mask = 0L;
        for (int i = 0; i < count; i++) {
            int fn = fieldNumbers[i];
            if (fn > 0) {
                mask |= fieldBit(fn);
            }
        }
        return mask;
    }

    private static boolean containsField(int[] fieldNumbers, int count, int target) {
        for (int i = 0; i < count; i++) {
            if (fieldNumbers[i] == target) {
                return true;
            }
        }
        return false;
    }

    private static byte[] encodeTagAndVarint(int tag, int number) {
        int tagSize = CodedSize.uint32(tag);
        int numSize = CodedSize.int32(number);
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

    /**
     * Mutable amortized-O(1) accumulator for building {@link UnknownFields}. Not thread-safe.
     * Allocates a backing {@link ProtoWriter} lazily on the first mutation; if never mutated,
     * {@link #build()} returns the seeded {@link UnknownFields} directly without copying.
     */
    public static final class Builder {

        private UnknownFields base;
        private ProtoWriter buffer;
        private long presentMask;

        private Builder(UnknownFields existing) {
            this.base = existing;
            this.presentMask = existing.isEmpty() ? 0L : -1L;
        }

        private ProtoWriter ensureBuffer() {
            if (buffer == null) {
                buffer = ProtoWriter.growing();
                if (!base.isEmpty()) {
                    buffer.writeRawBytes(base.bytes, 0, base.bytes.length);
                }
                base = EMPTY;
            }
            return buffer;
        }

        /**
         * Captures the field at {@link ProtoReader#lastTag()} and appends it in amortized O(1) time.
         *
         * @param reader positioned immediately after {@link ProtoReader#readTag()}
         * @return {@code this}
         */
        public Builder merge(ProtoReader reader) {
            Objects.requireNonNull(reader, "reader");
            int tag = reader.lastTag();
            validateFieldTag(tag);
            reader.captureFieldTo(ensureBuffer());
            presentMask |= fieldBit(WireType.fieldNumber(tag));
            return this;
        }

        /**
         * Appends an already-consumed varint field (used when an enum number is unrecognized).
         * {@code number} is encoded as proto {@code int32} (10 bytes when negative).
         *
         * @param tag    wire tag (unpacked varint)
         * @param number enum number
         * @return {@code this}
         */
        public Builder mergeVarint(int tag, int number) {
            validateVarintTag(tag);
            ProtoWriter out = ensureBuffer();
            out.writeUInt32NoTag(tag);
            out.writeInt32NoTag(number);
            presentMask |= fieldBit(WireType.fieldNumber(tag));
            return this;
        }

        /**
         * Appends {@code tag} followed by the raw payload bytes {@code [payloadStart, payloadEnd)}
         * from {@code reader} (for example, to preserve an entire map entry whose enum value was unknown).
         *
         * @param reader       reader whose backing buffer holds the payload range
         * @param tag          wire tag
         * @param payloadStart inclusive start index obtained from {@link ProtoReader#position()}
         * @param payloadEnd   exclusive end index obtained from {@link ProtoReader#position()}
         * @return {@code this}
         */
        public Builder mergeRange(ProtoReader reader, int tag, int payloadStart, int payloadEnd) {
            Objects.requireNonNull(reader, "reader");
            validateFieldTag(tag);
            reader.writeRangeTo(tag, payloadStart, payloadEnd, ensureBuffer());
            presentMask |= fieldBit(WireType.fieldNumber(tag));
            return this;
        }

        /**
         * Appends all captured fields from {@code other}.
         *
         * @param other unknown fields to append; {@code null} or {@link #EMPTY} is a no-op
         * @return {@code this}
         */
        public Builder merge(UnknownFields other) {
            if (other == null || other.isEmpty()) {
                return this;
            }
            if (buffer == null && base.isEmpty()) {
                base = other;
                presentMask = -1L;
                return this;
            }
            ensureBuffer().writeRawBytes(other.bytes, 0, other.bytes.length);
            presentMask = -1L;
            return this;
        }

        /**
         * Removes all captured entries matching {@code fieldNumber}, preserving all other fields
         * in their original order. Fast no-op when empty or when {@code fieldNumber} is absent.
         *
         * @param fieldNumber protobuf field number to remove
         * @return {@code this}
         */
        public Builder removeField(int fieldNumber) {
            if (fieldNumber <= 0 || isEmpty() || (presentMask & fieldBit(fieldNumber)) == 0L) {
                return this;
            }
            byte[] source = buffer == null ? base.bytes : buffer.toByteArray();
            ProtoReader r = internalReader(source);
            int runStart = 0;
            ProtoWriter kept = null;
            long newMask = 0L;
            while (true) {
                int start = r.position();
                int tag = r.readTag();
                if (tag == 0) {
                    break;
                }
                r.skipField(tag);
                int end = r.position();
                int fn = WireType.fieldNumber(tag);
                if (fn == fieldNumber) {
                    if (kept == null) {
                        kept = ProtoWriter.growing();
                    }
                    if (start > runStart) {
                        kept.writeRawBytes(source, runStart, start - runStart);
                    }
                    runStart = end;
                } else {
                    newMask |= fieldBit(fn);
                }
            }
            presentMask = newMask;
            if (kept == null) {
                return this;
            }
            if (source.length > runStart) {
                kept.writeRawBytes(source, runStart, source.length - runStart);
            }
            if (kept.position() == 0) {
                base = EMPTY;
                buffer = null;
            } else {
                base = EMPTY;
                buffer = kept;
            }
            return this;
        }

        /**
         * Removes all captured entries whose field number appears in {@code fieldNumbers},
         * preserving all other fields in their original order. Fast no-op when empty or none match.
         *
         * @param fieldNumbers protobuf field numbers to remove
         * @return {@code this}
         */
        public Builder removeFields(int... fieldNumbers) {
            if (fieldNumbers == null || fieldNumbers.length == 0 || isEmpty()) {
                return this;
            }
            if (fieldNumbers.length == 1) {
                return removeField(fieldNumbers[0]);
            }
            long queryMask = buildFieldMask(fieldNumbers, fieldNumbers.length);
            if (queryMask == 0L || (presentMask & queryMask) == 0L) {
                return this;
            }
            byte[] source = buffer == null ? base.bytes : buffer.toByteArray();
            ProtoReader r = internalReader(source);
            int runStart = 0;
            ProtoWriter kept = null;
            long newMask = 0L;
            while (true) {
                int start = r.position();
                int tag = r.readTag();
                if (tag == 0) {
                    break;
                }
                r.skipField(tag);
                int end = r.position();
                int fn = WireType.fieldNumber(tag);
                long bit = fieldBit(fn);
                if ((queryMask & bit) != 0L && containsField(fieldNumbers, fieldNumbers.length, fn)) {
                    if (kept == null) {
                        kept = ProtoWriter.growing();
                    }
                    if (start > runStart) {
                        kept.writeRawBytes(source, runStart, start - runStart);
                    }
                    runStart = end;
                } else {
                    newMask |= bit;
                }
            }
            presentMask = newMask;
            if (kept == null) {
                return this;
            }
            if (source.length > runStart) {
                kept.writeRawBytes(source, runStart, source.length - runStart);
            }
            if (kept.position() == 0) {
                base = EMPTY;
                buffer = null;
            } else {
                base = EMPTY;
                buffer = kept;
            }
            return this;
        }

        public boolean isEmpty() {
            return buffer == null ? base.isEmpty() : buffer.position() == 0;
        }

        /**
         * Builds an immutable {@link UnknownFields} snapshot.
         * Returns {@link #EMPTY} if empty, or the original seeded instance if unmodified.
         */
        public UnknownFields build() {
            if (buffer == null) {
                return base;
            }
            if (buffer.position() == 0) {
                base = EMPTY;
                buffer = null;
                presentMask = 0L;
                return EMPTY;
            }
            UnknownFields built = new UnknownFields(buffer.toByteArray());
            base = built;
            buffer = null;
            return built;
        }
    }
}
