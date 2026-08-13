package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/**
 * {@code google.protobuf.Int32Value}. Not a substitute for {@code Integer} int32 fields.
 *
 * @param value wrapped int32
 * @author Rawvoid
 */
public record Int32Value(int value) {
    public static final ProtoCodec<Int32Value> INSTANCE = WrapperCodec.int32();
}
