package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/** {@code google.protobuf.DoubleValue}. */
public record DoubleValue(double value) {
    public static final ProtoCodec<DoubleValue> INSTANCE = WrapperCodec.float64();
}
