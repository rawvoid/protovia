package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/** {@code google.protobuf.BoolValue}. */
public record BoolValue(boolean value) {
    public static final ProtoCodec<BoolValue> INSTANCE = WrapperCodec.bool();
}
