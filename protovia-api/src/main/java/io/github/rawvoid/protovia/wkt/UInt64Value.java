package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/** {@code google.protobuf.UInt64Value}. Java {@code long} on the wire as uint64. */
public record UInt64Value(long value) {
    public static final ProtoCodec<UInt64Value> INSTANCE = WrapperCodec.uint64();
}
