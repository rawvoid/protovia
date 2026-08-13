package io.github.rawvoid.protovia.codec;

import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;

/**
 * Stateless, thread-safe binary codec for one message type.
 * Implementations are generated at compile time; they may also be written by hand for tests.
 */
public interface ProtoCodec<T> {

    Class<T> type();

    int computeSize(T value);

    void writeTo(ProtoWriter writer, T value);

    T readFrom(ProtoReader reader);
}
