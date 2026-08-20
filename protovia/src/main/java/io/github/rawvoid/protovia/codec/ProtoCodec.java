/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.codec;

import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.SizeCache;

/**
 * Stateless, thread-safe binary codec for one message type.
 * Implementations are generated at compile time; they may also be written by hand for tests.
 *
 * @param <T> entity type this codec reads and writes
 * @author Rawvoid
 */
public interface ProtoCodec<T> {

    /**
     * @return the Java type this codec handles
     */
    Class<T> type();

    /**
     * Protobuf full name ({@code package.Message}) used by Any {@code type_url}.
     * Generated codecs use {@code @ProtoMessage(packageName, name)}; well-known
     * types return {@code google.protobuf.*}. Default is the Java simple name.
     */
    default String protoFullName() {
        return type().getSimpleName();
    }

    /**
     * @param value message to measure; must not be {@code null}
     * @return encoded size in bytes, excluding any outer length prefix
     */
    int computeSize(T value);

    /**
     * Computes the serialized size and records nested / packed / map-entry lengths in {@code cache}.
     * Hand-written codecs may ignore the cache; generated codecs fill it so {@link #writeTo}
     * does not walk the tree again.
     *
     * @param value message to measure
     * @param cache pre-order size table filled by generated codecs
     * @return encoded size in bytes
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

    /**
     * Writes {@code value} at the current writer position (no outer length prefix).
     *
     * @param writer destination
     * @param value  message to encode
     */
    void writeTo(ProtoWriter writer, T value);

    /**
     * Reads one message from {@code reader} up to the current limit.
     *
     * @param reader source positioned at the first field tag
     * @return a new instance
     */
    T readFrom(ProtoReader reader);

    /**
     * Merges one wire message into {@code existing} (proto3: scalars overwrite, repeated append,
     * nested messages merge). The default last-wins implementation is for hand-written test codecs.
     *
     * @param reader   source positioned at the first field tag
     * @param existing value to merge into; may be {@code null}
     * @return the merged instance (may be {@code existing} or a new object)
     */
    default T mergeFrom(ProtoReader reader, T existing) {
        return readFrom(reader);
    }
}
