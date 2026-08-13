package io.github.rawvoid.protovia;

import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

import java.util.Arrays;

/**
 * Opaque, ordered capture of unrecognized protobuf fields (tag + payload).
 * Immutable. {@link #EMPTY} is the unset slot.
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
     * Captures the field at the reader's current tag and appends it.
     */
    public static UnknownFields merge(UnknownFields existing, ProtoReader reader) {
        UnknownFields base = existing == null ? EMPTY : existing;
        return base.append(reader.captureField());
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
    public boolean equals(Object o) {
        return o instanceof UnknownFields other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
