package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/**
 * {@code google.protobuf.DoubleValue}.
 *
 * @param value wrapped double
 * @author Rawvoid
 */
public record DoubleValue(double value) {
    public static final ProtoCodec<DoubleValue> INSTANCE = WrapperCodec.float64();
}
