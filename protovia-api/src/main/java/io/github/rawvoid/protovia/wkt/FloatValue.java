package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/** {@code google.protobuf.FloatValue}. */
public record FloatValue(float value) {
    public static final ProtoCodec<FloatValue> INSTANCE = WrapperCodec.float32();
}
