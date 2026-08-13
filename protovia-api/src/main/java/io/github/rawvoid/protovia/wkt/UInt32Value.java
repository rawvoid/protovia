package io.github.rawvoid.protovia.wkt;

import io.github.rawvoid.protovia.codec.ProtoCodec;

/**
 * {@code google.protobuf.UInt32Value}. Java {@code int} on the wire as uint32.
 *
 * @param value wrapped uint32 bits
 * @author Rawvoid
 */
public record UInt32Value(int value) {
    public static final ProtoCodec<UInt32Value> INSTANCE = WrapperCodec.uint32();
}
