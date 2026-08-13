package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

import java.util.Arrays;

/** {@code google.protobuf.BytesValue}. */
public record BytesValue(byte[] value) {
    public static final ProtoCodec<BytesValue> INSTANCE = WrapperCodec.bytes();

    public BytesValue {
        if (value == null) {
            value = new byte[0];
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BytesValue other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}
