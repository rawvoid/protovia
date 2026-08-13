package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/**
 * {@code google.protobuf.Int64Value}.
 *
 * @param value wrapped int64
 * @author Rawvoid
 */
public record Int64Value(long value) {
    public static final ProtoCodec<Int64Value> INSTANCE = WrapperCodec.int64();
}
