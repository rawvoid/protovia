package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/**
 * {@code google.protobuf.BoolValue}.
 *
 * @param value wrapped bool
 * @author Rawvoid
 */
public record BoolValue(boolean value) {
    public static final ProtoCodec<BoolValue> INSTANCE = WrapperCodec.bool();
}
