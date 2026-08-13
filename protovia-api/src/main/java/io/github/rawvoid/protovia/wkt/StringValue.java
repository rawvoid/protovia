package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/** {@code google.protobuf.StringValue}. */
public record StringValue(String value) {
    public static final ProtoCodec<StringValue> INSTANCE = WrapperCodec.string();

    public StringValue {
        if (value == null) {
            value = "";
        }
    }
}
