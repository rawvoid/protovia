package io.github.rawvoid.protovia.codec;

import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.SizeCache;

/**
 * Stateless, thread-safe binary codec for one message type.
 * Implementations are generated at compile time; they may also be written by hand for tests.
 */
public interface ProtoCodec<T> {

    Class<T> type();

    int computeSize(T value);

    /**
     * Computes the serialized size and records nested / packed / map-entry lengths in {@code cache}.
     * Hand-written codecs may ignore the cache; generated codecs fill it so {@link #writeTo}
     * does not walk the tree again.
     */
    default int computeSize(T value, SizeCache cache) {
        return computeSize(value);
    }

    /**
     * Generated codecs that fill {@link SizeCache} return true so {@code writeMessage} consumes
     * reserved slots. Hand-written codecs stay false and never steal a sibling length.
     */
    default boolean cachesNestedSizes() {
        return false;
    }

    void writeTo(ProtoWriter writer, T value);

    T readFrom(ProtoReader reader);

    /**
     * Merges one wire message into {@code existing} (proto3: scalars overwrite, repeated append,
     * nested messages merge). The default last-wins implementation is for hand-written test codecs.
     */
    default T mergeFrom(ProtoReader reader, T existing) {
        return readFrom(reader);
    }
}
